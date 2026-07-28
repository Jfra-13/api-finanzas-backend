# Documentación técnica — target del backend

Especificación técnica de **cómo debe quedar** el backend de Finanzas Independientes cuando
esté finalizado. Cubre el núcleo ya construido (baseline) y el target de Analíticas y
Recomendaciones que aún falta. El seguimiento por fases (qué está hecho y qué falta) vive en
[`plan.md`](plan.md).

> **Convenciones transversales** (mismas en todo el backend): envelope de respuesta, decisión
> por campo `code` (nunca por `message`), prefijo `/api/v1`, JWT Bearer, montos decimales con 2
> posiciones, fechas ISO (`2026-06-12T10:30:00`), zona horaria del servidor. El contrato
> detallado endpoint por endpoint está en [`../integracion-frontend.md`](../integracion-frontend.md).
>
> **Fuente de verdad viva:** Swagger. Este documento es la especificación; al implementarse,
> Swagger manda.

---

## Baseline: núcleo ya construido

Arquitectura y features en producción sobre las que se apoya todo lo demás.

- **Monolito modular por feature** (`usuario/`, `transaccion/`, `meta/`, `security/`, `shared/`).
  Flujo estricto Controller → Service → Repository. DTOs no escapan de su feature; entidades no
  llegan al controller. Detalle en el [`README.md`](../../README.md) raíz.
- **Auth JWT + refresh rotativo.** Access token 15 min, refresh 30 días (hash SHA-256 en BD, rota
  en cada uso). OTP por correo para recupero de password.
- **Motor de cuota diaria** recalculado por **utilidad neta** (ingresos − egresos) del mes en
  curso, sobre días laborables dinámicos (schedule semanal configurable).
- **Metas mensuales persistidas** por usuario + período, con días laborables.
- **Transacciones (CRUD paginado)** y **categorías** (base compartidas + propias del usuario).
- **Analíticas descriptivas** ya expuestas: resumen semanal, tendencia mensual, resumen por
  categoría, salud financiera (3 señales deterministas).
- **Esquema propiedad de Flyway** (`ddl-auto=validate`), enums como `VARCHAR` para correr igual
  en H2 y PostgreSQL.

## Target: Analíticas y Recomendaciones (lo que falta)

El objetivo de esta capa es que Analíticas deje de ser sólo descriptiva (fotos del pasado) y pase
a entregar información **comparable, proyectada** y **recomendaciones financieras accionables**.

## Contexto de la app (por qué importa cada endpoint)

- Los gráficos usan la librería **MPAndroidChart** (barras, línea, torta). El teléfono sólo
  pinta datos ya calculados: el **procesamiento matemático pesado vive en el servidor** (batería).
- Sección actual y su fuente:
  - Barras "Resumen semanal" → `GET /resumen-semanal`
  - Línea "Tendencia mensual" → `GET /tendencia-mensual`
  - Torta "Egresos por categoría" → `GET /resumen-categorias`
  - Panel "Salud financiera" → `GET /salud-financiera`

## Principios (obligatorios para banca)

1. **Determinismo y explicabilidad primero.** Toda señal o recomendación debe derivar de una
   **regla explícita y auditable**. El usuario debe poder preguntar "¿por qué me decís esto?" y
   existir una respuesta exacta. Modelos de caja negra que aconsejan sobre dinero son un riesgo
   de confianza — dejarlos para una fase posterior, nunca como base.
2. **El cliente ramifica por `code`.** Todo texto (`titulo`, `mensaje`, `detalle`) es para humanos
   y puede cambiar sin previo aviso.
3. **Aislamiento por usuario.** La identidad sale del JWT; ningún endpoint recibe `usuarioId`.

---

## Orden de implementación (por dependencia e impacto)

| # | Capacidad | Depende de | Impacto |
|---|-----------|-----------|---------|
| 1 | Filtro por rango de fechas | — | **Base de todo.** Desbloquea drill-down y períodos custom |
| 2 | Comparación entre períodos (deltas) | #1 | Convierte totales en insight ("subió +32%") |
| 3 | Presupuestos por categoría | #1 | Habilita recomendaciones de gasto |
| 4 | Proyección de fin de mes | #1 | Anticipa, no sólo describe |
| 5 | Señales de salud ampliadas | #2, #3, #4 | Más y mejores alertas |
| 6 | Endpoint de recomendaciones | #2, #3, #4 | Capa accionable final |

