# Readiness del backend — contrato para el frontend

Estado del contrato de la API para que el equipo Kotlin integre sin sorpresas.
Cierra la Fase 5.5 del [README-PLAN.md](README-PLAN.md).

> **Fuente de verdad viva:** el contrato siempre actualizado está en Swagger:
> - Swagger UI: `http://<host>:9090/swagger-ui.html`
> - OpenAPI JSON: `http://<host>:9090/v3/api-docs`

## Checklist de "backend listo para fusión"

| # | Criterio | Estado |
|---|---|---|
| 1 | Endpoints expuestos en OpenAPI/Swagger | ✅ |
| 2 | Envelope de éxito y de error consistente en todos los endpoints | ✅ |
| 3 | Códigos `code` estables y documentados (el frontend decide por `code`, no por `message`) | ✅ (este doc) |
| 4 | 401 real en sesión vencida / token inválido (no 500) | ✅ (probado) |
| 5 | Historial, meta persistida y categorías expuestos y probados | ✅ (65 tests) |
| 6 | Manejo de sesión con refresh token definido y probado | ✅ |
| 7 | Secretos fuera del repo | ✅ (`application*.properties` gitignored) |
| 8 | Credenciales rotadas | ⚠️ acción del responsable (ver nota) |
| 9 | Perfil `prod` desplegado en Azure y accesible | ⏳ pendiente (infra en paralelo) |

**Nota #8:** los secretos ya no viven en el repo; la rotación efectiva de las
credenciales históricamente filtradas (Gmail app password, `jwt.secret`, password
de BD) es una acción operativa del responsable, fuera del código.

## Formato de respuesta (envelope)

**Éxito** (HTTP 2xx):
```json
{
  "timestamp": "2026-06-13T10:00:00",
  "status": 200,
  "code": "LOGIN_SUCCESS",
  "message": "Login exitoso",
  "data": { },
  "path": "/api/v1/usuarios/login"
}
```

**Error** (HTTP 4xx/5xx):
```json
{
  "timestamp": "2026-06-13T10:00:00",
  "status": 401,
  "code": "UNAUTHORIZED",
  "message": "Error de autenticación: ...",
  "details": [ ],
  "path": "/api/v1/usuarios/login"
}
```

`details` solo aparece en errores de validación (`VALIDATION_ERROR`), con un item por campo.

## Contrato de sesión (token + refresh)

- `login` y `refresh` devuelven `token` (access JWT, **15 min**) y `refreshToken` (**30 días**).
- Las rutas protegidas se llaman con header `Authorization: Bearer <token>`.
- Ante **401** en una ruta protegida → `POST /api/v1/usuarios/refresh` con `{ "refreshToken": "..." }`.
- El refresh **rota**: cada uso invalida el `refreshToken` anterior. El cliente
  debe **guardar el nuevo `refreshToken`** de cada respuesta.
- Si `/refresh` devuelve **401 `REFRESH_TOKEN_INVALIDO`** → enviar al login.

## Códigos `code` de éxito por endpoint

### Cuenta / autenticación — `/api/v1/usuarios`
| Método | Ruta | `code` |
|---|---|---|
| POST | `/registro` | `USER_REGISTERED` |
| POST | `/login` | `LOGIN_SUCCESS` |
| POST | `/refresh` | `TOKEN_REFRESHED` |
| POST | `/forgot-password` | `OTP_SENT` |
| POST | `/verify-otp` | `OTP_VERIFIED` |
| POST | `/reset-password` | `PASSWORD_RESET_SUCCESS` |
| PUT | `/me/negocio` | `BUSINESS_UPDATED` |

### Finanzas — `/api/v1/finanzas`
| Método | Ruta | `code` |
|---|---|---|
| POST | `/transacciones` | `TRANSACTION_CREATED` |
| GET | `/transacciones` | `TRANSACTIONS_OK` |
| PUT | `/transacciones/{id}` | `TRANSACTION_UPDATED` |
| DELETE | `/transacciones/{id}` | `TRANSACTION_DELETED` |
| GET | `/cuota-diaria` | `DAILY_QUOTA_OK` |
| GET | `/hoy` | `TODAY_INCOME_OK` |
| GET | `/resumen-semanal` | `WEEKLY_SUMMARY_OK` |
| GET | `/progreso-metas` | `GOALS_PROGRESS_OK` |
| GET | `/resumen-categorias` | `CATEGORY_SUMMARY_OK` |
| GET | `/tendencia-mensual` | `MONTHLY_TREND_OK` |
| GET | `/salud-financiera` | `FINANCIAL_HEALTH_OK` |
| GET | `/analiticas/comparacion-categorias` | `CATEGORY_COMPARISON_OK` |
| POST | `/presupuestos` | `BUDGET_SET` |
| GET | `/presupuestos` | `BUDGETS_OK` |
| DELETE | `/presupuestos/{id}` | `BUDGET_DELETED` |
| POST | `/metas` | `GOAL_SET` |
| GET | `/metas/actual` | `GOAL_OK` |
| POST | `/categorias` | `CATEGORY_CREATED` |
| GET | `/categorias` | `CATEGORIES_OK` |

