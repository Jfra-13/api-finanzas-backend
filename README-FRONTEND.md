# Guía de integración Frontend

Todo lo necesario para conectar el cliente con esta API. El cliente previsto del MVP es una **app móvil nativa en Kotlin Multiplatform**; el procesamiento matemático pesado vive del lado del servidor para no agotar la batería del dispositivo.

## Datos base

| Concepto | Valor |
|---|---|
| URL base local | `http://localhost:9090` |
| Prefijo de rutas | `/api/v1` |
| Formato | JSON (`Content-Type: application/json`) |
| Autenticación | JWT Bearer en header `Authorization` |
| Vigencia del token | 24 horas (no hay refresh token) |

> **CORS**: no afecta a un cliente móvil nativo (la política de mismo origen es del navegador, no de un HTTP client de Android/iOS). Si en el futuro se conecta un cliente **web, smartwatch o panel de flota** (visión a largo plazo del proyecto), habrá que configurar CORS en el backend primero. Para el MVP en Kotlin nativo, no es bloqueante.

## Formato de respuesta (envelope)

**Toda respuesta exitosa** usa este envelope:

```json
{
  "timestamp": "2026-06-12T10:30:00.123",
  "status": 200,
  "code": "LOGIN_SUCCESS",
  "message": "Login exitoso",
  "data": { },
  "path": "/api/v1/usuarios/login"
}
```

**Toda respuesta de error** usa este formato (campo `details` solo aparece en errores de validación):

```json
{
  "timestamp": "2026-06-12T10:30:00.123",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Validación fallida",
  "details": [
    { "field": "email", "rejectedValue": "abc", "message": "Formato de correo electrónico inválido" }
  ],
  "path": "/api/v1/usuarios/registro"
}
```

El cliente debe decidir por el campo `code`, no por el `message` (los mensajes pueden cambiar).

## Flujo de autenticación

1. `POST /registro` → crear cuenta.
2. `POST /login` → recibir `token` y datos del usuario.
3. Guardar el token de forma segura (Keychain/Keystore) y enviarlo en cada request protegido: `Authorization: Bearer <token>`.
4. Si el backend responde `401`, redirigir a login (token vencido o inválido).

> ⚠️ **Bug conocido**: hoy un token **expirado o malformado** devuelve `500` en lugar de `401` (la excepción del filtro JWT no está manejada). El cliente debería tratar `500` en rutas protegidas como posible sesión vencida hasta que se corrija. Es una corrección crítica en README-MEJORAS.md.

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

Validaciones: `nombre` obligatorio; `email` válido y obligatorio; `password` 8–72 caracteres con al menos una letra y un número; `tipoNegocio` opcional, valores: `BODEGA`, `TAXI`, `FREELANCE`, `PERSONALIZADO`. En el MVP de taxistas, enviar `TAXI`.

Respuesta exitosa: `code: "USER_REGISTERED"`, `data: null`.

> ⚠️ **Bug conocido**: `tipoNegocio` se acepta pero **no se guarda** en el registro. Tras registrarse, el usuario queda sin tipo de negocio hasta que se use `PUT /me/negocio`. El cliente debe contemplar `tipoNegocio: null` en el login.

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
    "usuarioId": 1,
    "nombre": "Juan Pérez",
    "email": "juan@correo.com",
    "tipoNegocio": null
  }
}
```

Errores: `401 UNAUTHORIZED` con credenciales incorrectas.

### POST `/api/v1/usuarios/forgot-password`

Request: `{ "email": "juan@correo.com" }`

Siempre responde `200` con `code: "OTP_SENT"` exista o no el correo (anti-enumeración). Si existe, envía por email un OTP de 4 dígitos válido por 10 minutos.

### POST `/api/v1/usuarios/verify-otp`

Request: `{ "email": "juan@correo.com", "otp": "1234" }`

`otp`: exactamente 4 dígitos. Respuesta exitosa: `code: "OTP_VERIFIED"`.

Errores: `404 USUARIO_NO_ENCONTRADO`, `422 OTP_INVALIDO`, `422 OTP_EXPIRADO`.

### POST `/api/v1/usuarios/reset-password`

Request:
```json
{ "email": "juan@correo.com", "otp": "1234", "newPassword": "nueva1234" }
```

`newPassword`: mínimo 8 caracteres. Vuelve a validar el OTP (el paso `verify-otp` es opcional para UX, no consume el código). Respuesta exitosa: `code: "PASSWORD_RESET_SUCCESS"`.

Errores: `404 USUARIO_NO_ENCONTRADO`, `422 OTP_INVALIDO`, `422 OTP_EXPIRADO`.

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
  "descripcion": "Venta del día",
  "fecha": "2026-06-12T10:30:00"
}
```

