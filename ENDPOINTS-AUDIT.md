# Auditoría de endpoints — App Android ↔ Backend

Documento para el equipo de backend: qué endpoints consume la app **hoy**, con qué contrato exacto,
qué opciones de la UI están bloqueadas por endpoints inexistentes, y qué se recomienda construir
(con contrato propuesto y prioridad).

> **Método:** auditoría hecha sobre el **código fuente de la app** (rama `feat/network-core-auth-session`,
> julio 2026): `FinanzasApi.kt`, DTOs, repositorios, ViewModels, Activities y layouts XML.
> Los documentos `API-CONTRACT.md`, `backend-analytics.md` y `backend-profile.md` se usaron solo
> como contraste; ante cualquier diferencia, **lo que dice este documento es lo que la app ejecuta**.

---

## Resumen ejecutivo

- La app consume **19 endpoints** (6 públicos de auth, 13 protegidos). Todos verificados en `FinanzasApi.kt` y rastreados hasta la pantalla que los usa.
- **1 endpoint existe pero ninguna pantalla lo consume**: `GET /finanzas/salud-financiera` (quedó huérfano tras el rediseño de la pantalla de salud; el pipeline data/domain sigue completo).
- **12 opciones visibles en la UI están bloqueadas o degradadas** por falta de endpoint (sección B).
- Lo que más UI desbloquea con menos esfuerzo: **(1)** filtro `desde`/`hasta` en `GET /transacciones`, **(2)** `GET /resumen-diario` (calendario), **(3)** tendencia con granularidad semanal + ventana 1M, **(4)** `GET /usuarios/me`, **(5)** `PUT`/`DELETE` de categorías (sección C, prioridad P0).

---

## 0. Convenciones verificadas en código

Estas reglas están implementadas en `core/network` y aplican a **todos** los endpoints:

- **Base URL**: inyectada por build type (`debug` → `http://10.0.2.2:9090/`, `release` → Azure). El prefijo `/api/v1` va en cada ruta del cliente.
- **Envelope uniforme** (`ApiResponse<T>`): la app deserializa siempre este shape, en éxito y en error:

  ```json
  {
    "timestamp": "2026-07-09T10:30:00",
    "status": 200,
    "code": "TRANSACTIONS_OK",
    "message": "texto humano, la app NO ramifica por acá",
    "data": { },
    "path": "/api/v1/finanzas/transacciones",
    "details": [ { "field": "monto", "rejectedValue": "-5", "message": "..." } ]
  }
  ```

  `details` solo se espera en `VALIDATION_ERROR`.
- **La app ramifica por `code`, nunca por `message`**. Un `code` desconocido cae en `UNKNOWN` y no rompe el cliente, pero tampoco produce comportamiento útil: todo `code` nuevo debe documentarse.
- **Identidad por JWT**: ningún body lleva `usuarioId`. `AuthInterceptor` agrega `Authorization: Bearer <token>` a las rutas protegidas.
- **Refresh automático**: ante un `401`, `TokenAuthenticator` llama `POST /usuarios/refresh` una sola vez (sincronizado entre requests concurrentes), persiste el par rotado y reintenta. Si el refresh falla (`REFRESH_TOKEN_INVALIDO` o error), la sesión se limpia y se navega a Login. **El backend debe mantener la rotación del refresh token en cada uso.**
- **Caché offline (Room)**: la app guarda y sirve sin red la **primera página sin filtros** de transacciones, la lista de categorías y la meta activa. Implicancia: esos tres GET deben seguir siendo estables en shape (el caché replica el modelo).
- **Formatos**: montos `Double` (2 decimales, S/), fechas `String` ISO. `tipo` de transacción/categoría: `"INGRESO"` | `"EGRESO"`.

---

## A. Endpoints existentes (19) — contrato tal como la app los consume

### Índice

