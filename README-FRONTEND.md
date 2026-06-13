# Guía de integración Frontend

Todo lo necesario para conectar el cliente con esta API. El cliente del MVP es una **app móvil nativa Android en Kotlin**; el procesamiento matemático pesado vive en el servidor para no agotar la batería del dispositivo.

> **Contrato vivo (fuente de verdad):** Swagger siempre refleja el estado real del backend.
> - Swagger UI: `http://localhost:9090/swagger-ui.html`
> - OpenAPI JSON: `http://localhost:9090/v3/api-docs`
>
> Este documento es la guía explicada; ante cualquier diferencia, manda Swagger.
> El catálogo canónico de códigos `code` está en [README-READINESS.md](README-READINESS.md).

## Datos base

| Concepto | Valor |
|---|---|
| URL base local | `http://localhost:9090` |
| Prefijo de rutas | `/api/v1` |
| Formato | JSON (`Content-Type: application/json`) |
| Autenticación | JWT Bearer en header `Authorization` |
| Vigencia del access token | 15 minutos |
| Vigencia del refresh token | 30 días (rota en cada uso) |

> **CORS**: no afecta a un cliente móvil nativo (la política de mismo origen es del navegador, no de un HTTP client de Android). Si más adelante se conecta un cliente **web, smartwatch o panel de flota**, habrá que habilitar CORS en `SecurityConfig` primero. Para el MVP Android nativo, no es bloqueante.

## Formato de respuesta (envelope)

**Toda respuesta exitosa** usa este envelope:

```json
{
  "timestamp": "2026-06-13T10:30:00.123",
  "status": 200,
  "code": "LOGIN_SUCCESS",
  "message": "Login exitoso",
  "data": { },
  "path": "/api/v1/usuarios/login"
}
```

**Toda respuesta de error** usa este formato (`details` solo aparece en errores de validación):

```json
{
  "timestamp": "2026-06-13T10:30:00.123",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Validación fallida",
  "details": [
    { "field": "email", "rejectedValue": "abc", "message": "Formato de correo electrónico inválido" }
  ],
  "path": "/api/v1/usuarios/registro"
}
```

El cliente debe decidir **siempre por el campo `code`**, nunca por el `message` (los mensajes son para humanos y pueden cambiar).

## Flujo de autenticación

1. `POST /registro` → crear cuenta (acepta y **guarda** `tipoNegocio`).
2. `POST /login` → recibir `token`, `refreshToken` y datos del usuario.
3. Guardar ambos tokens de forma segura (Android Keystore / EncryptedSharedPreferences) y enviar el access token en cada request protegido: `Authorization: Bearer <token>`.
4. Ante **401** en una ruta protegida → llamar a `POST /refresh` con el `refreshToken` guardado.
5. `/refresh` devuelve un **nuevo** `token` y un **nuevo** `refreshToken` (rotación): guardar ambos y reintentar el request original.
6. Si `/refresh` devuelve **401 `REFRESH_TOKEN_INVALIDO`** → la sesión murió: redirigir a login.

> Un token **expirado o malformado** en ruta protegida devuelve **401 `UNAUTHORIZED`** (no 500). El cliente puede tratar el 401 como disparador del flujo de refresh descrito arriba.

---

## Endpoints públicos (sin token)

### POST `/api/v1/usuarios/registro`

Request:
```json
{
  "nombre": "Juan Pérez",
  "email": "juan@correo.com",
  "password": "abc12345",
  "tipoNegocio": "TAXI"
}
```

Validaciones: `nombre` obligatorio; `email` válido y obligatorio; `password` 8–72 caracteres con al menos una letra y un número; `tipoNegocio` opcional, valores: `BODEGA`, `TAXI`, `FREELANCE`, `PERSONALIZADO`. Para el MVP de taxistas, enviar `TAXI`.

Respuesta exitosa: `code: "USER_REGISTERED"`, `data: null`. El `tipoNegocio` enviado **se persiste** y aparecerá en el login.

Errores: `422 EMAIL_DUPLICADO` si el correo ya existe; `400 VALIDATION_ERROR`.

### POST `/api/v1/usuarios/login`

Request:
```json
{ "email": "juan@correo.com", "password": "abc12345" }
```

Respuesta exitosa (`code: "LOGIN_SUCCESS"`):
```json
{
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "f1c2...e9",
    "usuarioId": 1,
    "nombre": "Juan Pérez",
    "email": "juan@correo.com",
    "tipoNegocio": "TAXI"
  }
}
```

Errores: `401 UNAUTHORIZED` con credenciales incorrectas.

### POST `/api/v1/usuarios/refresh`

Intercambia un refresh token válido por un access token nuevo y un refresh token rotado.

