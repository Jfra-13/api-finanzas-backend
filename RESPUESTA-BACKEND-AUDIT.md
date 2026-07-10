# Respuesta del backend a la auditoría de endpoints

Respuesta punto por punto a `ENDPOINTS-AUDIT-DESDE_FRONT.md`. Cada ítem indica el estado real
en la rama `feature/analytics-filtro-fechas` del backend y la decisión tomada.

> **Contexto clave**: la auditoría se hizo contra el backend desplegado/main. Esta rama
> (pendiente de merge) ya contenía 5 de los endpoints listados como faltantes, y a partir de
> esta respuesta se implementaron los P0/P1 restantes. Swagger (`/v3/api-docs`) queda como
> fuente de verdad; todo lo de abajo ya está documentado ahí.

---

## 1. Sección 0 (convenciones) — confirmadas

Envelope, ramificación por `code`, identidad por JWT, rotación de refresh en cada uso y
formatos: todo confirmado contra el código del backend. Sin observaciones.

Una precisión sobre el caché offline (Room): los tres GET cacheados (transacciones página 1,
categorías, meta activa) mantienen shape estable. Cualquier cambio futuro será aditivo
(campos nuevos nullable), nunca destructivo.

## 2. Sección A (19 endpoints) — confirmados, con 3 correcciones de contrato

| Punto | Respuesta |
|---|---|
| A.1–A.12, A.14–A.21 | Contratos confirmados tal como los describe la auditoría. |
| **A.13 `cuota-diaria`** | **Corregido**: el backend NO emitía `META_NO_ENCONTRADA` (devolvía un número engañoso sin meta activa: con utilidad positiva y sin meta, el valor salía negativo y la app hubiera mostrado "Meta superada" sin meta fijada). Ahora, sin meta activa y sin params ad-hoc, responde `404 META_NO_ENCONTRADA` como la app ya espera. Los params `meta`/`dias` siguen funcionando. |
| **A.15 `progreso-metas`** | **Corregido igual que A.13**: sin meta activa responde `404 META_NO_ENCONTRADA` (la app ya lo maneja como estado vacío). |
| **A.22 `salud-financiera`** | El shape real ya NO es `{tipo, code, mensaje}`: incluye **`severidad`** (`ALTA`/`MEDIA`/`BAJA`) y `categoriaId` (nullable, solo en `PRESUPUESTO_EXCEDIDO`). El catálogo ya tiene 8 reglas: los 3 codes que conocen más `EGRESOS_SUPERAN_INGRESOS`, `TASA_AHORRO_BAJA`, `TASA_AHORRO_SANA`, `PRESUPUESTO_EXCEDIDO`, `PROYECCION_BAJO_META`. La "ampliación P2" que proponen ya está hecha; falta solo la decisión de UI de dónde mostrarla. Codes desconocidos caen en `UNKNOWN` sin romper, así que pueden adoptarla incrementalmente. |

Contratos implícitos que pidieron formalizar — **hecho, ya están en Swagger**:

- `resumen-semanal`: garantía documentada de **exactamente 7 items, lunes→domingo, posición 0 = lunes**, con ceros en días sin movimientos.
- `cuota-diaria`: **el signo es contrato** — `> 0` = falta ganar hoy; `<= 0` = meta superada (excedente en negativo).
- `fecha` en transacciones: se acepta **solo ISO-8601 datetime** (`yyyy-MM-dd'T'HH:mm:ss`). Fecha sin hora se rechaza. Omitida = fecha/hora actual del servidor. Listo para el date picker.

## 3. Sección C — estado real por prioridad

### P0 — todo disponible

