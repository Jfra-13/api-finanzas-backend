# API Finanzas — Backend

Backend del **Ecosistema Financiero Inteligente para Trabajadores Independientes**: un "analista financiero de bolsillo" que registra ingresos/egresos y calcula cuotas diarias dinámicas para que el usuario alcance su meta mensual.

**Fase 1 (MVP): sector taxistas.** El nicho maneja alto volumen de transacciones diarias (efectivo y billeteras) pero no mide su rentabilidad real frente a costos operativos (combustible, peajes, alimentación). La visión a largo plazo adapta la plataforma a otros rubros (bodegas, freelancers), pero el MVP está enfocado en transporte privado.

El motor diferenciador es el **cálculo dinámico de metas**: el usuario fija una meta mensual y el sistema calcula la cuota diaria; si un día la supera, baja la exigencia de los días siguientes (refuerzo psicológico positivo).

## Stack

| Componente | Versión / Detalle |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.5 (webmvc, data-jpa, security, validation, mail) |
| Base de datos (dev) | H2 en archivo (`./data/finanzas_db`) en modo PostgreSQL |
| Base de datos (target) | PostgreSQL en Microsoft Azure (despliegue en la nube — ver README-MEJORAS.md) |
| Autenticación | Spring Security 6+ stateless con JWT (jjwt 0.11.5, HS256, expiración 24 h) |
| Hashing | BCrypt |
| Precisión monetaria | `BigDecimal` en todos los cálculos (sin flotantes) |
| Build | Maven (wrapper incluido: `mvnw` / `mvnw.cmd`) |
| Lombok | Sí (requiere annotation processing en el IDE) |

**Cliente previsto**: app móvil nativa en **Kotlin Multiplatform**. El backend es agnóstico al cliente (envía DTOs crudos procesados), así que en el futuro pueden conectarse web, smartwatches o sistemas de flota sin tocar la lógica central.

El driver de PostgreSQL ya está en el `pom.xml` para preparar la migración a Azure; la base activa en desarrollo sigue siendo H2 en archivo.

## Cómo ejecutar

```bash
# Windows
mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

La API levanta en **http://localhost:9090**.

### Configuración requerida

`src/main/resources/application.properties` **no está versionado** (contiene credenciales). Para levantar el proyecto se necesita un archivo con esta estructura:

```properties
server.port=9090

# Base de datos H2 local (desarrollo)
spring.datasource.url=jdbc:h2:file:./data/finanzas_db;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=<usuario>
spring.datasource.password=<password>
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect

# Hibernate y consola H2
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console
spring.h2.console.settings.web-allow-others=false

# Correo (Gmail SMTP — usado para enviar OTP de recuperación)
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=<correo>
spring.mail.password=<app-password-de-gmail>
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

# JWT
jwt.secret=<clave-base64-de-al-menos-256-bits>
jwt.expiration=86400000
```

La consola H2 queda disponible en `http://localhost:9090/h2-console` (excluida de Spring Security; deshabilitar fuera de desarrollo — ver README-MEJORAS.md).

## Estructura del proyecto

Arquitectura en capas: controladores (endpoints REST) → servicios (lógica de negocio financiera) → repositorios (acceso a datos).

```
src/main/java/com/finanzas/api/
├── config/          SecurityConfig (filtros, rutas públicas, BCrypt)
├── controller/      UsuarioController, TransaccionController
├── exception/       AppException, GlobalExceptionHandler, excepciones específicas
├── model/
│   ├── dto/         DTOs de request/response (envelope ApiResponseDTO)
│   ├── entity/      Usuario, Transaccion
│   └── enums/       TipoNegocio, TipoTransaccion
├── repository/      UsuarioRepository, TransaccionRepository
├── security/        JwtService, JwtAuthenticationFilter, JwtAuthenticationEntryPoint,
│                    CustomUserDetailsService, UsuarioPrincipal
├── service/         UsuarioService, TransaccionService, MetaService
└── validation/      @ValueOfEnum (valida strings contra enums)
```

## Modelo de datos

**usuarios**: `id`, `email` (único), `password_hash` (BCrypt), `nombre`, `fecha_registro`, `codigo_otp` (4 dígitos), `expiracion_otp`, `tipo_negocio` (BODEGA | TAXI | FREELANCE | PERSONALIZADO).

**transacciones**: `id`, `monto` (`BigDecimal`), `tipo` ("INGRESO" | "EGRESO" como string), `fecha` (timestamp, se asigna al crear), `usuario_id` (FK, lazy).

> El MVP de taxistas requiere **egresos categorizados** (gasolina, peajes, alimentación). Hoy la entidad `Transaccion` no tiene categoría ni descripción persistida — ver README-MEJORAS.md.

## Funcionalidad implementada

- Registro y login de usuarios con JWT stateless.
- Recuperación de contraseña por OTP de 4 dígitos enviado por correo (expira en 10 min).
- Actualización del tipo de negocio del usuario autenticado.
- Registro de transacciones (ingreso/egreso).
- Consultas: ingresos de hoy, cuota diaria restante para una meta, resumen semanal (lunes a domingo), progreso de metas (día/semana/mes).
- Manejo global de errores con formato JSON uniforme y mensajes de validación en español (`messages.properties`).

El detalle completo de endpoints, contratos y códigos de error está en **[README-FRONTEND.md](README-FRONTEND.md)**. Los pendientes y problemas conocidos están en **[README-MEJORAS.md](README-MEJORAS.md)**.

## Tests

```bash
mvnw.cmd test
```

Cobertura actual: `MetaServiceTest` (cálculo de cuota diaria) y `DTOValidationTest` (validaciones de DTOs). No hay tests de integración de controllers ni de seguridad.
