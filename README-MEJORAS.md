# Mejoras pendientes

Lista priorizada de correcciones y mejoras del backend, **reordenada según el resumen ejecutivo del proyecto**: MVP enfocado en taxistas, cliente móvil nativo (Kotlin), despliegue en Azure PostgreSQL y promesa de seguridad "grado bancario". La prioridad ya no es "que arranque un frontend web", sino **proteger las características core del MVP y cerrar la brecha entre lo que el pitch promete y lo que el código hace hoy**.

Cada punto incluye referencia a archivo para ubicarlo rápido.

---

## 🔴 Críticas — rompen una promesa del MVP o contradicen el pitch

### 1. Token JWT expirado/malformado devuelve 500 en vez de 401
**Contradice directamente tu argumento de venta de QA.** El resumen ejecutivo afirma que el `GlobalExceptionHandler` "devuelve respuestas HTTP controladas (400, 401, 403, 422) en lugar de exponer la infraestructura interna". Pero en `JwtAuthenticationFilter.java:43`, `jwtService.extractUsername(jwt)` lanza `ExpiredJwtException` / `MalformedJwtException` **dentro del filtro**, antes de llegar al handler global → el cliente recibe `500`. La app no puede distinguir "sesión vencida" de "error del servidor". Envolver el parseo en try/catch y delegar al `JwtAuthenticationEntryPoint` para responder `401`. Sin esto, el manejo de sesión vencida en la app móvil es frágil y la afirmación de QA es falsa.

### 2. El cálculo de cuota usa ingresos históricos totales
**Rompe la característica estrella: el Motor Dinámico de Metas.** `TransaccionRepository.sumarIngresosPorUsuario()` suma **todos los ingresos de la vida del usuario** y no resta egresos. A partir del segundo mes, la "utilidad actual" contra la meta mensual queda inflada y la cuota diaria sale mal (o negativa de entrada). El diferenciador que vende el proyecto —"si superás la meta, baja la exigencia de los días siguientes"— no funciona correctamente en el tiempo. Filtrar por mes actual y definir si la meta se mide en ingresos brutos o utilidad neta (ingresos − egresos del taxista).

### 3. La meta mensual no se persiste en el servidor
**El motor de metas no tiene dónde vivir.** `cuota-diaria` y `progreso-metas` exigen que el cliente envíe `meta` y `dias` en cada request. La meta debería ser un dato del usuario en el backend (entidad `Meta` o campos en `Usuario`), con endpoints para definirla y consultarla. Hoy cada dispositivo guarda su propia meta y los datos pueden divergir; además contradice el principio "procesamiento pesado del lado del servidor" del resumen.

### 4. Las transacciones descartan `descripcion`, `fecha` y no tienen categoría
**El MVP de taxistas necesita egresos categorizados (gasolina, peajes, alimentación).** `TransaccionRegistroDTO` acepta `descripcion` (máx. 500) y `fecha`, pero:
- La entidad `Transaccion` no tiene campo `descripcion` — se pierde silenciosamente.
- `TransaccionService.registrar()` nunca usa `dto.getFecha()` — la fecha siempre es `now()` del servidor.
- **No existe el campo `categoria`**, que el resumen marca como funcionalidad core ("egresos categorizados").

Agregar a la entidad: `descripcion`, respeto de la `fecha` enviada (default `now()`) y `categoria` (ver punto 8). Sin esto, el "registro ágil categorizado" que vende el MVP no es posible.

### 5. Endurecer el flujo OTP (la seguridad "grado bancario" no se sostiene hoy)
En `UsuarioService.java`:
- `new Random()` para generar el OTP (línea 77) → usar `SecureRandom`.
- Solo 10.000 combinaciones y **sin límite de intentos** ni rate limiting → fuerza bruta viable. Agregar contador de intentos (bloquear tras 3–5) y/o subir a 6 dígitos.
- `verify-otp` no consume el código; queda válido hasta expirar o resetear.
- `verificarOtp`/`resetPassword` lanzan `404 USUARIO_NO_ENCONTRADO`, lo que permite **enumerar correos registrados** (el `forgot-password` sí lo evita). Responder con error genérico.

El resumen promete "seguridad grado bancario": estos cuatro puntos son la diferencia entre la afirmación y la realidad.

### 6. Rotar credenciales expuestas en el historial de git
El commit `74136e2` quitó `application.properties` del control de versiones, pero el archivo con credenciales reales **sigue en el historial** (y el archivo local actual las contiene). Acciones:
- Rotar el app password de Gmail (`product.maxima.23@gmail.com`).
- Generar un nuevo `jwt.secret` (256+ bits, base64).
- Cambiar la contraseña de la base de datos.
- Mover todo a variables de entorno (`${MAIL_PASSWORD}`, `${JWT_SECRET}`) y dejar un `application.properties.example` versionado sin secretos.

Independiente del frontend, pero urgente: una fintech no puede tener secretos en el historial.

---

## 🟠 Importantes — habilitan el roadmap actual (nube, historial, expansión)

### 7. Migrar de H2 archivo a PostgreSQL en Azure
**Es parte de tu Fase 1 ("despliegue del backend en la nube").** H2 en archivo no sirve para producción (sin concurrencia real, datos en disco local). El driver de PostgreSQL ya está en el `pom.xml` y existe la carpeta `.azure/`. Junto con la migración: reemplazar `spring.jpa.hibernate.ddl-auto=update` por migraciones versionadas (Flyway o Liquibase) y crear perfiles `application-dev` / `application-prod`. Sin esto no hay despliegue ni cohorte de prueba de 50–100 taxistas.