| # | Método | Ruta | Consumido por |
|---|--------|------|---------------|
| 1 | POST | `/api/v1/usuarios/registro` | Registro |
| 2 | POST | `/api/v1/usuarios/login` | Login |
| 3 | POST | `/api/v1/usuarios/refresh` | Automático (TokenAuthenticator) |
| 4 | POST | `/api/v1/usuarios/forgot-password` | Olvidé mi contraseña |
| 5 | POST | `/api/v1/usuarios/verify-otp` | Verificación OTP |
| 6 | POST | `/api/v1/usuarios/reset-password` | Nueva contraseña |
| 7 | PUT | `/api/v1/usuarios/me/negocio` | Selección de negocio |
| 8 | POST | `/api/v1/finanzas/transacciones` | FAB "+" en Dashboard, Analytics, Calendar, Profile |
| 9 | GET | `/api/v1/finanzas/transacciones` | Movimientos (paginado), drill-down de Analytics |
| 10 | PUT | `/api/v1/finanzas/transacciones/{id}` | Movimientos (editar) |
| 11 | DELETE | `/api/v1/finanzas/transacciones/{id}` | Movimientos (eliminar) |
| 12 | GET | `/api/v1/finanzas/hoy` | Dashboard (dinero de hoy) |
| 13 | GET | `/api/v1/finanzas/cuota-diaria` | Dashboard (gauge diario) |
| 14 | GET | `/api/v1/finanzas/resumen-semanal` | Dashboard, Analytics, Calendar |
| 15 | GET | `/api/v1/finanzas/progreso-metas` | Dashboard, Calendar |
| 16 | POST | `/api/v1/finanzas/metas` | Dashboard (dialog "Fijar meta") |
| 17 | GET | `/api/v1/finanzas/metas/actual` | Analytics (línea de meta) |
| 18 | GET | `/api/v1/finanzas/categorias` | Categorías, spinners de todos los diálogos |
| 19 | POST | `/api/v1/finanzas/categorias` | Categorías (crear) |
| — | GET | `/api/v1/finanzas/resumen-categorias` | Analytics (ranking de categorías) |
| — | GET | `/api/v1/finanzas/tendencia-mensual` | Analytics (líneas y columnas mensuales) |
| — | GET | `/api/v1/finanzas/salud-financiera` | ⚠️ **Nadie** (ver A.22) |

### A.1 `POST /usuarios/registro` — público

- **Request**: `{ "nombre": string, "email": string, "password": string, "tipoNegocio": string|null }`
  (la pantalla de registro envía `tipoNegocio = null`; se fija después con el endpoint 7).
- **Response `data`**: no se usa (la app trata `data` como vacío).
- **Codes esperados**: éxito `USER_REGISTERED`; error `EMAIL_DUPLICADO`, `VALIDATION_ERROR`.
- Validación en cliente antes de llamar: campos no vacíos, email con `@`, password ≥ 6.

### A.2 `POST /usuarios/login` — público

- **Request**: `{ "email": string, "password": string }`
- **Response `data`** (la app persiste TODO esto en `EncryptedSharedPreferences`):

  ```json
  {
    "token": "jwt-access",
    "refreshToken": "jwt-refresh",
    "usuarioId": 42,
    "nombre": "Juan",
    "email": "juan@example.com",
    "tipoNegocio": "TAXI"
  }
  ```

  `tipoNegocio: null` es significativo: la app rutea a la pantalla de selección de negocio.
- **Codes**: éxito `LOGIN_SUCCESS`; error `UNAUTHORIZED`, `USUARIO_NO_ENCONTRADO`, `VALIDATION_ERROR`.

### A.3 `POST /usuarios/refresh` — público (lo llama el interceptor, no una pantalla)

- **Request**: `{ "refreshToken": string }`
- **Response `data`**: mismo shape que login (par de tokens **rotado**).
- **Codes**: éxito `TOKEN_REFRESHED`; error `REFRESH_TOKEN_INVALIDO` → la app cierra sesión.

### A.4 `POST /usuarios/forgot-password` — público

- **Request**: `{ "email": string }`
- **Response `data`**: vacío. **Codes**: éxito `OTP_SENT`; error `USUARIO_NO_ENCONTRADO`.