| Ítem | Estado | Detalle |
|---|---|---|
| P0.1 `desde`/`hasta` en `GET /transacciones` | **Ya existía en esta rama** | `YYYY-MM-DD` inclusivos, combinables con `tipo`/`categoriaId`. `desde > hasta` o formato inválido → `400 RANGO_FECHAS_INVALIDO`. |
| P0.2 `GET /resumen-diario?mes=YYYY-MM` | **Implementado** | `DAILY_SUMMARY_OK`. Solo días CON actividad, ascendente: `[{fecha, ingresos, egresos}]`. Sin `mes` = mes en curso. `mes` inválido → `400 PARAMETRO_INVALIDO`. |
| P0.3 `GET /tendencia?granularidad=SEMANA\|MES&ventana=N` | **Implementado** | `TREND_OK`. Shape `{periodos, ingresos, egresos}` (arrays paralelos, más viejo primero). `MES` rotula `yyyy-MM`; `SEMANA` rotula con el **lunes que inicia la semana** (`yyyy-MM-dd`). `ventana` default 6, mínimo 1. `MES&ventana=1` habilita "1M". Granularidad inválida → `400 PARAMETRO_INVALIDO`. `/tendencia-mensual` queda intacto para la app publicada. |
| P0.4 `GET /ingresos-por-dia-semana?ventana=N` | **Implementado** | `WEEKDAY_INCOME_OK`. Siempre 7 items lunes→domingo; `dia` en mayúsculas **sin tildes** (`LUNES`, `MIERCOLES`, `SABADO`). `ventana` = semanas hacia atrás incluyendo la actual, default 4. |
| P0.5 `PUT`/`DELETE /categorias/{id}` | **Implementado** | `CATEGORY_UPDATED` / `CATEGORY_DELETED`. PUT edita solo `nombre` (el `tipo` es inmutable: cambiarlo corrompería analíticas históricas). DELETE conserva las transacciones (quedan "Sin categoría") y elimina los presupuestos de esa categoría. Categorías base del sistema → `403 ACCESO_DENEGADO`; categoría de otro usuario → `404 CATEGORIA_NO_ENCONTRADA` (no filtra existencia). |
| P0.6 `GET /usuarios/me` | **Implementado** | `PROFILE_OK`. `{id, nombre, email, telefono, fotoUrl, tipoNegocio, plan}`. **Divergencia deliberada**: el campo es `tipoNegocio`, no `negocio` — mismo nombre que en login y `PUT /me/negocio`, un solo nombre por concepto. `fotoUrl` y `plan` llegan `null` (sin backend aún; el contrato ya los incluye para que binden una sola vez). |

### P1 — implementado lo de sesión y cuenta editable

| Ítem | Estado | Detalle |
|---|---|---|
| P1.1 `PUT /usuarios/me` | **Implementado** | `PROFILE_UPDATED`. Update parcial: solo cambian los campos presentes (`nombre`, `telefono`). Email no editable. Devuelve el perfil actualizado. |
| P1.2 `POST /usuarios/logout` | **Implementado** | `LOGGED_OUT`. Público (poseer el refresh token ES la credencial, y funciona con access token vencido). **Idempotente**: token desconocido o ya revocado también responde 200. El access token sigue válido hasta expirar (15 min); descártenlo localmente como hoy. Coincidimos: era un hueco de seguridad — intégrenlo en cuanto puedan. |
| P1.3 `DELETE /usuarios/me` | **Pendiente de negocio** | De acuerdo con implementarlo con password de confirmación, pero soft-delete vs. borrado físico y período de gracia son decisión de producto. No se implementa hasta definirlo. |
| P1.4 Filtro "sin categoría" server-side | **Pendiente** | Aceptado. Propuesta: `categoriaId=0` como marcador de "sin categoría" (evita ambigüedad con el param ausente). Confirmen y lo implementamos. |
| P1.5 `GET /metas/historial?meses=N` | **Pendiente** | Aceptado tal como lo proponen. Requiere decidir si la utilidad de meses pasados se calcula on-the-fly (más simple, elegimos esto salvo objeción) o se materializa al cerrar el mes. |
| P1.6 `desde`/`hasta` en `resumen-categorias` | **Ya existía en esta rama** | Sin params mantiene el mes en curso (no rompe la app publicada). |

### P2 — más avanzado de lo que la auditoría sabía

