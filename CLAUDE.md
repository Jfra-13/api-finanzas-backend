# CLAUDE.md

## Security rules

These rules apply to every change in this repo. A PR that breaks one is not done.

### Access
- Every endpoint is authenticated by default (`anyRequest().authenticated()` in `SecurityConfig`).
  A new public route must be added explicitly to the `permitAll()` list with a comment explaining why.
- Swagger/OpenAPI (`/v3/api-docs/**`, `/swagger-ui/**`) must not be public in the `prod` profile.
- H2 console exists only under the `dev` profile. Never expose it elsewhere.

### JWT
- Tokens are signed (HS256) and parsed with signature + expiration verification (`parseClaimsJws`).
  Never decode a token without verifying it.
- `jwt.secret` comes only from the `JWT_SECRET` environment variable. No default value, no secret
  in code, tests, or committed properties files. Minimum 256-bit base64 key.
- Access tokens stay short-lived (`jwt.expiration`); sessions renew through rotated, server-side
  refresh tokens (stored hashed).
- Soft-deleted accounts must be rejected by the JWT filter even with a valid token.

### Ownership (a user NEVER reaches another user's data)
- The user id always comes from `@AuthenticationPrincipal UsuarioPrincipal`, never from the
  request body, path, or query params.
- Every repository query on user data is scoped by `usuarioId` (e.g. `findByUsuarioId...`,
  `findVisible(id, usuarioId)`). A bare `findById` on user data must be followed by an
  explicit owner check before any read or write.
- Any foreign id received in a request (e.g. `categoriaId`) is resolved through a
  user-scoped lookup before use.
- Another user's resource answers `404 <RESOURCE>_NO_ENCONTRADO`, exactly like a missing one
  (never `403`, which confirms the id exists). `403` is only for visible-but-not-editable
  resources, such as base categories.

### Input validation
- Every `@RequestBody` is annotated with `@Valid` and its DTO carries Bean Validation constraints.
- Query/path params that drive work (window sizes, ranges, amounts) are bounded; out-of-range
  values return `400`, not a slow query or a `500`.

### Errors
- Errors go through `GlobalExceptionHandler` and the `ErrorResponseDTO` envelope.
- Never return stack traces, exception class names, SQL, or raw framework messages to the client.
  Log details server-side; return a stable `code` and a generic `message`.
- Client errors (bad params, unknown route, wrong method) return `4xx`, never `500`.
- Auth flows must not reveal whether an email exists.

### CORS
- CORS stays disabled while the only client is the native app. Enabling it requires an explicit
  allow-list of origins (never `*` with credentials).

### Tests (mandatory)
- Every new protected endpoint ships with integration tests for:
  - `401` without token and with an invalid/expired token.
  - `403`/`404` when accessing another user's resource (owner check).
- Every new public endpoint ships with a test proving it does not leak data across users.