Validaciones: `monto` obligatorio, mínimo `0.01`; `tipo` obligatorio, `INGRESO` o `EGRESO` (case-insensitive, se normaliza a mayúsculas); `descripcion` opcional, máx. 500 caracteres.

Respuesta exitosa: `code: "TRANSACTION_CREATED"`, `data: null`.

> ⚠️ **Bug conocido**: `descripcion` y `fecha` se aceptan en el request pero **se descartan** — la entidad no tiene campo descripción y la fecha siempre se asigna al momento del servidor. Tampoco existe aún el campo **categoría** que el MVP necesita para los egresos. No ofrecer en UI "registrar con fecha pasada", "agregar nota" ni "categorizar egreso" hasta corregirlo (es prioridad en README-MEJORAS.md).

### GET `/api/v1/finanzas/hoy`

Suma de ingresos del día actual (00:00 a 23:59 hora del servidor). Alimenta el **termómetro diario** (anillos de progreso de la cuota de hoy).

Respuesta: `code: "TODAY_INCOME_OK"`, `data` es un número decimal (`450.00`). Devuelve `0` si no hay ingresos.

### GET `/api/v1/finanzas/cuota-diaria?meta=3000&dias=15`

Cuánto debe ganar por día para alcanzar la meta mensual en los días restantes. Es el núcleo del **motor dinámico de metas**.

| Query param | Tipo | Descripción |
|---|---|---|
| `meta` | decimal | Meta mensual de ingresos |
| `dias` | entero | Días restantes del mes |

Respuesta: `code: "DAILY_QUOTA_OK"`, `data` decimal.

Semántica del valor:
- **Positivo**: cuota diaria que falta ganar (faltante ÷ días restantes, redondeo a 2 decimales).
- **Negativo**: la meta ya se superó; el valor absoluto es el excedente (ej. `-500` = superada por 500). El cliente debe mostrar `abs(valor)` como "meta superada por X".
- Si `dias <= 0`, devuelve el faltante completo.

> ⚠️ La meta **no se persiste** en el backend — hoy el cliente debe guardar `meta` y `dias` localmente y enviarlos en cada consulta. Además, el acumulado suma **todos los ingresos históricos** del usuario, no solo los del mes, así que a partir del segundo mes la cuota sale mal. Ambos puntos son correcciones prioritarias (README-MEJORAS.md): afectan directamente la característica estrella del producto.

### GET `/api/v1/finanzas/resumen-semanal`

Ingresos y egresos por día de la semana actual (lunes a domingo). Alimenta el **analista semanal** (gráfico de barras para ver los días más rentables).

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

> El desglose es ingresos/egresos por día, **sin** desglose por categoría de egreso. Si la UI necesita "cuánto gasté en gasolina esta semana", requiere el campo categoría (pendiente en README-MEJORAS.md).

### GET `/api/v1/finanzas/progreso-metas?meta=3000&dias=15`

Progreso consolidado día/semana/mes. Alimenta el **resumen global** (estado de cuenta mensual proyectado). Mismos query params que `cuota-diaria`.

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

`metaSemanal` se calcula como `metaDiaria × 7`. Si la meta mensual ya se superó, `metaDiaria` llega negativa (misma semántica que `cuota-diaria`).

---

## Tabla de códigos de error

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Falla de validación de campos (trae `details[]`) |
| 400 | `MALFORMED_JSON` | Body JSON inválido |
| 401 | `UNAUTHORIZED` | Sin token, token inválido o credenciales incorrectas en login |
| 403 | `FORBIDDEN` | Sin permisos sobre el recurso |
| 404 | `USUARIO_NO_ENCONTRADO` | Email no registrado (verify-otp / reset-password) |
| 422 | `EMAIL_DUPLICADO` | Registro con correo ya existente |
| 422 | `OTP_INVALIDO` | Código OTP incorrecto |
| 422 | `OTP_EXPIRADO` | Código OTP vencido (>10 min) |
| 500 | `INTERNAL_SERVER_ERROR` | Error no controlado (incluye hoy el caso de token JWT expirado — ver bug arriba) |

## Endpoints faltantes que el cliente va a necesitar

- **No existe** `GET /api/v1/finanzas/transacciones` (listado/historial de transacciones), ni edición ni eliminación. Para el "registro ágil" con historial visible, hay que implementarlo primero en el backend.
- **No existe** endpoint para fijar/consultar la **meta mensual** en el servidor (hoy viaja como query param). El motor dinámico de metas debería persistirla por usuario.
- **No existe** el concepto de **categoría de egreso**, requerido por el MVP de taxistas.

Los tres están priorizados en README-MEJORAS.md.