### A.5 `POST /usuarios/verify-otp` — público

- **Request**: `{ "email": string, "otp": string }` — la UI fuerza OTP de **exactamente 4 dígitos**.
- **Response `data`**: vacío.
- **Codes**: éxito `OTP_VERIFIED`; error `OTP_INVALIDO`, `OTP_EXPIRADO`, `OTP_BLOQUEADO`
  (la app muestra mensaje distinto por cada uno — estos tres codes son contrato firme).

### A.6 `POST /usuarios/reset-password` — público

- **Request**: `{ "email": string, "otp": string, "newPassword": string }`
- **Codes**: éxito `PASSWORD_RESET_SUCCESS`; error `OTP_INVALIDO` (incluye OTP ya usado), `OTP_EXPIRADO`, `OTP_BLOQUEADO`.

### A.7 `PUT /usuarios/me/negocio` — Bearer

- **Request**: `{ "tipoNegocio": string }`
- **Codes**: éxito `BUSINESS_UPDATED`. La app replica el valor en la sesión local.

### A.8 `POST /finanzas/transacciones` — Bearer

- **Request**:

  ```json
  {
    "monto": 25.50,
    "tipo": "INGRESO",
    "descripcion": "opcional o ausente",
    "fecha": null,
    "categoriaId": 3
  }
  ```

  `descripcion`, `fecha`, `categoriaId` opcionales. **La UI actual nunca envía `fecha`**
  (siempre registra "hoy"); el campo existe en el DTO para cuando el diálogo tenga date picker.
- **Codes**: éxito `TRANSACTION_CREATED`; error `VALIDATION_ERROR`, `CATEGORIA_NO_ENCONTRADA`.
- Cliente valida `monto > 0` antes de llamar.

### A.9 `GET /finanzas/transacciones` — Bearer

- **Query params**: `tipo` (`INGRESO`|`EGRESO`, opcional), `categoriaId` (Long, opcional),
  `page` (default 0), `size` (default 20), `sort` (la app usa `"fecha,desc"`).
- **Response `data`** — página de Spring; la app depende de **estos campos exactos**:

  ```json
  {
    "content": [
      {
        "id": 10, "monto": 25.50, "tipo": "EGRESO",
        "descripcion": "Gasolina", "fecha": "2026-07-09T10:30:00",
        "categoriaId": 3, "categoriaNombre": "Combustible", "usuarioId": 42
      }
    ],
    "totalElements": 57, "totalPages": 3, "number": 0,
    "size": 20, "first": true, "last": false
  }
  ```

- **Codes**: éxito `TRANSACTIONS_OK`.
- **Usos reales**: Movimientos pagina con `page/size/sort` sin filtros; el drill-down de Analytics
  usa `categoriaId=X&size=50` y, para "Sin categoría", `tipo=EGRESO&size=50` filtrando en cliente
  (ver C, P1: filtro sin-categoría server-side).

### A.10 `PUT /finanzas/transacciones/{id}` — Bearer

- **Request**: mismo body que A.8. **Codes**: éxito `TRANSACTION_UPDATED`; error
  `TRANSACCION_NO_ENCONTRADA`, `ACCESO_DENEGADO`, `VALIDATION_ERROR` (la app muestra mensaje distinto por cada uno).

### A.11 `DELETE /finanzas/transacciones/{id}` — Bearer

- **Codes**: éxito `TRANSACTION_DELETED`; error `TRANSACCION_NO_ENCONTRADA`, `ACCESO_DENEGADO`.

### A.12 `GET /finanzas/hoy` — Bearer

- **Response `data`**: `Double` plano (ingreso acumulado de hoy). **Code**: `TODAY_INCOME_OK`.

### A.13 `GET /finanzas/cuota-diaria` — Bearer

- **Query params**: `meta` (Double) y `dias` (Int) existen en el cliente pero **la app siempre llama sin parámetros** (usa la meta persistida). Los params ad-hoc hoy son código muerto en el cliente.
- **Response `data`**: `Double`. **La app interpreta `<= 0` como "meta superada"** — ese signo es contrato.
- **Codes**: éxito `DAILY_QUOTA_OK`; error `META_NO_ENContrADA` → la app muestra "Sin meta activa" (flujo normal, no error fatal).