**Sin #1 no se puede calcular nada de lo demás.** Empezar por ahí.

---

## 1. Filtro por rango de fechas (transversal)

Hoy `GET /transacciones` sólo filtra por `tipo` y `categoriaId`, y las analíticas usan períodos
fijos (semana/mes actual). **Sin filtro de fechas no hay drill-down de barras/línea ni períodos
personalizados.**

### 1.1 `GET /api/v1/finanzas/transacciones` — agregar `desde` / `hasta`

Añadir dos query params opcionales a la firma existente:

| Query param | Tipo | Descripción |
|---|---|---|
| `desde` | fecha `YYYY-MM-DD` (opcional) | Límite inferior **inclusivo** (00:00:00 de ese día) |
| `hasta` | fecha `YYYY-MM-DD` (opcional) | Límite superior **inclusivo** (23:59:59 de ese día) |

- Se combinan con los filtros actuales (`tipo`, `categoriaId`, `page`, `size`, `sort`).
- Si sólo se envía `desde`, filtra desde esa fecha en adelante; sólo `hasta`, hasta esa fecha.
- Validación: `desde <= hasta`. Si no, `400 RANGO_FECHAS_INVALIDO`.
- Respuesta: **sin cambios** (misma página de Spring que hoy).

> Esto habilita, por ejemplo, el drill-down de un día de la barra semanal
> (`?desde=2026-06-10&hasta=2026-06-10`) o de un mes de la línea de tendencia.

### 1.2 `GET /api/v1/finanzas/resumen-categorias` — agregar `desde` / `hasta`

Mismos dos params opcionales. **Sin ellos, mantiene el comportamiento actual** (mes en curso),
para no romper la app existente.

Respuesta: igual que hoy (`code: "CATEGORY_SUMMARY_OK"`, mapa `nombre → total`), pero acotada al
rango pedido.

---

## 2. Comparación entre períodos (deltas)

Un total suelto no informa. Lo relevante es la **variación**: "Gasolina subió **+32%** respecto al
mes anterior". Alimenta una vista comparativa y varias recomendaciones.

### `GET /api/v1/finanzas/analiticas/comparacion-categorias`

Egresos por categoría del período elegido **contra** un período de referencia.

| Query param | Tipo | Descripción |
|---|---|---|
| `desde` | fecha (opcional) | Inicio del período actual. Default: primer día del mes en curso |
| `hasta` | fecha (opcional) | Fin del período actual. Default: hoy |
| `compararCon` | string (opcional) | `PERIODO_ANTERIOR` (default) o `MISMO_PERIODO_ANIO_ANTERIOR` |

El backend calcula el período de referencia con la misma duración inmediatamente anterior
(o el año anterior). `deltaPct` = `null` cuando el período anterior fue `0` (evitar división por cero;
el cliente muestra "nuevo").

Respuesta (`code: "CATEGORY_COMPARISON_OK"`):
```json
{
  "data": {
    "periodoActual":   { "desde": "2026-06-01", "hasta": "2026-06-30" },
    "periodoAnterior": { "desde": "2026-05-01", "hasta": "2026-05-31" },
    "categorias": [
      { "categoria": "Gasolina", "actual": 320.00, "anterior": 242.00, "deltaAbs": 78.00,  "deltaPct": 32.2 },
      { "categoria": "Peaje",    "actual": 75.50,  "anterior": 90.00,  "deltaAbs": -14.50, "deltaPct": -16.1 },
      { "categoria": "Sin categoría", "actual": 40.00, "anterior": 0.00, "deltaAbs": 40.00, "deltaPct": null }
    ],
    "totalActual": 435.50,
    "totalAnterior": 332.00,
    "totalDeltaPct": 31.2
  }
}
```

Errores: `400 RANGO_FECHAS_INVALIDO`.

---

## 3. Presupuestos por categoría

Permite al usuario fijar un tope mensual por categoría; el backend devuelve **gastado vs tope**.
Es la fuente de la recomendación más natural: "te pasaste 15% en Peajes este mes".

### 3.1 `POST /api/v1/finanzas/presupuestos`

Crea o actualiza (upsert) el presupuesto mensual de una categoría.

Request:
```json
{ "categoriaId": 1, "montoMensual": 300.00 }
```