Request:
```json
{ "refreshToken": "f1c2...e9" }
```

Respuesta exitosa (`code: "TOKEN_REFRESHED"`): mismo shape que el login (`token`, `refreshToken`, datos del usuario). El `refreshToken` anterior queda invalidado: guardar el nuevo.

Errores: `401 REFRESH_TOKEN_INVALIDO` (inexistente, expirado o ya usado).

### POST `/api/v1/usuarios/forgot-password`

Request: `{ "email": "juan@correo.com" }`

Siempre responde `200` con `code: "OTP_SENT"` exista o no el correo (anti-enumeración). Si existe, envía por email un OTP de 4 dígitos válido por 10 minutos.

### POST `/api/v1/usuarios/verify-otp`

Request: `{ "email": "juan@correo.com", "otp": "1234" }`

`otp`: exactamente 4 dígitos. Respuesta exitosa: `code: "OTP_VERIFIED"`. Este paso es opcional para UX (no consume el código).

Errores: `422 OTP_INVALIDO` (código incorrecto **o email inexistente**, sin distinción), `422 OTP_EXPIRADO`, `429 OTP_BLOQUEADO` (tras 5 intentos fallidos).

### POST `/api/v1/usuarios/reset-password`

Request:
```json
{ "email": "juan@correo.com", "otp": "1234", "newPassword": "nueva1234" }
```

`newPassword`: mínimo 8 caracteres. Vuelve a validar el OTP. Respuesta exitosa: `code: "PASSWORD_RESET_SUCCESS"`.

Errores: `422 OTP_INVALIDO`, `422 OTP_EXPIRADO`, `429 OTP_BLOQUEADO`.

---

## Endpoints protegidos (requieren `Authorization: Bearer <token>`)

### PUT `/api/v1/usuarios/me/negocio`

Actualiza el tipo de negocio del usuario autenticado.

Request: `{ "tipoNegocio": "TAXI" }` (obligatorio, mismos valores del enum).

Respuesta exitosa: `code: "BUSINESS_UPDATED"`.

### POST `/api/v1/finanzas/transacciones`

Registra un ingreso (viaje) o egreso (gasolina, peaje, alimentación).

Request:
```json
{
  "monto": 150.50,
  "tipo": "INGRESO",
  "descripcion": "Carrera al aeropuerto",
  "fecha": "2026-06-12T10:30:00",
  "categoriaId": 4
}
```

Validaciones:
- `monto` obligatorio, mínimo `0.01`.
- `tipo` obligatorio, `INGRESO` o `EGRESO` (case-insensitive, se normaliza a mayúsculas).
- `descripcion` opcional, máx. 500 caracteres. **Se persiste.**
- `fecha` opcional; si se omite, el servidor usa el momento actual. **Se persiste** (permite registrar movimientos con fecha pasada).
- `categoriaId` opcional; debe ser una categoría base o propia del usuario (ver `GET /categorias`). Si es `null`, el movimiento queda sin categoría.

Respuesta exitosa: `code: "TRANSACTION_CREATED"`, `data: null`.

Errores: `404 CATEGORIA_NO_ENCONTRADA` si el `categoriaId` no existe o no es visible para el usuario; `400 VALIDATION_ERROR`.

### GET `/api/v1/finanzas/transacciones`

Historial paginado del usuario, más reciente primero por defecto.

| Query param | Tipo | Descripción |
|---|---|---|
| `tipo` | string (opcional) | Filtra por `INGRESO` o `EGRESO` |
| `categoriaId` | entero (opcional) | Filtra por categoría |
| `page` | entero (opcional) | Página (base 0, default `0`) |
| `size` | entero (opcional) | Tamaño de página (default `20`) |
| `sort` | string (opcional) | Campo + dirección, ej. `fecha,desc` (default `fecha,desc`) |

Respuesta (`code: "TRANSACTIONS_OK"`) — `data` es una página de Spring:
```json
{
  "data": {
    "content": [
      {
        "id": 12,
        "monto": 150.50,
        "tipo": "INGRESO",
        "descripcion": "Carrera al aeropuerto",
        "fecha": "2026-06-12T10:30:00",
        "categoriaId": 4,
        "categoriaNombre": "Carreras",
        "usuarioId": 1
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "number": 0,
    "size": 20,
    "first": true,
    "last": true
  }
}
```

`categoriaId` y `categoriaNombre` llegan `null` cuando el movimiento no tiene categoría.

### PUT `/api/v1/finanzas/transacciones/{id}`

Actualiza una transacción propia. Mismo body que el registro **más** el campo `id`:
```json
{
  "id": 12,
  "monto": 180.00,
  "tipo": "INGRESO",
  "descripcion": "Carrera larga",
  "fecha": "2026-06-12T10:30:00",
  "categoriaId": 4
}
```