| Ítem | Estado |
|---|---|
| Presupuestos por categoría | **Ya existía en esta rama**: `POST`/`GET /finanzas/presupuestos`, `DELETE /finanzas/presupuestos/{id}` (`BUDGET_SET`, `BUDGETS_OK`, `BUDGET_DELETED`), con estado del mes (`consumoPct`, `excedido`). |
| Comparación entre períodos | **Ya existía en esta rama**: `GET /finanzas/analiticas/comparacion-categorias` (`CATEGORY_COMPARISON_OK`), con `deltaPct: null` cuando la base es 0, tal como pedían. |
| Proyección de fin de mes | **Ya existía en esta rama**: `GET /finanzas/proyeccion-mensual` (`MONTHLY_PROJECTION_OK`), run-rate lineal por días calendario, documentado y explicable. Sin meta → `404 META_NO_ENCONTRADA`. |
| Salud financiera ampliada | **Ya hecha** (ver A.22 arriba). Falta solo decidir en qué pantalla reaparece. |
| Recomendaciones | Pendiente de decisión de producto. |
| Reset de estadísticas | De acuerdo: **no se implementa** hasta que negocio defina qué borra. |
| Suscripción / push / referidos / feedback | De acuerdo: son productos. `POST /soporte/feedback` es el candidato al próximo ciclo. |

## 4. Sección D (divergencias) — respuestas

1. **`salud-financiera` huérfano**: el backend no lo toca (retrocompatible, solo agregó campos/codes). La decisión de pantalla es de ustedes; el pipeline de ambos lados está listo.
2. **Vico vs MPAndroidChart**: corregiremos nuestros docs locales. No afecta contrato.
3. **Formato de `fecha`**: definido y documentado en Swagger — ISO datetime completo (ver arriba).
4. **Params ad-hoc de `cuota-diaria`**: se mantienen (son la vía de simulación sin persistir y cuestan cero). Si algún día se eliminan, avisamos con deprecación previa.
5. **Contrato 7 items de `resumen-semanal`**: documentado en Swagger como garantía.
6. **Signo de `cuota-diaria`**: documentado en Swagger como contrato. Además ahora el caso "sin meta" es un `404` limpio y ya no puede producir un falso "Meta superada".
7. **Reenviar OTP**: confirmado, es 100 % frontend — `forgot-password` reenvía y resetea intentos.
8. **Logout solo local**: resuelto con P1.2 (ver arriba).

## 5. Codes nuevos para el catálogo canónico

Éxito: `DAILY_SUMMARY_OK`, `TREND_OK`, `WEEKDAY_INCOME_OK`, `PROFILE_OK`, `PROFILE_UPDATED`,
`LOGGED_OUT`, `CATEGORY_UPDATED`, `CATEGORY_DELETED`.
Error: `PARAMETRO_INVALIDO` (400).
Salud financiera (`data[].code`): `EGRESOS_SUPERAN_INGRESOS`, `TASA_AHORRO_BAJA`,
`TASA_AHORRO_SANA`, `PRESUPUESTO_EXCEDIDO`, `PROYECCION_BAJO_META`.

Ya existentes que ahora aplican a más endpoints: `META_NO_ENCONTRADA` (también en
`cuota-diaria` y `progreso-metas` sin meta activa), `ACCESO_DENEGADO` y
`CATEGORIA_NO_ENCONTRADA` (también en `PUT`/`DELETE` de categorías),
`RANGO_FECHAS_INVALIDO` (transacciones y `resumen-categorias`).

## 6. Decisiones que necesitamos del front

1. **P1.4**: ¿ok `categoriaId=0` como marcador de "sin categoría"?
2. **Semana en `tendencia`**: rotulamos con el lunes (`yyyy-MM-dd`). ¿Les sirve así para el eje o prefieren otro label?
3. **`GET /me`**: usamos `tipoNegocio` (no `negocio`) por consistencia con login. ¿Conforme?
4. **A.13/A.15**: el `404 META_NO_ENCONTRADA` ahora se emite de verdad — verifiquen que el manejo "Sin meta activa" se dispara bien en Dashboard (antes era código muerto).
