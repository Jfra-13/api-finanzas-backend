# Plan de implementación por fases

Hoja de ruta para llevar el backend desde su estado actual hasta **listo para fusionar con el frontend Kotlin**. Cubre todas las mejoras **críticas e importantes** de [README-MEJORAS.md](README-MEJORAS.md), más una reorganización estructural inicial y un cierre de contrato para el equipo móvil.

## Principio de orden

Las fases están ordenadas por dependencia técnica, no por gravedad aislada. No se toca lógica de negocio sobre una estructura desordenada, ni se migra a PostgreSQL sobre un esquema que todavía va a cambiar. La secuencia es:

```
Fase 0          Fase 1            Fase 2          Fase 3            Fase 4           Fase 5
Fundaciones  →  Seguridad y    →  Modelo de    →  Lógica         →  Migración    →  Readiness y
estructura      manejo errores    datos           financiera        Azure +         congelado de
                                  (esquema)       y endpoints       Flyway          contrato
```

Cada fase deja el proyecto **compilando y con tests en verde**. No se avanza a la siguiente con la anterior a medias.

### Mapa mejora → fase

| Mejora (README-MEJORAS) | Fase |
|---|---|
| Rotar credenciales (#6) | 0 |
| Limpieza repo / H2 console (#18 parcial) | 0 |
| Bug token 500→401 (#1) | 1 |
| Endurecer OTP (#5) | 1 |
| `tipo` como enum en entidad (#10) | 2 |
| Transacción: descripción, fecha, categoría (#4) | 2 |
| Completar entidad Categoría (#8) | 2 |
| Persistir Meta mensual (#3) | 2 |
| Registro guarda `tipoNegocio` (#11) | 2 |
| Cálculo de cuota: histórico → mensual (#2) | 3 |
| CRUD de transacciones (#9) | 3 |
| Migrar a PostgreSQL/Azure + Flyway (#7) | 4 |
| OpenAPI, tests integración, mail async (recomendadas) | 5 |

---

## Fase 0 — Fundaciones estructurales

**Objetivo**: dejar una base ordenada, escalable y con configuración segura **antes** de tocar comportamiento. Sin esta fase, todo lo demás se construye sobre arena.

### 0.1 Reorganizar de paquetes-por-capa a paquetes-por-feature

**Por qué**: la estructura actual agrupa por tipo técnico (`controller/`, `service/`, `repository/`). Cuando entren los rubros nuevos del roadmap (bodegas, freelancers) y los módulos de predicción/ML, esa organización obliga a tocar carpetas dispersas por cada feature. Un **modular monolith** (paquete por dominio) permite que cada feature crezca y, eventualmente, se extraiga a un microservicio sin reescribir imports.

**Estructura actual**:
```
com/finanzas/api/
├── config/          controller/        exception/
├── model/{dto,entity,enums}            repository/
├── security/        service/           validation/
```

**Estructura objetivo**:
```
com/finanzas/api/
├── shared/                      Código transversal sin dominio propio
│   ├── dto/                     ApiResponseDTO, ErrorResponseDTO, ErrorDetailDTO
│   ├── exception/               AppException, GlobalExceptionHandler
│   └── validation/              @ValueOfEnum, ValueOfEnumValidator
│
├── security/                    Infraestructura de seguridad (transversal)
│   ├── SecurityConfig
│   ├── JwtService, JwtAuthenticationFilter, JwtAuthenticationEntryPoint
│   └── UsuarioPrincipal, CustomUserDetailsService
│
├── usuario/                     Feature: cuenta, autenticación, perfil
│   ├── UsuarioController
│   ├── UsuarioService
│   ├── UsuarioRepository
│   ├── model/                   Usuario (entity)
│   └── dto/                     RegistroDTO, LoginDTO, LoginResponseDTO,
│                                ForgotPasswordDTO, VerifyOtpDTO, ResetPasswordDTO,
│                                NegocioUpdateDTO
│
├── transaccion/                 Feature: ingresos/egresos y categorías
│   ├── TransaccionController
│   ├── TransaccionService
│   ├── TransaccionRepository
│   ├── CategoriaController / CategoriaService / CategoriaRepository   (Fase 2)
│   ├── model/                   Transaccion, Categoria (entities), TipoTransaccion (enum)
│   └── dto/                     TransaccionRegistroDTO, TransaccionUpdateDTO,
│                                TransaccionResponseDTO, CategoriaCreateDTO, CategoriaResponseDTO
│
└── meta/                        Feature: metas y motor de cuota dinámica
    ├── MetaController           (Fase 2/3)
    ├── MetaService
    ├── MetaRepository           (Fase 2)
    ├── model/                   Meta (entity), TipoNegocio (enum, o moverlo a usuario)
    └── dto/                     DiaResumenDTO, ProgresoMetasDTO
```

**Tareas**:
- Crear los paquetes y mover cada clase a su feature.
- Actualizar `package` y `import` de cada archivo movido.
- `messages.properties` y `application.properties` se quedan en `src/main/resources`.
- Verificar que el componente de Spring siga escaneando (`@SpringBootApplication` en la raíz `com.finanzas.api` cubre todos los subpaquetes — no hace falta tocar el scan).

**Verificación**: `mvnw.cmd clean test` pasa sin cambios de comportamiento. Es un refactor puro de ubicación.

### 0.2 Externalizar configuración y rotar credenciales (#6)

> **Seguridad — acción urgente.** Las credenciales reales siguen en el historial de git (el commit `74136e2` borró el archivo, no el historial). Hay que rotarlas, no solo moverlas.

**Tareas**:
1. **Rotar** los tres secretos comprometidos:
   - App password de Gmail (`product.maxima.23@gmail.com`).
   - `jwt.secret` (generar uno nuevo, 256+ bits, base64).
   - Password de la base de datos.
2. Cambiar `application.properties` para leer de variables de entorno:
   ```properties
   spring.mail.password=${MAIL_PASSWORD}
   jwt.secret=${JWT_SECRET}
   spring.datasource.password=${DB_PASSWORD}
   ```
3. Crear `application.properties.example` versionado, **sin secretos** (placeholders), para que cualquiera levante el proyecto.
4. Confirmar que `application.properties` está en `.gitignore`.

**Verificación**: el proyecto levanta con las variables de entorno seteadas; `application.properties.example` no contiene ningún secreto real.

### 0.3 Limpieza de repositorio (#18 parcial)

- Eliminar `api.zip` de la raíz.
- Quitar imports duplicados en `TransaccionService` (líneas ~10–24).
- Completar metadatos vacíos del `pom.xml` (`<name>`, `<description>`).
- Endurecer la consola H2: hoy `web.ignoring().requestMatchers("/h2-console/**")` **desactiva toda la seguridad** en esa ruta. Restringirla a perfil `dev` (se resuelve del todo en Fase 4 con perfiles).

**Entregable de fase**: estructura modular, configuración sin secretos en el repo, credenciales rotadas, repo limpio. Tests en verde.

---

## Fase 1 — Seguridad y manejo de errores

**Objetivo**: cerrar la brecha entre la promesa de "QA robusto / seguridad grado bancario" del resumen ejecutivo y lo que el código hace hoy. Son las dos correcciones que un revisor técnico va a notar primero.

### 1.1 Token JWT expirado/malformado → 401 (#1)

**Problema**: en `JwtAuthenticationFilter`, `extractUsername(jwt)` lanza `ExpiredJwtException` / `MalformedJwtException` dentro del filtro, antes del `GlobalExceptionHandler` → el cliente recibe `500`. El pitch afirma que el servidor devuelve 401/403/422 controlados.

**Tareas**:
- Envolver el parseo del token en try/catch dentro de `doFilterInternal`.
- Ante token inválido/expirado: no autenticar y delegar al `JwtAuthenticationEntryPoint` para responder `401 UNAUTHORIZED` con el envelope de error estándar.
- Reutilizar (o eliminar) las excepciones muertas `TokenExpiradoException` / `TokenInvalidoException` que ya existen sin uso.

**Verificación**: request a ruta protegida con token vencido y con token basura → ambas devuelven `401` con el JSON de error estándar, no `500`.

### 1.2 Endurecer el flujo OTP (#5)

**Tareas**:
- Reemplazar `new Random()` por `SecureRandom` en la generación del OTP.
- Agregar contador de intentos fallidos por usuario; bloquear tras 3–5 intentos (campo `intentos_otp` o tabla de control). Considerar subir el OTP a 6 dígitos.
- Hacer que `verify-otp` consuma o marque el código para que no quede reutilizable indefinidamente.
- Quitar la fuga de enumeración: `verificarOtp` / `resetPassword` devuelven `404 USUARIO_NO_ENCONTRADO` cuando el email no existe → unificar a un error genérico (igual que `forgot-password`).

**Verificación**: el OTP no es predecible; tras N intentos fallidos se bloquea; un email inexistente no se distingue de un OTP inválido en la respuesta.

**Entregable de fase**: autenticación y recuperación con respuestas HTTP correctas y resistentes a fuerza bruta/enumeración.

---

## Fase 2 — Modelo de datos (esquema definitivo)

**Objetivo**: dejar el esquema **estable y completo** antes de migrar a PostgreSQL. Todos los cambios de entidad se concentran aquí, sobre H2 con `ddl-auto=update`, para que la Fase 4 congele un esquema que ya no se mueve.

### 2.1 `tipo` de transacción como enum (#10)
- Cambiar `Transaccion.tipo` de `String` a `TipoTransaccion` con `@Enumerated(EnumType.STRING)`.
- Ajustar `TransaccionService.registrar()` (ya no hace falta `.toUpperCase()`) y las queries JPQL que comparan contra `'INGRESO'`.

### 2.2 Transacción con descripción, fecha real y categoría (#4)
- Agregar a la entidad `Transaccion`: `descripcion` (nullable, máx. 500) y respetar `dto.getFecha()` (default `now()` si viene nula).
- Agregar la relación a `Categoria` (ver 2.3).
- Actualizar `TransaccionService.registrar()` para persistir los tres campos.

### 2.3 Completar la entidad Categoría (#8)
**Por qué**: el MVP de taxistas exige egresos categorizados (gasolina, peajes, alimentación). Los DTOs ya existen; falta el resto.
- Crear entidad `Categoria` (`id`, `nombre`, `tipo` = INGRESO/EGRESO, opcional `usuario_id` para categorías propias) y `CategoriaRepository`.
- Relación `Transaccion → Categoria` (`@ManyToOne`).
- `CategoriaService` + `CategoriaController` con creación y listado.
- Seed de categorías base para taxistas (gasolina, peaje, alimentación, mantenimiento).

### 2.4 Persistir la Meta mensual (#3)
**Por qué**: el motor dinámico de metas no puede vivir en `localStorage` de cada dispositivo.
- Crear entidad `Meta` (`id`, `usuario_id`, `monto_objetivo`, `mes`/`periodo`, `activa`) y `MetaRepository`.
- Definir la regla: una meta activa por usuario por mes.
- (Los endpoints para fijar/consultar la meta se implementan en Fase 3, junto con la lógica que la consume.)

### 2.5 Registro guarda `tipoNegocio` (#11)
- En `UsuarioService.registrarUsuario()`, asignar `nuevoUsuario.setTipoNegocio(dto.getTipoNegocio())`.
- En el MVP llega `TAXI`; el campo ya queda listo para la expansión multi-rubro.

**Verificación**: con `ddl-auto=update` sobre H2, el esquema refleja todas las entidades nuevas; una transacción se guarda con descripción, fecha enviada y categoría; el registro persiste el tipo de negocio.

**Entregable de fase**: modelo de datos completo y congelable. A partir de aquí el esquema no debería cambiar de forma incompatible.

> **Nota (revisión Fase 3):** la Fase 3 agrega **un** campo al esquema — `Meta.diasLaborables` — para soportar la jornada laboral dinámica. Es el único cambio de esquema posterior a Fase 2 y se hace todavía sobre H2 con `ddl-auto=update`, antes de Flyway (Fase 4), por lo que no implica migración. Con esa salvedad, el esquema queda congelado.

---

## Fase 3 — El motor financiero inteligente y endpoints analíticos

**Objetivo**: implementar la lógica financiera basada en **Utilidad Neta**, permitir la personalización de **días laborables** para recalcular la cuota dinámicamente, y exponer la data ya "masticada" que los gráficos del frontend necesitan. Depende del modelo de la Fase 2.

**Decisión de producto (fijada en revisión):**
- La meta se mide en **Utilidad Neta** = ingresos − egresos **del mes en curso**. No en ingresos brutos.
- Los **días laborables viven en `Meta`** (por mes), no en el perfil del usuario: cada meta mensual lleva su propia jornada y puede cambiar mes a mes.
- Almacenamiento de la jornada: columna **CSV** `"1,2,3,4,5"` (1 = lunes … 7 = domingo) en `Meta.diasLaborables`. No bitmask — legible al debuggear y mapeo trivial desde el array del frontend.

**Estrategia de entrega**: la fase se parte en dos PRs encadenados para que cada uno quede compilando y en verde por separado:
- **3a (core financiero)** — tareas 3.1 a 3.4.
- **3b (analítica visual)** — tarea 3.5.

### 3.1 Ampliar el esquema: jornada en `Meta`

**Por qué**: el cálculo de cuota dinámica necesita saber qué días trabaja el usuario. La entidad `Meta` de la Fase 2 no tiene dónde guardarlo.

- Agregar `Meta.diasLaborables` (`String`, CSV de 1–7, ej. `"1,2,3,4,5"`).
- Es el **único** cambio de esquema posterior a Fase 2. Se hace todavía sobre H2 con `ddl-auto=update`, antes de Flyway (Fase 4): cero migración.

### 3.2 Cálculo de cuota dinámica y Utilidad Neta (el core) (#2)

**Problema actual**: `sumarIngresosPorUsuario()` suma todos los ingresos de la vida del usuario y no resta egresos. A partir del segundo mes la cuota diaria sale mal.

**Tareas**:
- **Utilidad Neta del mes en curso**: nueva query que calcule `SUM(ingresos) − SUM(egresos)` acotada estrictamente al mes actual. Si el neto cae por un gasto fuerte, la cuota de los días restantes **sube** sola.
- **Días laborables restantes**: contar, desde hoy hasta fin de mes, sólo los días que matchean el patrón de `Meta.diasLaborables`. Guardar contra división por cero cuando no quedan días laborables.
- **Nueva fórmula**: `cuotaDiaria = (metaMensual − utilidadNeta) / díasLaborablesRestantes`.
- **Borrar la query histórica muerta** (`sumarIngresosPorUsuario`) una vez migrado.
- Dejar `progreso-metas` **consistente en neto** (no mezclar bruto en pantalla con objetivo neto). Revisar de paso `metaSemanal = metaDiaria × 7` (#17) ahora que hay días reales.

### 3.3 Endpoints de Meta y configuración de jornada (#3)

**Por qué**: el servidor es la única fuente de verdad de la configuración financiera; se elimina el envío de parámetros por la URL.

- `POST /api/v1/finanzas/metas` — fijar/actualizar la meta mensual **y** el array de días laborables (ej. `[1,2,3,4,5]`). Regla: una meta activa por usuario por período (`YYYY-MM`).
- `GET /api/v1/finanzas/metas/actual` — meta vigente + días activos.
- Migrar `cuota-diaria` y `progreso-metas` para que tomen meta y jornada **de la BD** (los query params `meta`/`dias` quedan deprecados/opcionales para compatibilidad temporal).

### 3.4 CRUD de transacciones (el historial operativo) (#9)

**Por qué**: un registro financiero real exige auditar, corregir y borrar.

- `GET /api/v1/finanzas/transacciones` — `Pageable` de Spring; filtros por fecha, tipo (`INGRESO`/`EGRESO`) y categoría; orden **fecha descendente** por defecto.
- `PUT /api/v1/finanzas/transacciones/{id}` y `DELETE /api/v1/finanzas/transacciones/{id}`.
- **Seguridad**: validar que el usuario del JWT sea dueño de la transacción. Si no, `403` con `AccesoDenegadoException` (hoy sin uso). Si no existe, `404` con `TransaccionNoEncontradaException`.

**Verificación de 3a**: cuota correcta en un escenario de dos meses simulados y con jornada parcial (ej. lun-mié-vie); el historial pagina y filtra; un usuario no puede tocar transacciones de otro.

### 3.5 Endpoints de análisis visual (entrega 3b)

**Por qué**: el celular no calcula nada — recibe JSONs listos para dibujar en las librerías de gráficos.

- **A. Gráfico circular (egresos por categoría)** — `GET /api/v1/finanzas/resumen-categorias`: agrupa y suma egresos por categoría del mes actual (ej. `{ "Gasolina": 150.0, "Alimentación": 80.0 }`). Las transacciones sin categoría caen en un bucket `"Sin categoría"`.
- **B. Gráfico de líneas (flujo de caja)** — `GET /api/v1/finanzas/tendencia-mensual`: dos arreglos (ingresos y egresos) por mes a lo largo de los últimos N meses.
- **C. Salud financiera** — `GET /api/v1/finanzas/salud-financiera`: alertas con **un set acotado de reglas deterministas** (no abierto). Set inicial:
  1. Gasto del día > cuota de ganancia diaria → alerta.
  2. Utilidad neta del mes ≥ 80 % de la meta → felicitación.
  3. Quedan días laborables pero la cuota requerida supera el mejor día histórico → alerta de meta en riesgo.

**Verificación de 3b**: los tres endpoints devuelven la forma de JSON acordada con [README-FRONTEND.md](README-FRONTEND.md) y sólo leen del mes/usuario correcto.

**Entregable de fase**: lógica financiera correcta basada en neto + jornada, CRUD completo y endpoints analíticos. El contrato de la API queda casi cerrado.

---

## Fase 4 — Migración a PostgreSQL en Azure + Flyway (#7)

**Objetivo**: dejar el backend desplegable en la nube, requisito de la Fase 1 del roadmap (cohorte de 50–100 taxistas). Se hace **después** de estabilizar el esquema para que las migraciones nazcan limpias.

**Tareas**:
- Añadir Flyway al `pom.xml` y crear el baseline `V1__init.sql` a partir del esquema ya estable (Fases 2–3).
- Reemplazar `spring.jpa.hibernate.ddl-auto=update` por `validate` (Flyway pasa a ser la fuente de verdad del esquema).
- Crear perfiles: `application-dev.properties` (H2) y `application-prod.properties` (PostgreSQL Azure), seleccionables por `spring.profiles.active`.
- Configurar la conexión a la instancia PostgreSQL de Azure vía variables de entorno (la carpeta `.azure/` ya existe).
- Deshabilitar la consola H2 en `prod` y restringir su exclusión de seguridad solo a `dev`.
- Probar el arranque contra PostgreSQL real y correr la suite completa.

**Verificación**: el backend levanta con perfil `prod` contra PostgreSQL en Azure; Flyway aplica las migraciones desde cero; la consola H2 no está expuesta en `prod`.

**Entregable de fase**: backend desplegado en la nube, esquema versionado, dev y prod separados.

---

## Fase 5 — Readiness y congelado de contrato para el frontend

**Objetivo**: dejar el backend en condiciones de que el equipo Kotlin integre **sin sorpresas**. Recoge mejoras recomendadas que importan para la fusión.

### 5.1 Documentación OpenAPI/Swagger (#13)
- Agregar `springdoc-openapi`; exponer `/swagger-ui.html` y `/v3/api-docs`.
- El equipo móvil consume el contrato siempre actualizado (incluso puede generar clientes Kotlin desde el OpenAPI).

### 5.2 Tests de integración (#15)
- `@WebMvcTest` / `MockMvc` para controllers: contratos de respuesta y seguridad (401/403).
- Tests de `UsuarioService` (registro, login, OTP) y `TransaccionService` (resumen semanal, progreso, cálculo de cuota mensual).
- Apuntalan la afirmación de QA del pitch con evidencia.

### 5.3 Envío de correo asíncrono (#16)
- Marcar `enviarEmailOtp` con `@Async` (+ `@EnableAsync`) para que un Gmail lento no cuelgue la request de recuperación.

### 5.4 Decisiones de contrato a confirmar con el frontend
- **Refresh token (#14)**: implementarlo o documentar explícitamente que el token dura 24 h y al vencer se re-loguea. Un taxista abre la app muchas veces al día — decidir antes de que la app se construya alrededor del supuesto.
- **CORS (#12)**: **no se toca para el MVP nativo.** Dejar anotado dónde se activa (`SecurityConfig`, `http.cors(...)`) para cuando entre un cliente web/smartwatch/flota.

### 5.5 Checklist de "backend listo para fusión"
- [ ] Todos los endpoints documentados en OpenAPI coinciden con [README-FRONTEND.md](README-FRONTEND.md).
- [ ] Envelope de éxito y de error consistente en todos los endpoints.
- [ ] Códigos `code` estables y documentados (el frontend decide por `code`, no por `message`).
- [ ] 401 real en sesión vencida (no 500).
- [ ] Historial, meta persistida y categorías expuestos y probados.
- [ ] Perfil `prod` desplegado en Azure y accesible para el equipo móvil.
- [ ] Secretos fuera del repo; credenciales rotadas.

**Entregable de fase**: backend documentado, probado y desplegado, con el contrato congelado. Listo para que el frontend Kotlin integre.

---

## Resumen de entregables por fase

| Fase | Entregable | Mejoras cerradas |
|---|---|---|
| 0 | Estructura modular + config segura + repo limpio | #6, #18 |
| 1 | Seguridad y errores correctos | #1, #5 |
| 2 | Esquema de datos completo y estable | #4, #8, #10, #11, (#3 entidad) |
| 3 | Motor de metas correcto + CRUD + endpoints de meta | #2, #3, #9, (#17) |
| 4 | PostgreSQL/Azure + Flyway + perfiles | #7 |
| 5 | OpenAPI + tests + readiness de contrato | #13, #14, #15, #16, #12 (anotado) |

> Las fases 0–4 cubren todo lo **crítico e importante**. La fase 5 agrega lo necesario para una fusión sin fricción con el frontend.