### A.14 `GET /finanzas/resumen-semanal` — Bearer

- **Response `data`**: lista de items `{ "dia": string, "ingresos": Double, "egresos": Double }`.
- **Contrato implícito que la app asume**: exactamente **7 items, empezando lunes** (Dashboard indexa posiciones 0–6 = Lu–Do y solo renderiza si `size == 7`; Calendar mapea la fecha seleccionada a índice lunes-based). Si el backend cambia orden o cantidad, se rompe silenciosamente.
- **Code**: `WEEKLY_SUMMARY_OK`. Consumidores: Dashboard (barras), Analytics (gráfico "¿Qué día ganás más?"), Calendar (detalle del día — solo semana en curso).

### A.15 `GET /finanzas/progreso-metas` — Bearer

- **Response `data`**:

  ```json
  {
    "ingresoDiario": 80.0, "metaDiaria": 100.0,
    "ingresoSemanal": 450.0, "metaSemanal": 600.0,
    "ingresoMensual": 1800.0, "metaMensual": 2400.0
  }
  ```

- **Codes**: éxito `GOALS_PROGRESS_OK`; error `META_NO_ENCONTRADA` (manejado como estado vacío).

### A.16 `POST /finanzas/metas` — Bearer

- **Request**: `{ "montoObjetivo": 3000.0, "diasLaborables": [1,2,3,4,5] }`
  — convención de días verificada en la UI: **1=lunes … 7=domingo**.
- **Response `data`**: la meta creada (shape de A.17). **Code**: `GOAL_SET`.
- Cliente valida monto > 0 y al menos un día seleccionado.

### A.17 `GET /finanzas/metas/actual` — Bearer

- **Response `data`**: `{ "id": 7, "montoObjetivo": 3000.0, "periodo": "2026-07", "diasLaborables": [1,2,3,4,5], "activa": true }`
- **Codes**: éxito `GOAL_OK`; error `META_NO_ENCONTRADA` → Analytics simplemente no dibuja la línea de meta (estado normal).

### A.18 `GET /finanzas/categorias` — Bearer

- **Response `data`**: lista `{ "id": 3, "nombre": "Combustible", "tipo": "EGRESO" }`.
- **Code**: `CATEGORIES_OK`. Consumidores: pantalla Categorías + spinner de categoría en todos los diálogos de transacción + resolución nombre→id del drill-down de Analytics.

### A.19 `POST /finanzas/categorias` — Bearer

- **Request**: `{ "nombre": string, "tipo": "INGRESO"|"EGRESO" }`
- **Response `data`**: la categoría creada. **Codes**: éxito `CATEGORY_CREATED`; error `VALIDATION_ERROR`.

### A.20 `GET /finanzas/resumen-categorias` — Bearer

- **Response `data`**: mapa plano `{ "Combustible": 320.0, "Peaje": 75.5 }` (nombre → total de egresos del **mes en curso**; el período no es configurable hoy).
- **Code**: `CATEGORY_SUMMARY_OK`. Consumidor: ranking de categorías de Analytics (modo Egresos). Claves que no matcheen una categoría del usuario se tratan como "Sin categoría".

### A.21 `GET /finanzas/tendencia-mensual` — Bearer

- **Query param**: `meses` (Int, opcional). La UI ofrece y envía **3, 6 o 12**; "1M" está deshabilitado (ver B).
- **Response `data`** — tres arrays paralelos, mismo largo:

  ```json
  {
    "meses": ["2026-02", "2026-03", "2026-04"],
    "ingresos": [1200.0, 1500.0, 1100.0],
    "egresos": [600.0, 700.0, 500.0]
  }
  ```

  La app parsea cada entrada de `meses` como `yyyy-MM` para rotular el eje.
