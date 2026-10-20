# Plan del backend — fases y estado

Hoja de ruta para llevar el backend al target descrito en
[`documentacion-tecnica.md`](documentacion-tecnica.md). Cada fase marca su estado en el título:

- **✅** terminada
- **–** en progreso / falta pulir
- **(sin marca)** aún no empezada

> **Fuente de verdad viva** del contrato: Swagger (`/swagger-ui.html`, `/v3/api-docs`).
> Catálogo canónico de códigos `code`: [`../integracion-frontend.md`](../integracion-frontend.md).

## Estado global

| # | Fase | Estado |
|---|------|--------|
| 0 | Núcleo (auth, transacciones, metas, cuota, categorías, analíticas descriptivas) | ✅ |
| 1 | Filtro por rango de fechas | ✅ |
| 2 | Comparación entre períodos (deltas) | ✅ |
| 3 | Presupuestos por categoría | ✅ |
| 4 | Proyección de fin de mes | ✅ |
| 5 | Señales de salud ampliadas | – |
| 6 | Endpoint de recomendaciones | |

**Regla de dependencia:** sin la Fase 1 (filtro de fechas) no se calcula nada de lo demás.
Las Fases 5 y 6 dependen de 2, 3 y 4.

---

## ✅ Fase 0 — Núcleo

Base ya en producción. Detalle técnico en [`documentacion-tecnica.md`](documentacion-tecnica.md).

- [x] Auth JWT + refresh rotativo + OTP por correo
- [x] Transacciones CRUD paginado
- [x] Categorías (base + propias)
- [x] Metas mensuales persistidas + días laborables dinámicos
- [x] Motor de cuota diaria por utilidad neta
- [x] Analíticas descriptivas: resumen semanal, tendencia mensual, resumen por categoría, salud financiera (3 señales)
- [x] Envelope de respuesta consistente + `GlobalExceptionHandler`
- [x] Esquema propiedad de Flyway (`ddl-auto=validate`)

## ✅ Fase 1 — Filtro por rango de fechas

Base de todo. Desbloquea drill-down y períodos custom. Spec: sección 1 del doc técnico.

- [x] `GET /transacciones` acepta `desde` / `hasta` (`YYYY-MM-DD`, inclusivos)
- [x] `GET /resumen-categorias` acepta `desde` / `hasta` (sin ellos, mes en curso)
- [x] Validación `desde <= hasta` → `400 RANGO_FECHAS_INVALIDO`

## ✅ Fase 2 — Comparación entre períodos (deltas)

Convierte totales en insight ("subió +32%"). Spec: sección 2 del doc técnico.

- [x] `GET /analiticas/comparacion-categorias` (`desde`, `hasta`, `compararCon`)
- [x] `deltaPct = null` cuando el período anterior fue `0`
- [x] `code: CATEGORY_COMPARISON_OK`

## ✅ Fase 3 — Presupuestos por categoría

Habilita recomendaciones de gasto. Spec: sección 3 del doc técnico.

- [x] `POST /presupuestos` (upsert por `(usuario, categoría)`)
- [x] `GET /presupuestos` con estado del mes (`gastadoMes`, `restante`, `consumoPct`, `excedido`)
- [x] `DELETE /presupuestos/{id}`
- [x] Códigos `BUDGET_SET` / `BUDGETS_OK` / `BUDGET_DELETED` / `PRESUPUESTO_NO_ENCONTRADO`

## ✅ Fase 4 — Proyección de fin de mes

Anticipa, no sólo describe. Spec: sección 4 del doc técnico.

- [x] `GET /proyeccion-mensual` (run-rate por días hábiles)
- [x] `brechaProyectada`, `enCamino`, método de proyección documentado
- [x] `code: MONTHLY_PROJECTION_OK`

## – Fase 5 — Señales de salud ampliadas

Amplía el catálogo de `salud-financiera` y agrega `severidad`, retrocompatible. Spec: sección 5.
**En progreso.** Lo implementado ya está commiteado en `AnaliticaService` / `AlertaDTO`.

- [x] Campo `severidad` (`ALTA` | `MEDIA` | `BAJA`) + `categoriaId` opcional
- [x] Nuevos `code` implementados: `EGRESOS_SUPERAN_INGRESOS`, `TASA_AHORRO_BAJA`,
      `TASA_AHORRO_SANA`, `PRESUPUESTO_EXCEDIDO`, `PROYECCION_BAJO_META`
- [ ] Nuevos `code` pendientes: `PICO_CATEGORIA`, `INGRESO_VOLATIL`, `RACHA_POSITIVA`
- [ ] Cada `code` con su regla exacta documentada: los 5 implementados coinciden con la
      sección 5 del doc técnico; los 3 pendientes todavía no tienen umbral exacto
      ("alta dispersión", "N días")
- [ ] Tests: solo `EGRESOS_SUPERAN_INGRESOS` tiene test de integración; faltan
      `TASA_AHORRO_BAJA`, `TASA_AHORRO_SANA`, `PRESUPUESTO_EXCEDIDO`, `PROYECCION_BAJO_META`
      y `severidad`
- [x] Retrocompatibilidad: los 3 códigos actuales siguen

## Fase 6 — Endpoint de recomendaciones

Capa accionable final. Reúne 2, 3 y 4 en consejos priorizados. Spec: sección 6. **Sin empezar.**

- [ ] `GET /recomendaciones` (lista priorizada, deterministas primero)
- [ ] Catálogo cerrado de `code`: `REDUCIR_CATEGORIA`, `SUBIR_RITMO_INGRESOS`,
      `CREAR_PRESUPUESTO`, `AUMENTAR_AHORRO`, `REGISTRAR_MOVIMIENTOS`, `META_ALCANZABLE`
- [ ] `code: RECOMMENDATIONS_OK`

---

## Checklist "backend listo para fusión"

| # | Criterio | Estado |
|---|----------|--------|
| 1 | Endpoints expuestos en OpenAPI/Swagger | ✅ |
| 2 | Envelope de éxito y error consistente | ✅ |
| 3 | Códigos `code` estables y documentados | ✅ |
| 4 | 401 real en sesión vencida / token inválido (no 500) | ✅ (probado) |
| 5 | Historial, meta persistida y categorías expuestos y probados | ✅ (69 tests) |
| 6 | Sesión con refresh token definido y probado | ✅ |
| 7 | Secretos fuera del repo | ✅ (`application*.properties` gitignored) |
| 8 | Credenciales rotadas | ⚠️ acción operativa del responsable |
| 9 | Perfil `prod` desplegado en Azure y accesible | ⏳ pendiente (infra en paralelo) |

## Pendientes reales

- **Rotación de credenciales (#8):** sacar los secretos del repo (ya hecho) no es lo mismo que
  rotarlos. Gmail app password, `jwt.secret` y password de BD siguen siendo los históricamente
  filtrados. Acción operativa fuera del código.
- **Despliegue Azure (#9):** se verificó la **conexión** a PostgreSQL Azure con el perfil `prod`
  en local (Hikari conecta, Flyway valida) — eso NO es tener el backend **desplegado** en Azure
  App Service. Falta confirmar/hacer el deploy.
- **CORS:** sin tocar a propósito. Se activa recién cuando entre un cliente basado en navegador
  (web/smartwatch/flota). Decisión tomada, no pendiente real.
- **Metadatos `pom.xml`:** cosmético. `<license/>`, `<developers/>`, `<scm/>` vacíos.
