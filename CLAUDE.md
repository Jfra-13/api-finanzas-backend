# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

REST API backend for **Finanzas Independientes**: financial tracking for independent workers (initial cohort: taxi drivers). Serves a native Android client. Handles JWT auth, transaction tracking, net-profit-based daily quota calculation, monthly goals, categories, and analytics.

Spring Boot 4.0.5, Java 21, Maven wrapper. H2 (dev/test) / PostgreSQL 16+ on Azure (prod).

## Commands

```bash
./mvnw spring-boot:run          # run dev server on :9090 (H2, ./data/finanzas_db)
./mvnw clean install            # build + full test suite — must be green before any PR
./mvnw test                     # tests only
./mvnw test -Dtest=ClassName                     # single test class
./mvnw test -Dtest=ClassName#methodName          # single test method

SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run   # prod profile (PostgreSQL)
```

On Windows use `mvnw.cmd` (or `.\mvnw`) instead of `./mvnw`.

Swagger UI: `http://localhost:9090/swagger-ui.html` — OpenAPI JSON: `http://localhost:9090/v3/api-docs`. Swagger is the live source of truth for the API contract; endpoint readiness/status codes are catalogued in README-READINESS.md, roadmap in README-PLAN.md.

### Required env vars (dev)

`application*.properties` are gitignored, no secrets in repo — context won't start without these:

- `DB_PASSWORD`, `DB_USERNAME` (default `adminjuan`) — local H2
- `JWT_SECRET` — base64, access token signing
- `MAIL_PASSWORD` — Gmail app password for OTP mail

## Architecture

**Modular monolith organized by feature, not by layer.** Root package `com.finanzas.api`:

- `usuario/` — registration, login, JWT, OTP, refresh token, business type
- `transaccion/` — transaction CRUD, categories, analytics, quota engine
- `meta/` — monthly goal + dynamic working-day schedule
- `security/` — JWT filters, `SecurityConfig`
- `shared/` — cross-cutting: `config/` (OpenAPI, beans), `dto/` (`ApiResponseDTO`, `ErrorResponseDTO` envelope), `exception/` (`GlobalExceptionHandler` + `specific/` per-case exceptions extending `AppException`), `validation/`

Each feature is self-contained with its own `controller`, `service`, `repository`, `dto/`, `model/`. Flow is strictly **Controller → Service → Repository**. DTOs never leave their feature; entities never reach the controller.

Identifiers, class names, and routes are in **Spanish**, consistent with existing code — keep new code Spanish for symbols/routes.

### Response envelope

Every response (success or error) uses the same envelope (`ApiResponseDTO` / `ErrorResponseDTO`): `timestamp`, `status`, `code`, `message`, `data` (success) or `details` (validation errors only), `path`. **The frontend branches on `code`, never on `message`.** Domain errors are modeled as exceptions extending `AppException` (carries `code` + `HttpStatus`) in `shared/exception/specific/`, caught centrally by `GlobalExceptionHandler`.

### Auth: access + refresh token

- `login`/`refresh` return `token` (access JWT, 15 min) and `refreshToken` (30 days, stored server-side as SHA-256 hash only).
- Protected routes: `Authorization: Bearer <token>`.
- Refresh **rotates**: each use invalidates the previous token; client must persist the new `refreshToken` from every response.
- `POST /api/v1/usuarios/refresh` returning 401 `REFRESH_TOKEN_INVALIDO` means the client must go back to login.

### Schema: Flyway owns it

**Flyway is the single source of truth for the schema** across dev/test/prod (`ddl-auto=validate`, Hibernate only validates entity mapping against migrations — never generates DDL). Migrations in `src/main/resources/db/migration/` (`V1__init.sql`, `V2__refresh_tokens.sql`, ...). Enums map to `VARCHAR` so the same migration runs identically on H2 and PostgreSQL.

**Any schema change requires a new `V{n}__description.sql` migration. Never edit an already-applied migration.**

### Financial engine

Daily quota (`cuota-diaria`) is recalculated from **net profit** (ingresos − egresos) of the current month, not gross income. `progreso-metas` indicators (`ingresoDiario/Semanal/Mensual`) stay **gross** by design (so the frontend shows daily effort without being demotivating), while the quota recalculation itself is strictly **net**. Working days (`diasLaborables`) support a dynamic weekly schedule (CSV of weekday ints, 1=Mon..7=Sun; null = every day) stored as CSV `String` internally but exposed as `List<Integer>` in `MetaResponseDTO`.

### Android client integration

Backend listens on `:9090`, all routes under `/api/v1`. Emulator debug builds hit `http://10.0.2.2:9090/`; physical devices need the host's LAN IP; release build points to `https://businesscontrol.azurewebsites.net/`. Backend must be up before the emulator can reach it.
