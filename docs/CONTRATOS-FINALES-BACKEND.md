# Contratos finales del backend — cierre del ciclo de auditoría

Cierra el intercambio `ENDPOINTS-AUDIT.md` → `RESPUESTA-BACKEND-AUDIT.md` →
`RESPUESTA-FRONT-A-BACKEND.md`. Consolida los contratos que quedaron pendientes en esos
documentos, ya **implementados y con suite verde (103/103)**. Ante cualquier diferencia,
Swagger (`/v3/api-docs`) es la fuente de verdad.

## Estado y orden de merge

Todo P0 y P1 está implementado. Las ramas se apilan en este orden (cada una sobre la anterior):

1. `feature/analytics-filtro-fechas` — P0 completo + P1.1/P1.2/P1.6 + P2 adelantado
2. `feat/transacciones-filtro-sin-categoria` — P1.4
3. `feat/metas-historial` — P1.5
4. `feat/usuarios-eliminar-cuenta` — P1.3

El front puede empezar a consumir cada contrato en cuanto su rama llegue a main y se despliegue.

---

## P1.4 — Filtro "sin categoría" (`sinCategoria=true`)

`GET /api/v1/finanzas/transacciones` acepta el param booleano opcional `sinCategoria`:

- `sinCategoria=true` → solo transacciones **sin** categoría. Compone con `tipo`, `desde`, `hasta` y paginado.
- `sinCategoria=true` + `categoriaId` presente → `400 PARAMETRO_INVALIDO` (combinación contradictoria; no se resuelve en silencio).
- `sinCategoria=false` u omitido → sin filtro (**no** significa "solo categorizadas").

Verificado lo que pidieron: el listado sin filtros sigue devolviendo las transacciones
huérfanas (el acceso `t.categoria.id` se resuelve por columna FK, sin inner join).

## P1.5 — Historial de metas

`GET /api/v1/finanzas/metas/historial?meses=N` → `200 GOALS_HISTORY_OK`:

```json
[
  { "periodo": "2026-06", "metaMensual": 3000.0, "utilidadReal": 2450.0, "cumplida": false }
]
```

- Un item por mes **con meta registrada** dentro de la ventana, ascendente por período.
- `meses` = ventana hacia atrás incluyendo el mes en curso; default 6, mínimo 1 (misma convención que `ventana` de analytics).
- `utilidadReal` es utilidad **neta** (ingresos − egresos), calculada on-the-fly como se acordó. En el mes en curso compara lo acumulado hasta hoy.
- `cumplida` = `utilidadReal >= metaMensual`.
- Sin metas en la ventana → **lista vacía**, no 404.

## P1.3 — Eliminación de cuenta (contrato final, consultas del front resueltas)

**Decisión de producto**: soft-delete con **30 días de gracia**; purga física posterior por job.

### `POST /api/v1/usuarios/me/eliminar` — Bearer

Se eligió `POST` (no `DELETE /me` con body) por pedido del front: el body en DELETE requiere
un hack no estándar en Retrofit y algunos proxies lo descartan.

- **Request**: `{ "password": "..." }` — re-autenticación: el JWT prueba la sesión, la password prueba a la persona.
- **Éxito**: `200 ACCOUNT_DELETED`. Efectos inmediatos:
  - La cuenta queda marcada como eliminada (inicia la gracia de 30 días; los datos no se tocan).
  - Se revocan **todos** los refresh tokens del usuario.
  - El access token vigente **deja de ser aceptado de inmediato** en rutas protegidas (no existe ventana de 15 minutos: el filtro JWT rechaza cuentas con baja pendiente).
- **Errores**: password incorrecta → `401 CREDENCIALES_INVALIDAS`; password ausente → `400 VALIDATION_ERROR`.

### Reactivación (dentro de los 30 días)

- Iniciar sesión reactiva la cuenta con todos sus datos. No hay endpoint extra.
- La respuesta de login incluye el campo nuevo **`cuentaReactivada: boolean`** — `true` solo
  cuando ese login reactivó una cuenta con baja pendiente (login normal y refresh: `false`).
  Con eso el front muestra el aviso de reactivación y puede ofrecer rehacer la baja si fue accidental.

### Después de la gracia

- Login → `404 USUARIO_NO_ENCONTRADO` (la cuenta se comporta como inexistente).
- Un job diario (03:00, hora del servidor) purga físicamente usuario y todos sus datos:
  sesiones, presupuestos, transacciones, metas y categorías propias (las base del sistema quedan).

## Codes nuevos al catálogo (delta sobre la sección 5 de `RESPUESTA-BACKEND-AUDIT.md`)

| Code | Tipo | Dónde |
|---|---|---|
| `GOALS_HISTORY_OK` | éxito | `GET /metas/historial` |
| `ACCOUNT_DELETED` | éxito | `POST /usuarios/me/eliminar` |
| `CREDENCIALES_INVALIDAS` | error 401 | password incorrecta en la baja (mostrar inline, no expulsar sesión) |
| `PRESUPUESTO_NO_ENCONTRADO` | error 404 | `DELETE /presupuestos/{id}` |

Campo nuevo (aditivo, no rompe): `cuentaReactivada` en la respuesta de login.

Recordatorio P2 (contratos ya enviados, endpoints vivos en la rama 1): `compararCon` acepta
`PERIODO_ANTERIOR` (default) y `MISMO_PERIODO_ANIO_ANTERIOR` — con **ANIO**, sin ñ.

## Qué queda

**Del backend: nada.** Solo mergear el stack y desplegar.

**Del front**: consumir P0/P1, catalogar los codes de arriba, y las decisiones de producto ya
anotadas en su respuesta (pantalla de salud-financiera, UI de presupuestos, dónde vive el
historial de metas).

**Bucket P2 de producto (parkeado hasta definición)**: recomendaciones, reset de estadísticas,
`POST /soporte/feedback` (próximo candidato), suscripción / push / referidos.