`categoriaId: null` quita la categoría del movimiento. Respuesta (`code: "TRANSACTION_UPDATED"`) devuelve la transacción actualizada (mismo shape que un item del historial).

Errores: `404 TRANSACCION_NO_ENCONTRADA`, `403 ACCESO_DENEGADO` (la transacción es de otro usuario), `404 CATEGORIA_NO_ENCONTRADA`.

### DELETE `/api/v1/finanzas/transacciones/{id}`

Elimina una transacción propia. Respuesta: `code: "TRANSACTION_DELETED"`, `data: null`.

Errores: `404 TRANSACCION_NO_ENCONTRADA`, `403 ACCESO_DENEGADO`.

### GET `/api/v1/finanzas/hoy`

Suma de ingresos del día actual (00:00 a 23:59 hora del servidor). Alimenta el **termómetro diario**.

Respuesta: `code: "TODAY_INCOME_OK"`, `data` decimal (`450.00`). Devuelve `0` si no hay ingresos.

### GET `/api/v1/finanzas/cuota-diaria`

Cuánto debe ganar por día (en **utilidad neta**) para alcanzar la meta mensual en los días laborables restantes. Núcleo del **motor dinámico de metas**.

| Query param | Tipo | Descripción |
|---|---|---|
| `meta` | decimal (opcional) | Meta mensual de utilidad |
| `dias` | entero (opcional) | Días restantes |

**Sin parámetros**, lee la meta y los días laborables restantes de la **meta persistida** del usuario (ver `POST /metas`). Pasar `meta`/`dias` los sobrescribe para el cálculo.

Respuesta: `code: "DAILY_QUOTA_OK"`, `data` decimal.

Semántica del valor:
- **Positivo**: cuota diaria que falta ganar (faltante ÷ días restantes, redondeo a 2 decimales).
- **Negativo**: la meta ya se superó; `abs(valor)` es el excedente. Mostrar "meta superada por X".
- El cálculo usa **utilidad neta del mes en curso** (ingresos − egresos del mes actual), no el acumulado histórico.

### GET `/api/v1/finanzas/resumen-semanal`

Ingresos y egresos por día de la semana actual (lunes a domingo). Alimenta el **analista semanal**.

Respuesta (`code: "WEEKLY_SUMMARY_OK"`) — siempre los 7 días, en orden, con ceros si no hubo movimientos:

```json
{
  "data": [
    { "dia": "Lunes", "ingresos": 120.00, "egresos": 30.00 },
    { "dia": "Martes", "ingresos": 0, "egresos": 0 },
    { "dia": "Miércoles", "ingresos": 0, "egresos": 0 },
    { "dia": "Jueves", "ingresos": 0, "egresos": 0 },
    { "dia": "Viernes", "ingresos": 0, "egresos": 0 },
    { "dia": "Sábado", "ingresos": 0, "egresos": 0 },
    { "dia": "Domingo", "ingresos": 0, "egresos": 0 }
  ]
}
```

### GET `/api/v1/finanzas/progreso-metas`

Progreso consolidado día/semana/mes. Alimenta el **resumen global**. Mismos query params (opcionales) que `cuota-diaria`; sin ellos usa la meta persistida.

Respuesta (`code: "GOALS_PROGRESS_OK"`):
```json
{
  "data": {
    "ingresoDiario": 80.00,
    "metaDiaria": 95.50,
    "ingresoSemanal": 560.00,
    "metaSemanal": 668.50,
    "ingresoMensual": 1567.00,
    "metaMensual": 3000
  }
}
```

Los indicadores de ingreso son **brutos** (solo ingresos); solo `metaDiaria` usa el motor de utilidad neta. `metaSemanal` = `metaDiaria × 7`. Si la meta ya se superó, `metaDiaria` llega negativa (misma semántica que `cuota-diaria`).

---

## Metas (persistidas por usuario)

### POST `/api/v1/finanzas/metas`

Fija o actualiza la meta del mes en curso junto con los días laborables.

Request:
```json
{
  "montoObjetivo": 3000.00,
  "diasLaborables": [1, 2, 3, 4, 5]
}
```

Validaciones: `montoObjetivo` obligatorio, mínimo `0.01`; `diasLaborables` lista no vacía de enteros 1–7 (1=Lunes … 7=Domingo).

Respuesta (`code: "GOAL_SET"`):
```json
{
  "data": {
    "id": 1,
    "montoObjetivo": 3000.00,
    "periodo": "2026-06",
    "diasLaborables": [1, 2, 3, 4, 5],
    "activa": true
  }
}
```