Validaciones: `categoriaId` obligatorio y visible para el usuario; `montoMensual` mínimo `0.01`.
Un presupuesto por `(usuario, categoría)` — reenviar reemplaza el monto.

Respuesta (`code: "BUDGET_SET"`): el presupuesto con su `id`.

Errores: `404 CATEGORIA_NO_ENCONTRADA`, `400 VALIDATION_ERROR`.

### 3.2 `GET /api/v1/finanzas/presupuestos`

Lista los presupuestos del usuario **con su estado del mes en curso**.

Respuesta (`code: "BUDGETS_OK"`):
```json
{
  "data": [
    {
      "id": 5,
      "categoriaId": 1,
      "categoriaNombre": "Gasolina",
      "montoMensual": 300.00,
      "gastadoMes": 320.00,
      "restante": -20.00,
      "consumoPct": 106.7,
      "excedido": true
    }
  ]
}
```

`restante` negativo y `excedido: true` cuando se pasó del tope. `consumoPct` sin tope superior
(puede superar 100).

### 3.3 `DELETE /api/v1/finanzas/presupuestos/{id}`

Elimina un presupuesto propio. Respuesta: `code: "BUDGET_DELETED"`.
Errores: `404 PRESUPUESTO_NO_ENCONTRADO`, `403 ACCESO_DENEGADO`.

---

## 4. Proyección de fin de mes

Con los días transcurridos y el ritmo (run-rate), proyectar cómo cierra el mes. Cuadra con la
filosofía de "mate pesado en el servidor". Alimenta un mensaje anticipatorio:
"A este ritmo cerrás el mes en S/ 2.400, S/ 200 bajo tu meta".

### `GET /api/v1/finanzas/proyeccion-mensual`

Sin parámetros: usa la meta persistida del usuario y el mes en curso.

Respuesta (`code: "MONTHLY_PROJECTION_OK"`):
```json
{
  "data": {
    "periodo": "2026-06",
    "diasTranscurridos": 20,
    "diasDelMes": 30,
    "diasHabilesRestantes": 7,
    "ingresoActual": 1567.00,
    "egresoActual": 620.00,
    "utilidadActual": 947.00,
    "ingresoProyectado": 2350.00,
    "egresoProyectado": 930.00,
    "utilidadProyectada": 1420.00,
    "metaMensual": 3000.00,
    "brechaProyectada": -1580.00,
    "enCamino": false
  }
}
```

- `brechaProyectada` = `utilidadProyectada - metaMensual` (negativo = por debajo).
- `enCamino`: `true` si la proyección alcanza o supera la meta.
- Documentar el método de proyección (ej. lineal por días hábiles) para que sea explicable.

Errores: `404 META_NO_ENCONTRADA` si el usuario no fijó meta este mes.

---

## 5. Señales de salud ampliadas

`GET /salud-financiera` ya existe con el patrón `{ tipo, code, mensaje }` y hoy sólo emite 3 códigos
(`GASTO_DIARIO_ALTO`, `META_CERCA`, `META_EN_RIESGO`). Se pide **ampliar el catálogo** y agregar un
campo `severidad`, manteniendo compatibilidad (los 3 actuales siguen).

Shape ampliado (retrocompatible — se agregan campos, no se quitan):
```json
{
  "data": [
    {
      "tipo": "ALERTA",
      "code": "PRESUPUESTO_EXCEDIDO",
      "severidad": "ALTA",
      "mensaje": "Superaste el presupuesto de Gasolina en 7%.",
      "categoriaId": 1
    }
  ]
}
```

`severidad`: `ALTA` | `MEDIA` | `BAJA` (el cliente ordena y colorea por esto).
`categoriaId`: opcional, presente cuando la señal apunta a una categoría concreta.

Nuevos `code` sugeridos (deterministas):