**Filtro por rango de fechas (opcional).** `GET /transacciones` y
`GET /resumen-categorias` aceptan `desde` y `hasta` (`YYYY-MM-DD`, ambos
inclusivos). Sin ellos, el comportamiento no cambia (`resumen-categorias`
sigue devolviendo el mes en curso). `comparacion-categorias` usa los mismos
`desde`/`hasta` más `compararCon` (`PERIODO_ANTERIOR` por defecto o
`MISMO_PERIODO_ANIO_ANTERIOR`).

## Catálogo de códigos `code` de error

| `code` | HTTP | Cuándo |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Falla de validación de campos (incluye `details`) |
| `MALFORMED_JSON` | 400 | JSON de la request mal formado |
| `RANGO_FECHAS_INVALIDO` | 400 | `desde > hasta`, o fecha (`desde`/`hasta`) mal formada |
| `PARAMETRO_INVALIDO` | 400 | Query param con tipo/formato inválido (que no sea fecha) |
| `UNAUTHORIZED` | 401 | Sin token, token inválido/expirado, o credenciales de login inválidas |
| `REFRESH_TOKEN_INVALIDO` | 401 | Refresh token inexistente, expirado o ya usado (rotado) |
| `CREDENCIALES_INVALIDAS` | 401 | Credenciales inválidas (variante explícita) |
| `FORBIDDEN` | 403 | Acceso denegado por Spring Security |
| `ACCESO_DENEGADO` | 403 | El recurso pertenece a otro usuario |
| `USUARIO_NO_ENCONTRADO` | 404 | Usuario inexistente |
| `TRANSACCION_NO_ENCONTRADA` | 404 | Transacción inexistente |
| `META_NO_ENCONTRADA` | 404 | No hay meta activa para el período actual |
| `CATEGORIA_NO_ENCONTRADA` | 404 | Categoría inexistente |
| `PRESUPUESTO_NO_ENCONTRADO` | 404 | Presupuesto inexistente |
| `EMAIL_DUPLICADO` | 422 | Email ya registrado |
| `OTP_INVALIDO` | 422 | Código OTP inválido (o email inexistente, sin distinción) |
| `OTP_EXPIRADO` | 422 | Código OTP expirado |
| `OTP_BLOQUEADO` | 429 | Demasiados intentos OTP fallidos |
| `INTERNAL_SERVER_ERROR` | 500 | Error inesperado del servidor |

> El frontend debe ramificar **siempre por `code`**, nunca por `message` (el
> mensaje es para humanos y puede cambiar).

## Pendiente real

- **#6 — Rotación efectiva de credenciales**: Gmail app password, `jwt.secret`
  y password de BD siguen siendo los históricamente filtrados. Sacarlos del
  repo (ya hecho) no es lo mismo que rotarlos. Acción operativa fuera de
  código, marcada ⚠️ en el criterio 8 de la checklist.
- **#9 — Despliegue Azure App Service**: criterio 9 de la checklist en ⏳.
  Ya se verificó la **conexión** a PostgreSQL Azure corriendo el perfil `prod`
  en local (Hikari conecta, Flyway valida) — eso NO es lo mismo que tener el
  backend **desplegado** en Azure App Service. Falta confirmar si el deploy
  ya se hizo o sigue pendiente.
- **#12 — CORS**: sin tocar a propósito. Correcto según el plan: se activa
  recién cuando entre un cliente basado en navegador (web/smartwatch/flota).
  No es un pendiente real, es una decisión tomada.
- **#18 — Metadatos `pom.xml`**: cosmético. `<license/>`, `<developers/>` y
  `<scm/>` siguen vacíos en el POM.
