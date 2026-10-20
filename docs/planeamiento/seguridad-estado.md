# Endurecimiento de seguridad — estado al 2026-10-20

Resumen de la auditoría de seguridad de endpoints y de los fixes aplicados. Las reglas que
cada cambio debe cumplir están en [`CLAUDE.md`](../../CLAUDE.md) ("Security rules").
Plan de fases funcionales: [`plan.md`](plan.md).

Metodología de cada fix: test primero (rojo), fix, suite completa en verde, un fix por commit.

## Estado actual

| Ítem | Estado |
|------|--------|
| Suite de tests | 145/145 en verde |
| PR #1 `fix/otp-brute-force` | ✅ Mergeado en `main` (`1a02fb7`) |
| Rama `fix/registro-enumeracion-email` | 3 commits, pusheada, PR pendiente de abrir |
| Fases funcionales 0–4 | ✅ |
| Fase 5 (señales de salud) | En progreso: 5 de 8 códigos, faltan tests (ver `plan.md`) |
| Fase 6 (recomendaciones) | Sin empezar |

## PR #1 — `fix/otp-brute-force` (mergeado)

- **Fuerza bruta de OTP:** el contador de intentos se perdía por el rollback de la transacción.
  Ahora se reserva el intento de forma atómica (`UPDATE ... WHERE intentosOtp < max`) antes de
  comparar el código, y pedir otro OTP mientras el vigente sigue válido no reinicia el contador.
- **OTP de 6 dígitos** (antes 4), migración `V6__otp_seis_digitos.sql`.
- **`OTP_BLOQUEADO`:** el mensaje indica esperar antes de pedir otro código.
- **`reset-password`** revoca todos los refresh tokens del usuario.
- **Bloqueo de login:** 5 fallos en 15 minutos por email dan `429 LOGIN_BLOQUEADO`
  (`LoginIntentos`, en memoria). El intento se reserva de forma atómica antes de validar la
  contraseña, así una ráfaga en paralelo no supera el límite.
- **Recursos ajenos** responden `404 *_NO_ENCONTRADO`, igual que uno inexistente (no `403`).
- **Errores 401** pasan por `GlobalExceptionHandler` con mensajes fijos.
- **Errores 4xx del framework** (ruta inexistente, método o content type incorrecto) ya no
  se devuelven como `500`.
- **Parámetros acotados** (`meses`, `ventana`, `dias`): fuera de rango dan `400 PARAMETRO_INVALIDO`.
- **Swagger/OpenAPI** deshabilitado en el perfil `prod`.
- **`CLAUDE.md`** versionado con las reglas de seguridad.

## Rama `fix/registro-enumeracion-email`

| Commit | Cambio |
|--------|--------|
| `2ba56b4` | El registro responde siempre `200 USER_REGISTERED`. Con un email existente no modifica la cuenta, avisa al dueño por correo (`@Async`) y ejecuta bcrypt en ambos caminos para no filtrar el email por tiempo de respuesta. Se elimina `EMAIL_DUPLICADO`. |
| `b06e38e` | Límite por IP: 20 solicitudes por minuto sumando `registro`, `login`, `forgot-password`, `verify-otp` y `reset-password`. Excedido: `429 RATE_LIMIT_EXCEDIDO`. Implementado como `HandlerInterceptor` (`RateLimitPorIp`) para que el error pase por `GlobalExceptionHandler`. Límite configurable con `rate-limit.max-por-minuto`. |
| `325da54` | Dos registros en paralelo con el mismo email nuevo daban `500` (violación del `UNIQUE`). Ahora el perdedor recibe la misma respuesta que un email existente. |

### Cambios de contrato para el front

- Se elimina el código `EMAIL_DUPLICADO`; el registro siempre responde éxito y cambia el texto.
- Nuevo error `429 RATE_LIMIT_EXCEDIDO` en los endpoints públicos de autenticación.
- El OTP de recuperación pasa a 6 dígitos (PR #1).

Detalle en [`integracion-frontend.md`](../integracion-frontend.md).

## Decisiones y riesgos aceptados

- **Bloqueo de login por email:** cualquiera puede bloquear el login de una cuenta ajena durante
  15 minutos fallando a propósito. Aceptado; el límite por IP frena la velocidad del ataque.
- **CGNAT de operadoras móviles:** varios usuarios legítimos pueden compartir IP. Con 20
  solicitudes por minuto hay margen para el volumen actual.
- **Contadores en memoria** (`LoginIntentos`, `RateLimitPorIp`): correctos solo con una
  instancia del backend. Ver pendiente "Store compartido".

## Pendientes

### Store compartido para los contadores (a evaluar)

Los dos contadores viven en la memoria de cada instancia. Si Azure App Service corre más de una
instancia (escalado manual o autoescalado), cada una cuenta por separado y los límites reales se
multiplican por la cantidad de instancias.

- **Cómo verificarlo:** portal de Azure → App Service → *Scale out (App Service plan)*:
  cantidad de instancias y si el escalado es manual o automático.
- **Con 1 instancia fija:** no hace falta nada.
- **Con más de una:** mover los contadores a PostgreSQL (tabla de contadores con un `UPDATE`
  atómico), sin agregar Redis como dependencia nueva.

### Deploy en Azure

- Validar que `server.forward-headers-strategy=native` entregue la IP real del cliente en
  `getRemoteAddr()` (ni la del proxy ni `ip:puerto`). Si no, el límite por IP afecta a todos
  los usuarios a la vez. Ajustar `server.tomcat.remoteip.internal-proxies` si hace falta.
- Confirmar que springdoc está apagado en Azure (`/v3/api-docs` no debe responder).
- Rotar credenciales y completar el deploy (ítems 8 y 9 del checklist de `plan.md`).

### Front

- Enviar el mensaje con los cambios de contrato listados arriba.

### Rama actual

- Abrir el PR de `fix/registro-enumeracion-email` y mergearlo.

### Funcional

- Fase 5: tests de los 4 códigos sin cobertura y `severidad`; umbrales de los 3 códigos pendientes.