| `code` | `tipo` | Regla (ejemplo) |
|---|---|---|
| `TASA_AHORRO_BAJA` | ALERTA | `(ingreso - egreso) / ingreso < 10%` en el mes |
| `EGRESOS_SUPERAN_INGRESOS` | ALERTA | Egreso mensual > ingreso mensual |
| `PICO_CATEGORIA` | ALERTA | Una categoría creció > 50% vs mes anterior |
| `PRESUPUESTO_EXCEDIDO` | ALERTA | `gastadoMes > montoMensual` de un presupuesto |
| `PROYECCION_BAJO_META` | ALERTA | `proyeccion.enCamino == false` |
| `INGRESO_VOLATIL` | ALERTA | Alta dispersión de ingresos diarios en la semana |
| `RACHA_POSITIVA` | FELICITACION | N días seguidos cumpliendo la cuota diaria |
| `TASA_AHORRO_SANA` | FELICITACION | Tasa de ahorro > 20% |

Cada `code` nuevo debe quedar documentado con su **regla exacta** en el catálogo canónico.

---

## 6. Endpoint de recomendaciones

Capa accionable final. Reúne lo anterior en consejos priorizados. Reglas deterministas primero.

### `GET /api/v1/finanzas/recomendaciones`

Sin parámetros: evalúa el estado actual del usuario.

Respuesta (`code: "RECOMMENDATIONS_OK"` — lista, puede venir vacía):
```json
{
  "data": [
    {
      "code": "REDUCIR_CATEGORIA",
      "prioridad": "ALTA",
      "titulo": "Gasolina está consumiendo más de lo previsto",
      "detalle": "Este mes gastaste S/ 320 en Gasolina, 7% sobre tu presupuesto de S/ 300.",
      "impactoEstimado": 20.00,
      "accionSugerida": "AJUSTAR_PRESUPUESTO",
      "categoriaId": 1
    },
    {
      "code": "SUBIR_RITMO_INGRESOS",
      "prioridad": "MEDIA",
      "titulo": "Vas camino a quedar bajo tu meta",
      "detalle": "A este ritmo cerrás el mes en S/ 1.420 de utilidad, S/ 1.580 bajo tu meta.",
      "impactoEstimado": 1580.00,
      "accionSugerida": "VER_PROYECCION",
      "categoriaId": null
    }
  ]
}
```

Campos:

| Campo | Tipo | Nota |
|---|---|---|
| `code` | string | El cliente ramifica por acá. Catálogo cerrado |
| `prioridad` | string | `ALTA` \| `MEDIA` \| `BAJA` — orden de la lista |
| `titulo` | string | Texto humano, breve |
| `detalle` | string | Texto humano, explicación con números |
| `impactoEstimado` | decimal (opcional) | En S/, para ordenar por impacto |
| `accionSugerida` | string (opcional) | Deep-link de acción del cliente (ej. `AJUSTAR_PRESUPUESTO`) |
| `categoriaId` | entero (opcional) | Si la recomendación apunta a una categoría |

`code` de recomendación sugeridos: `REDUCIR_CATEGORIA`, `SUBIR_RITMO_INGRESOS`,
`CREAR_PRESUPUESTO` (categoría con gasto alto y sin presupuesto), `AUMENTAR_AHORRO`,
`REGISTRAR_MOVIMIENTOS` (pocos registros → datos pobres), `META_ALCANZABLE` (felicitación/impulso).

---

## Catálogo de nuevos códigos de error

Agregar al catálogo canónico ([`../integracion-frontend.md`](../integracion-frontend.md)):

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `RANGO_FECHAS_INVALIDO` | `desde > hasta` o fecha malformada |
| 404 | `PRESUPUESTO_NO_ENCONTRADO` | Presupuesto inexistente |

Los `code` de éxito nuevos (`CATEGORY_COMPARISON_OK`, `BUDGET_SET`, `BUDGETS_OK`, `BUDGET_DELETED`,
`MONTHLY_PROJECTION_OK`, `RECOMMENDATIONS_OK`) también deben quedar registrados.

---

## Notas de consistencia (aplican a todo lo anterior)

- **Montos**: decimal con 2 posiciones, misma moneda que el resto (S/).
- **Porcentajes**: número (ej. `32.2` = 32.2%), no string, `null` cuando no aplica.
- **Fechas de rango**: `YYYY-MM-DD`, inclusivas ambos extremos, zona del servidor.
- **Retrocompatibilidad**: `resumen-categorias` y `salud-financiera` deben seguir funcionando sin
  los campos/params nuevos (la app en producción no debe romperse).
- **Vacío ≠ error**: listas vacías se devuelven con `200` y `data: []`, no con error.
- **Explicabilidad**: cada señal y recomendación documenta su regla exacta en el catálogo.