### GET `/api/v1/finanzas/metas/actual`

Devuelve la meta activa del período actual. Respuesta `code: "GOAL_OK"`, mismo shape que arriba.

Errores: `404 META_NO_ENCONTRADA` si el usuario aún no fijó meta este mes.

---

## Categorías

Existen **categorías base** (compartidas, creadas por el sistema) y **categorías propias** del usuario. Ambas son visibles vía `GET /categorias` y usables en `categoriaId` de las transacciones.

### GET `/api/v1/finanzas/categorias`

Lista las categorías base + las propias del usuario.

Respuesta (`code: "CATEGORIES_OK"`):
```json
{
  "data": [
    { "id": 1, "nombre": "Gasolina", "tipo": "EGRESO" },
    { "id": 2, "nombre": "Peaje", "tipo": "EGRESO" },
    { "id": 4, "nombre": "Carreras", "tipo": "INGRESO" }
  ]
}
```

### POST `/api/v1/finanzas/categorias`

Crea una categoría propia del usuario.

Request: `{ "nombre": "Mantenimiento", "tipo": "EGRESO" }` (`nombre` obligatorio; `tipo` obligatorio, `INGRESO` o `EGRESO`).

Respuesta (`code: "CATEGORY_CREATED"`): la categoría creada con su `id`.

---

## Analíticas (solo lectura — el teléfono recibe JSON listo para graficar)

### GET `/api/v1/finanzas/resumen-categorias`

Egresos del mes en curso agrupados por categoría (para gráfico de torta). Los movimientos sin categoría se agrupan bajo `"Sin categoría"`.

Respuesta (`code: "CATEGORY_SUMMARY_OK"`) — mapa `nombre → total`:
```json
{
  "data": {
    "Gasolina": 320.00,
    "Peaje": 75.50,
    "Sin categoría": 40.00
  }
}
```

### GET `/api/v1/finanzas/tendencia-mensual`

Ingresos/egresos totales de los últimos N meses (para gráfico de líneas). Arrays paralelos indexados por mes, **más antiguo primero**.

| Query param | Tipo | Descripción |
|---|---|---|
| `meses` | entero (opcional) | Ventana de meses (default `6`) |

Respuesta (`code: "MONTHLY_TREND_OK"`):
```json
{
  "data": {
    "meses": ["2026-01", "2026-02", "2026-03"],
    "ingresos": [2400.00, 2650.00, 3100.00],
    "egresos": [800.00, 910.00, 1050.00]
  }
}
```

### GET `/api/v1/finanzas/salud-financiera`

Conjunto determinista de señales de salud financiera (alertas y felicitaciones). El cliente ramifica por `code`, nunca por `mensaje`.

Respuesta (`code: "FINANCIAL_HEALTH_OK"`) — lista (puede venir vacía):
```json
{
  "data": [
    { "tipo": "ALERTA", "code": "GASTO_DIARIO_ALTO", "mensaje": "Tus gastos de hoy superan tu meta de ganancia diaria." },
    { "tipo": "FELICITACION", "code": "META_CERCA", "mensaje": "¡Vas excelente! Alcanzaste el 80% de tu meta del mes." }
  ]
}
```

Códigos de señal posibles: `GASTO_DIARIO_ALTO`, `META_CERCA`, `META_EN_RIESGO`.

---

## Catálogo de códigos de error

Catálogo canónico y siempre actualizado en [README-READINESS.md](README-READINESS.md). Resumen:

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Falla de validación (trae `details[]`) |
| 400 | `MALFORMED_JSON` | Body JSON inválido |
| 401 | `UNAUTHORIZED` | Sin token, token inválido/expirado, o credenciales incorrectas en login |
| 401 | `REFRESH_TOKEN_INVALIDO` | Refresh token inexistente, expirado o ya usado |
| 403 | `FORBIDDEN` / `ACCESO_DENEGADO` | Sin permisos / el recurso es de otro usuario |
| 404 | `USUARIO_NO_ENCONTRADO` | Usuario inexistente |
| 404 | `TRANSACCION_NO_ENCONTRADA` | Transacción inexistente |
| 404 | `META_NO_ENCONTRADA` | No hay meta activa este período |
| 404 | `CATEGORIA_NO_ENCONTRADA` | Categoría inexistente o no visible |
| 422 | `EMAIL_DUPLICADO` | Registro con correo ya existente |
| 422 | `OTP_INVALIDO` | OTP incorrecto (o email inexistente) |
| 422 | `OTP_EXPIRADO` | OTP vencido (>10 min) |
| 429 | `OTP_BLOQUEADO` | Demasiados intentos OTP fallidos |
| 500 | `INTERNAL_SERVER_ERROR` | Error no controlado |
