# Respuesta del front a `RESPUESTA-BACKEND-AUDIT.md`

Cerramos el ciclo. Confirmamos lo implementado, respondemos las 4 decisiones que pidieron
y dejamos anotado lo que queda pendiente de nuestro lado (producto).

> **Contexto**: verificamos contra el código de la app (rama `feat/network-core-auth-session`).
> Nada bloquea la integración: podemos empezar a consumir todo P0 en cuanto mergeen.

---

## 1. Correcciones de contrato (A.13, A.15, A.22) — recibidas

- **A.13 / A.15 (`cuota-diaria`, `progreso-metas`)**: perfecto que ahora emitan `404 META_NO_ENCONTRADA`
  real. **Verificado en código**: `DashboardViewModel` ya ramifica por
  `ApiCode.META_NO_ENCONTRADA` en ambos casos y cae en estado vacío ("Sin meta activa" /
  `progresoMetas = null`). No era código muerto: está cableado y el `404` dispara bien. Sin cambios de nuestro lado.
- **A.22 (`salud-financiera` con `severidad` + `categoriaId`)**: recibido. El shape ampliado es
  retrocompatible y hoy no lo consume ninguna pantalla, así que no rompe nada. Cuando lo re-adoptemos,
  ampliamos el DTO (`severidad`, `categoriaId` nullable). La decisión de **dónde** se muestra es
  nuestra y está pendiente (ver sección 4).

Contratos formalizados en Swagger (`resumen-semanal` 7 items lunes→domingo, signo de `cuota-diaria`,
`fecha` ISO-8601 datetime): confirmados, coinciden con lo que la app asume.

## 2. Las 4 decisiones que pidieron

### 2.1 P1.4 — marcador "sin categoría": vamos con `sinCategoria=true`

Decidido: param booleano ortogonal **`sinCategoria=true`**, no `categoriaId=0`.

`categoriaId=0` funciona solo porque los IDs arrancan en 1: contrato implícito frágil que sobrecarga el
param. Y no ahorra nada: con `t.categoria.id = :categoriaId`, un `0` devuelve vacío igual — "sin
categoría" **necesita** rama `t.categoria IS NULL` sí o sí. O sea, ambas opciones requieren
special-case en la query; a igual costo gana el param limpio y self-documenting.

Contrato acordado para este filtro:

1. **`sinCategoria=true` + `categoriaId` presente** → `400 PARAMETRO_INVALIDO` (combinación
   contradictoria; no resolver silenciosamente uno de los dos). El code ya está en el catálogo (sección 5).
2. **`sinCategoria=false`** → igual que ausente (sin filtro), **no** "solo categorizadas". Documentar en Swagger.
3. **Ojo con el join**: pasar a `t.categoria IS NULL` cambia el join implícito. En algunos providers
   JPA, `t.categoria.id = :categoriaId` genera inner join que ya excluye las huérfanas del listado
   general. Verificar con test que el **listado sin filtros** siga trayendo las transacciones sin categoría.

### 2.2 Semana en `tendencia` rotulada con el lunes (`yyyy-MM-dd`) — conforme

Nos sirve tal cual. Formateamos el label en el cliente; la fecha del lunes es ordenable y sin
ambigüedad. Sin cambios.

### 2.3 `GET /me` usa `tipoNegocio` (no `negocio`) — de acuerdo

Decisión correcta: un nombre por concepto, consistente con login y `PUT /me/negocio`. La auditoría
proponía `negocio` pero prima la consistencia. Conforme.

### 2.4 `404 META_NO_ENCONTRADA` real — verificado

Ya respondido en 1: el manejo "Sin meta activa" dispara correctamente en Dashboard para
`cuota-diaria` y `progreso-metas`.

## 3. P1 pendientes — nuestra postura

- **P1.5 `metas/historial`**: de acuerdo con calcular la utilidad **on-the-fly**. Es lo más simple y
  correcto al volumen actual; materializar al cierre de mes solo si se vuelve un hotspot. No lo
  adelantamos.
- **P1.3 `DELETE /me`**: de acuerdo con password de confirmación. Soft-delete vs. físico y período de
  gracia los definimos nosotros (producto) antes de que lo implementen.

## 4. Pendientes de nuestro lado — no los bloquean a ustedes

- **P1.4 consumo `sinCategoria=true`** (front): rama aparte (`feat/analytics-sin-categoria-server-filter`)
  una vez que ustedes mergeen el endpoint. Cambia `FinanzasApi.kt` (`@Query("sinCategoria")`) y saca el
  filtrado en cliente del drill-down de Analytics. No entra en la rama actual (fuera de scope).
- **Dónde mostrar `salud-financiera`**: candidato natural, card en Dashboard o Analytics. Les avisamos
  cuando esté decidido; el pipeline de ambos lados ya está listo.
- **`DELETE /me`**: definir alcance (soft/físico + gracia).
- **reset-estadísticas**: definir exactamente qué borra antes de que lo toquen.
- **Recomendaciones, suscripción, push, referidos, feedback**: productos, no endpoints sueltos. De
  acuerdo con que `POST /soporte/feedback` sea el próximo candidato.

## 5. Codes nuevos — los tomamos al catálogo del cliente

Agregamos al `ApiCode` de la app estos codes (hoy un code no catalogado cae en `UNKNOWN`):

Éxito: `DAILY_SUMMARY_OK`, `TREND_OK`, `WEEKDAY_INCOME_OK`, `PROFILE_OK`, `PROFILE_UPDATED`,
`LOGGED_OUT`, `CATEGORY_UPDATED`, `CATEGORY_DELETED`.
Error: `PARAMETRO_INVALIDO`.
Salud financiera (`data[].code`): `EGRESOS_SUPERAN_INGRESOS`, `TASA_AHORRO_BAJA`, `TASA_AHORRO_SANA`,
`PRESUPUESTO_EXCEDIDO`, `PROYECCION_BAJO_META`.

---

## 6. Cierre

Todo cerrado. P1.4 va con `sinCategoria=true` (contrato en 2.1). Arrancamos a consumir P0
(`resumen-diario`, `tendencia`, `ingresos-por-dia-semana`, `PUT`/`DELETE categorias`, `GET /me`) en
cuanto mergeen la rama.