- **Code**: `MONTHLY_TREND_OK`. Consumidor: los dos gráficos mensuales de Analytics (+ proyección lineal calculada en el cliente).

### A.22 `GET /finanzas/salud-financiera` — Bearer — ⚠️ SIN CONSUMIDOR EN LA UI

- **Response `data`**: lista `{ "tipo": string, "code": string, "mensaje": string }` con codes
  `GASTO_DIARIO_ALTO`, `META_CERCA`, `META_EN_RIESGO`. **Code** envelope: `FINANCIAL_HEALTH_OK`.
- **Estado real**: el cliente tiene el endpoint, DTO, mapper, modelo de dominio y método de
  repositorio completos, pero **ninguna pantalla lo llama** desde el rediseño de la pantalla de salud.
- **Recomendación**: no tocar el endpoint (el pipeline del cliente está listo); decidir en qué
  pantalla reaparecen las señales (candidato natural: tarjeta en Dashboard o Analytics). Si se
  amplía el catálogo, ver C-P2 (shape ampliado con `severidad`).

---

## B. Opciones visibles en la UI sin endpoint (bloqueadas o degradadas hoy)

Verificado en Activities y layouts — esto es exactamente lo que el usuario ve y no puede usar:

| Pantalla | Opción visible | Estado en código | Endpoint que falta |
|----------|----------------|------------------|--------------------|
| Analytics | Toggle granularidad **"Semana"** | `enabled="false"` en XML ("En desarrollo") | `GET /finanzas/tendencia?granularidad=SEMANA\|MES&ventana=N` (C-P0.3) |
| Analytics | Ventana de período **"1M"** | `enabled="false"` en XML ("En desarrollo") | El mismo endpoint de tendencia con ventana 1 |
| Analytics | Gráfico "¿Qué día ganás más?" | Funciona pero **solo con la semana en curso** (usa `resumen-semanal`) | `GET /finanzas/ingresos-por-dia-semana?ventana=N` (C-P0.4) |
| Analytics | Drill-down de "Sin categoría" | Degradado: trae egresos y filtra en el cliente (solo primera página) | Filtro `categoriaId` nulo server-side en `GET /transacciones` (C-P1.4) |
| Calendar | Detalle de cualquier día fuera de la semana en curso | Muestra "sin datos" (`conDatos = false`, limitación documentada en el ViewModel) | `GET /finanzas/resumen-diario?mes=YYYY-MM` (C-P0.2) |
| Categorías | Corregir/eliminar una categoría | No existe la acción: la pantalla es solo listar+crear | `PUT` y `DELETE /finanzas/categorias/{id}` (C-P0.5) |
| Profile | **Suscripción** | Toast "Próximamente" | Producto completo (C-P2, decisión de negocio) |
| Profile | **Notificaciones** | Toast "Próximamente" | Registro de device token + preferencias (C-P2) |
| Profile | **Ayuda y comentarios** | Toast "Próximamente" | `POST /soporte/feedback` (C-P2) |
| Profile | **Invitar a un amigo** | Toast "Próximamente" | Referidos (C-P2) |
| Profile | **Apariencia** | Toast "Próximamente" | **Ninguno** — es 100 % cliente (dark mode ya resuelto) |
| Account | **Cambiar número de teléfono** | Toast "Próximamente" | `PUT /usuarios/me` con `telefono` (C-P1.1) |
| Account | **Resetear estadísticas mensuales** | Toast "Próximamente" | `POST /usuarios/me/reset-estadisticas` (C-P2, definir alcance) |
| Account | **Eliminar cuenta** | Toast "Próximamente" | `DELETE /usuarios/me` (C-P1.3) |
| Account | Cabecera nombre/email + "Correo electrónico" | Sale del JWT guardado en la sesión, no de la API | `GET /usuarios/me` (C-P0.6) |
| Account | **Cerrar sesión** | Solo borra tokens locales; el refresh token queda vivo en el servidor | `POST /usuarios/logout` (C-P1.2) |
| Verificación OTP | (Ausencia) No hay botón "Reenviar código" | Gap de **UI**, no de backend: `forgot-password` ya sirve para reenviar | Ninguno — trabajo de frontend |