### 8. Completar la funcionalidad de categorías
**No es código muerto — es funcionalidad del MVP.** Existen `CategoriaCreateDTO` y `CategoriaResponseDTO` pero no hay entidad, repositorio, servicio ni controller. El resumen exige egresos categorizados (gasolina, peajes, alimentación). Crear la entidad `Categoria` (o un enum/catálogo de categorías de egreso para taxistas), relacionarla con `Transaccion`, y exponer el desglose por categoría en el resumen semanal/mensual.

### 9. Implementar el CRUD de transacciones
**El "registro ágil con historial" lo necesita.** Solo existe el `POST`. No hay listado, edición ni eliminación, aunque ya existen los DTOs `TransaccionResponseDTO` y `TransaccionUpdateDTO` y la excepción `TransaccionNoEncontradaException` (todos sin uso). Implementar:
- `GET /api/v1/finanzas/transacciones` con paginación (`Pageable`) y filtros por fecha/tipo/categoría.
- `PUT /api/v1/finanzas/transacciones/{id}` y `DELETE /api/v1/finanzas/transacciones/{id}`, verificando que la transacción pertenezca al usuario autenticado.

### 10. `tipo` de transacción como String en la entidad
`Transaccion.tipo` es `String` y el servicio hace `dto.getTipo().toUpperCase()`. Ya existe el enum `TipoTransaccion` — usarlo en la entidad con `@Enumerated(EnumType.STRING)` para garantizar integridad a nivel de modelo (las queries JPQL comparan contra el literal `'INGRESO'`). Importa más al migrar a PostgreSQL, donde la integridad de datos es menos perdonadora que en H2.

### 11. El registro ignora `tipoNegocio`
`UsuarioRegistroDTO` acepta y valida `tipoNegocio`, pero `UsuarioService.registrarUsuario()` (líneas 43–54) nunca lo asigna a la entidad. El usuario queda con `tipo_negocio = null`. Agregar `nuevoUsuario.setTipoNegocio(dto.getTipoNegocio())`. **Prioridad media en el MVP** (todos los usuarios de Fase 1 son taxistas), pero es prerequisito de la expansión multi-rubro del roadmap.

---

## 🟡 Recomendadas — calidad, resiliencia y futuro

### 12. Configurar CORS (solo cuando entre un cliente web/flota)
**No es bloqueante para el MVP móvil nativo** (la política de mismo origen es del navegador, no de un HTTP client de Android/iOS). Pero la visión a largo plazo del resumen contempla "aplicaciones web, smartwatches o sistemas de flotas". Cuando entre el primer cliente basado en navegador, agregar un `CorsConfigurationSource` con los orígenes permitidos y registrarlo (`http.cors(...)`) en `SecurityConfig.java`. Hasta entonces, no tocar.

### 13. Documentación OpenAPI/Swagger
Agregar `springdoc-openapi` para exponer `/swagger-ui.html`. El equipo de la app Kotlin tendría el contrato siempre actualizado sin depender de documentación manual.

### 14. Refresh tokens
El token dura 24 h y al vencer obliga a re-login — molesto para un taxista que abre la app varias veces al día. Implementar refresh token (o al menos documentar la decisión de no tenerlo).

### 15. Tests de integración
Solo existen `MetaServiceTest` y `DTOValidationTest`. Para sostener la afirmación de QA del resumen, faltan tests de `UsuarioService` (registro, login, OTP), `TransaccionService` (resumen semanal, progreso, cálculo de cuota) y tests de controller con `@WebMvcTest`/`MockMvc` que cubran seguridad (401/403) y contratos de respuesta.

### 16. Envío de correo asíncrono
`enviarEmailOtp` se ejecuta de forma síncrona dentro de una transacción (`UsuarioService.generarOtpRecuperacion`). Si Gmail tarda o falla, la request se cuelga o revienta. Marcar el envío con `@Async` (y habilitar `@EnableAsync`) o encolar.

### 17. Revisar `metaSemanal = metaDiaria × 7`
En `TransaccionService.obtenerProgresoMetas()` la meta semanal se deriva multiplicando la cuota diaria por 7, incluso cuando quedan menos de 7 días de mes o la semana cruza dos meses. Validar la regla de negocio con el producto.

### 18. Limpieza
- Eliminar `api.zip` de la raíz del repo.
- Excepciones sin uso: `CredencialesInvalidasException`, `TokenInvalidoException`, `TokenExpiradoException`, `AccesoDenegadoException` — usarlas (p. ej. en el punto 1) o eliminarlas.
- Imports duplicados en `TransaccionService.java` (líneas 10–24).
- Deshabilitar la consola H2 fuera de desarrollo (`web.ignoring()` además desactiva TODA la seguridad en esa ruta — riesgo al desplegar en Azure).
- Completar metadatos vacíos del `pom.xml` (`<name/>`, `<description/>`).

---

## Orden sugerido de ataque

1. **Bug del 401 + flujo OTP + rotar credenciales** (puntos 1, 5, 6) — sostienen las afirmaciones de seguridad/QA del pitch.
2. **Motor de metas correcto** (puntos 2 y 3) — sin esto, el diferenciador del producto no funciona.
3. **Transacciones completas: categoría, descripción, fecha + CRUD** (puntos 4, 8, 9) — habilitan el registro ágil categorizado y el historial.
4. **Migración a Azure PostgreSQL con Flyway** (punto 7) — requisito para desplegar y validar con la cohorte de taxistas.
5. El resto según prioridad de producto. **CORS (punto 12) recién cuando entre un cliente web.**
