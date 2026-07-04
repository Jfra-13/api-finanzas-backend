# API Finanzas Backend

![Java](https://img.shields.io/badge/Java-21-blue?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen?logo=springboot)
![Database](https://img.shields.io/badge/Database-H2%20%2F%20PostgreSQL-orange?logo=postgresql)
![Status](https://img.shields.io/badge/status-active-success)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

API REST de **Finanzas Independientes**: gestión financiera para trabajadores independientes (cohorte inicial de taxistas). Es el servidor que consume el cliente Android nativo. Maneja autenticación JWT, registro de transacciones, cálculo de cuota diaria por utilidad neta, metas mensuales, categorías y analíticas.

> **Contrato de la API** (lo que consume el frontend): la fuente de verdad viva es **Swagger** (`/swagger-ui.html`). El estado de readiness y los códigos `code` por endpoint están en [README-READINESS.md](README-READINESS.md). El roadmap por fases está en [README-PLAN.md](README-PLAN.md).

## Requisitos

| Herramienta | Versión | Notas |
|---|---|---|
| JDK | **21** | Spring Boot 4 lo exige. |
| Maven | — | No instalar a mano: usa el wrapper (`./mvnw`). |
| PostgreSQL | 16+ | Solo para el perfil `prod`. En dev se usa H2 embebido, sin instalar nada. |

Stack fijado en `pom.xml`:

- Spring Boot `4.0.5`, Spring Web MVC, Data JPA, Security, Validation, Mail.
- Flyway (migraciones), `jjwt 0.11.5` (JWT), springdoc-openapi `3.0.3` (Swagger), Lombok.
- H2 (dev/test), PostgreSQL driver (prod).

## Setup inicial

1. Clonar el repo y abrirlo en tu IDE (importa el proyecto Maven solo).
2. Copiar los ejemplos de configuración y completar los secretos **localmente** (los `application*.properties` reales están en `.gitignore`):
   ```bash
   cp src/main/resources/application.properties.example src/main/resources/application.properties
   cp src/main/resources/application-prod.properties.example src/main/resources/application-prod.properties
   ```
3. Definir las variables de entorno que consume el perfil de desarrollo:

   | Variable | Para qué |
   |---|---|
   | `DB_PASSWORD` | Password de la BD H2 local. |
   | `DB_USERNAME` | Usuario de la BD (default `adminjuan`). |
   | `JWT_SECRET` | Secreto del access token. Base64 válido. |
   | `MAIL_PASSWORD` | App password de Gmail para el envío de OTP. |

   No hay secretos en el repo: si falta una variable, el contexto no arranca.

## Correr la app

### Desarrollo (H2, por defecto)

```bash
# Levantar el servidor en http://localhost:9090
./mvnw spring-boot:run

# Compilar + correr la suite de tests
./mvnw clean install

# Solo tests
./mvnw test
```

> En Windows usar `mvnw.cmd` (o `.\mvnw`) en lugar de `./mvnw`.

La app queda en `http://localhost:9090`. Datos persistidos en `./data/finanzas_db` (archivo H2). Consola H2 en `http://localhost:9090/h2-console` (solo dev).

### Documentación interactiva (Swagger)

- Swagger UI: `http://localhost:9090/swagger-ui.html`
- OpenAPI JSON: `http://localhost:9090/v3/api-docs`

## Perfiles y base de datos

| Perfil | BD | `ddl-auto` | Cuándo |
|---|---|---|---|
| **dev** (default) | H2 archivo (`./data/finanzas_db`, modo PostgreSQL) | `validate` | Desarrollo local |
| **prod** | PostgreSQL en Azure (SSL) | `validate` | Despliegue |

Activar producción:

```bash
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

**Flyway es la única fuente de verdad del esquema.** Hibernate solo *valida* el mapeo de entidades contra lo que crean las migraciones (`ddl-auto=validate`), nunca genera DDL. Las migraciones viven en `src/main/resources/db/migration/` (`V1__init.sql`, `V2__refresh_tokens.sql`). Los enums se mapean a `VARCHAR` para que la misma migración corra igual en H2 y PostgreSQL.

> Todo cambio de esquema = nueva migración `V{n}__descripcion.sql`. Nunca editar una migración ya aplicada.

## Conexión desde el cliente Android

El backend escucha en **`:9090`** y todas las rutas cuelgan de **`/api/v1`**.

| Build del cliente | URL base | Notas |
|---|---|---|
| `debug` (emulador) | `http://10.0.2.2:9090/` | `10.0.2.2` es el alias del emulador hacia el `localhost` del host. |
| `debug` (dispositivo físico) | `http://<IP-LAN-del-host>:9090/` | Ambos en la misma red. |
| `release` | `https://businesscontrol.azurewebsites.net/` | Producción en Azure. |

Para que el emulador alcance tu backend: levantarlo **primero** con `./mvnw spring-boot:run` y dejarlo en `:9090`.

## Estructura del proyecto

**Monolito modular** organizado por feature (no por capa). Raíz: `com.finanzas.api`.

```
src/main/java/com/finanzas/api/
├── ApiApplication.java     ← entrypoint Spring Boot
├── usuario/                ← registro, login, JWT, OTP, refresh token, negocio
│   ├── dto/  model/
├── transaccion/            ← transacciones (CRUD), categorías, analíticas, motor de cuota
│   ├── dto/  model/
├── meta/                   ← meta mensual + jornada laboral dinámica
│   ├── dto/  model/
├── security/               ← filtros JWT, SecurityConfig
└── shared/                 ← transversal
    ├── config/             ← OpenAPI, beans
    ├── dto/                ← ApiResponseDTO, ErrorResponseDTO (envelope)
    ├── exception/          ← GlobalExceptionHandler (@RestControllerAdvice)
    └── validation/

src/main/resources/
├── application.properties           ← perfil dev (gitignored)
├── application-prod.properties      ← perfil prod (gitignored)
├── db/migration/                    ← Flyway: V1, V2...
├── templates/  static/  messages.properties
```

Regla: **Controller → Service → Repository**. Los DTO no escapan de su feature; las entidades nunca llegan al controller.

## Contrato de respuesta (envelope)

Toda respuesta —éxito o error— usa el mismo sobre. **El frontend decide por el campo `code`, nunca por `message`.**

**Éxito** (2xx):
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

**Error** (4xx/5xx): igual, pero con `details` (un item por campo, solo en `VALIDATION_ERROR`) en lugar de `data`.

### Sesión: access + refresh token

- `login` y `refresh` devuelven `token` (access JWT, **15 min**) y `refreshToken` (**30 días**).
- Rutas protegidas: header `Authorization: Bearer <token>`.
- Ante **401** en una ruta protegida → `POST /api/v1/usuarios/refresh` con `{ "refreshToken": "..." }`.
- El refresh **rota**: cada uso invalida el anterior. El cliente debe guardar el nuevo `refreshToken` de cada respuesta.
- Si `/refresh` devuelve **401 `REFRESH_TOKEN_INVALIDO`** → mandar al login.

## Endpoints

### Cuenta / autenticación — `/api/v1/usuarios`
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/registro` | Alta de usuario |
| POST | `/login` | Login → token + refreshToken |
| POST | `/refresh` | Rotar tokens |
| POST | `/forgot-password` | Enviar OTP por correo |
| POST | `/verify-otp` | Verificar OTP |
| POST | `/reset-password` | Cambiar password |
| PUT | `/me/negocio` | Actualizar tipo de negocio |

### Finanzas — `/api/v1/finanzas`
| Método | Ruta | Descripción |
|---|---|---|
| POST/GET/PUT/DELETE | `/transacciones[/{id}]` | CRUD de transacciones (GET paginado) |
| GET | `/cuota-diaria` | Cuota diaria recalculada por utilidad neta |
| GET | `/hoy` | Movimientos del día |
| GET | `/resumen-semanal` | Resumen de la semana |
| GET | `/progreso-metas` | Progreso contra la meta |
| GET | `/resumen-categorias` | Gasto por categoría |
| GET | `/tendencia-mensual` | Series ingresos/egresos por mes |
| GET | `/salud-financiera` | Indicadores de salud financiera |
| POST/GET | `/metas[/actual]` | Fijar / consultar la meta mensual |
| POST/GET | `/categorias` | Crear / listar categorías |

El catálogo completo de códigos `code` por endpoint está en [README-READINESS.md](README-READINESS.md) y en Swagger.

## Convenciones

- **Monolito modular por feature**, no por capa. Cada feature trae su `controller`, `service`, `repository`, `dto`, `model`.
- Identificadores, clases y rutas en **español**, consistente con el código existente.
- **Flyway** es dueño del esquema: cambios = nueva migración, nunca `ddl-auto` generando DDL.
- Envelope `ApiResponseDTO` consistente en todos los endpoints; el cliente decide por `code`.
- Secretos solo por variables de entorno / `application*.properties` (gitignored). Nunca commitearlos.
- **Toda PR debe dejar `./mvnw clean install` en verde** (suite de tests verde).

## Troubleshooting

| Síntoma | Causa probable | Solución |
|---|---|---|
| El contexto no arranca, falta una env var | `JWT_SECRET` / `DB_PASSWORD` / `MAIL_PASSWORD` sin definir | Definir las variables de entorno (ver Setup). |
| `Schema validation failed` al arrancar | Entidad cambió sin migración | Crear `V{n}__...sql` que refleje el cambio. |
| El emulador no conecta al backend | Backend caído o URL incorrecta | Levantar en `:9090`; el cliente debug usa `http://10.0.2.2:9090/`. |
| Dispositivo físico no conecta | `10.0.2.2` no resuelve fuera del emulador | Apuntar el cliente a la IP LAN del host, misma red. |
| 500 en vez de 401 con token vencido | — | Resuelto: el filtro JWT devuelve **401** ante token inválido/expirado. |
| Consola H2 inaccesible en prod | Está deshabilitada a propósito | La consola H2 solo existe en el perfil dev. |