---

## C. Endpoints faltantes — recomendación con contrato

Los contratos siguen las convenciones de la sección 0 (envelope, `code`, JWT, montos con 2
decimales, fechas `YYYY-MM-DD` inclusivas). El detalle extendido vive en `backend-analytics.md` y
`backend-profile.md`; acá va el contrato mínimo verificado contra lo que la UI necesita.

### P0 — Desbloquean UI que ya existe (construir primero)

**P0.1 — `GET /api/v1/finanzas/transacciones` + `desde`/`hasta`** *(cambio, no endpoint nuevo)*
Query params opcionales `desde`/`hasta` (`YYYY-MM-DD`, inclusivos), combinables con los filtros
actuales. Respuesta sin cambios. Error nuevo: `400 RANGO_FECHAS_INVALIDO`.
*Es la base del drill-down por día/mes y de todo período custom. Sin esto, nada de lo demás compone.*

**P0.2 — `GET /api/v1/finanzas/resumen-diario?mes=YYYY-MM`**
Respuesta (`DAILY_SUMMARY_OK`): lista de días **con actividad**:
`[{ "fecha": "2026-07-01", "ingresos": 120.0, "egresos": 40.0 }]`
*Desbloquea el calendario real (hoy solo la semana en curso tiene datos) y habilita heatmap + flujo de caja diario.*

**P0.3 — `GET /api/v1/finanzas/tendencia?granularidad=SEMANA|MES&ventana=N`**
Mismo shape que `tendencia-mensual` (arrays paralelos `periodos/ingresos/egresos`) pero bucketing
semanal cuando `granularidad=SEMANA`. Con `MES` y `ventana=1` habilita el botón "1M".
*Desbloquea los dos toggles deshabilitados de Analytics. Ya está especificado como TODO en `FinanzasApi.kt`.*

**P0.4 — `GET /api/v1/finanzas/ingresos-por-dia-semana?ventana=N`**
Ingreso agregado por día de semana sobre la ventana pedida (no solo la semana actual):
`[{ "dia": "LUNES", "ingresos": 320.0 }]` — 7 items, lunes primero (la app ya asume ese orden).
*Convierte el gráfico "¿Qué día ganás más?" en un insight real en vez de una foto de la semana.*

**P0.5 — `PUT` y `DELETE /api/v1/finanzas/categorias/{id}`**
`PUT` body `{ "nombre": string }` → `CATEGORY_UPDATED`; `DELETE` → `CATEGORY_DELETED`
(recomendado: reasignar transacciones a `categoriaId = null`, no borrarlas).
Errores: `404 CATEGORIA_NO_ENCONTRADA`, `403 ACCESO_DENEGADO`, `400 VALIDATION_ERROR`.
*Hoy un nombre mal escrito queda para siempre.*

**P0.6 — `GET /api/v1/usuarios/me`**
Respuesta (`PROFILE_OK`): `{ "id", "nombre", "email", "telefono", "fotoUrl", "negocio", "plan" }`
(los tres últimos nullable). *Única fuente para la cabecera de Perfil y el detalle de Cuenta, que hoy son una fachada del JWT.*

### P1 — Completan cuenta y coherencia de sesión

**P1.1 — `PUT /api/v1/usuarios/me`** — `{ "nombre"?, "telefono"? }` → `PROFILE_UPDATED`.
Cubre el botón "Cambiar número de teléfono".

**P1.2 — `POST /api/v1/usuarios/logout`** — `{ "refreshToken": "..." }` → `LOGGED_OUT`, idempotente.
Hoy cerrar sesión deja el refresh token válido 30 días en el servidor: es un hueco de seguridad, no una feature.

**P1.3 — `DELETE /api/v1/usuarios/me`** — recomendado con password en el body para confirmar
identidad → `ACCOUNT_DELETED`. Definir soft-delete/período de gracia antes de implementar.

**P1.4 — Filtro "sin categoría" server-side** en `GET /transacciones` (aceptar marcador de
`categoriaId` nulo). Elimina el filtrado en cliente del drill-down (hoy limitado a la primera página).

**P1.5 — `GET /api/v1/finanzas/metas/historial?meses=N`** → `GOALS_HISTORY_OK`:
`[{ "periodo": "2026-06", "metaMensual": 3000.0, "utilidadReal": 2450.0, "cumplida": false }]`
Alimenta el gráfico de cumplimiento histórico.

**P1.6 — `desde`/`hasta` en `GET /resumen-categorias`** (sin params mantiene el mes en curso, para
no romper la app publicada).

### P2 — Producto / requieren decisión de negocio primero

- **Presupuestos por categoría**: `POST|GET /finanzas/presupuestos`, `DELETE /finanzas/presupuestos/{id}` (`BUDGET_SET`, `BUDGETS_OK`, `BUDGET_DELETED`).
- **Comparación entre períodos**: `GET /finanzas/analiticas/comparacion-categorias` (`CATEGORY_COMPARISON_OK`, deltas absolutos y porcentuales, `deltaPct: null` cuando el período base es 0).
- **Proyección de fin de mes**: `GET /finanzas/proyeccion-mensual` (`MONTHLY_PROJECTION_OK`; método de proyección documentado y explicable).
- **Salud financiera ampliada**: mismos 3 codes actuales + `severidad` y nuevos codes deterministas (`PRESUPUESTO_EXCEDIDO`, `EGRESOS_SUPERAN_INGRESOS`, etc.). Retrocompatible: solo agrega campos. *Coordinar con A.22: primero decidir dónde se muestra en la app.*
- **Recomendaciones**: `GET /finanzas/recomendaciones` (`RECOMMENDATIONS_OK`, catálogo cerrado de `code` + `prioridad` + `accionSugerida`).
- **Reset de estadísticas**: `POST /usuarios/me/reset-estadisticas` (`STATS_RESET`) — **no implementar** hasta que negocio defina qué borra exactamente.
- **Suscripción, notificaciones push, referidos, feedback**: son productos, no endpoints sueltos. El mínimo viable inmediato es `POST /soporte/feedback` (`FEEDBACK_RECEIVED`).

Todo `code` nuevo (éxito y error) debe entrar al catálogo canónico antes del deploy: la app
ramifica por `code` y un valor no catalogado cae en `UNKNOWN`.

---

## D. Divergencias y hallazgos (código vs documentación)

1. **`salud-financiera` huérfano**: el endpoint existe y el cliente tiene todo el pipeline, pero ninguna pantalla lo consume (A.22). Decidir dónde reaparece antes de invertir en ampliar su catálogo.
2. **Librería de gráficos**: `backend-analytics.md` dice MPAndroidChart; el código usa **Vico**. No afecta contratos (el servidor solo manda datos), pero conviene corregir el doc.
3. **`fecha` en el registro de transacciones**: el DTO la soporta, la UI nunca la envía. Cuando el diálogo tenga date picker, el backend ya está listo — validar formato aceptado (la app recibiría `YYYY-MM-DD` o ISO completo; definirlo explícitamente en Swagger).
4. **Params ad-hoc de `cuota-diaria` (`meta`, `dias`)**: declarados en el cliente, jamás usados. Mantenerlos en el backend es barato; si se eliminan, avisar para limpiar el cliente.
5. **Contrato implícito de `resumen-semanal`**: la app asume 7 items empezando lunes (Dashboard descarta la respuesta si `size != 7`). Documentar ese orden en Swagger como garantía.
6. **Semántica de signo en `cuota-diaria`**: valor `<= 0` significa "meta superada" para la UI. Documentarlo como contrato.
7. **Reenviar OTP**: no requiere backend (reutiliza `forgot-password`); es un pendiente de frontend en la pantalla de verificación.
8. **Logout solo local**: hasta que exista P1.2, un refresh token robado sigue siendo válido tras "cerrar sesión". Priorizarlo como ítem de seguridad, no de features.
