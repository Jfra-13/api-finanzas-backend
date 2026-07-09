#!/usr/bin/env bash
# Seeds a demo user with a monthly goal and a spread of transactions by hitting
# the real API (so the quota engine, envelope and validations all run).
# Local dev only. Requires the server up on :9090 and `jq` on PATH.
#
# Reset before re-seeding (local H2): stop the server, delete ./data, restart.
# That gives Flyway a clean schema and avoids duplicate demo transactions.
set -euo pipefail

BASE="${BASE:-http://localhost:9090/api/v1}"
EMAIL="${EMAIL:-demo@finanzas.dev}"
PASSWORD="${PASSWORD:-Demo1234}"
NOMBRE="${NOMBRE:-Demo Taxista}"
NEGOCIO="${NEGOCIO:-TAXI}"

api() { # method path json
  curl -sS -X "$1" "$BASE$2" -H 'Content-Type: application/json' ${TOKEN:+-H "Authorization: Bearer $TOKEN"} ${3:+-d "$3"}
}

echo "-> register (tolerates existing email)"
api POST /usuarios/registro \
  "{\"nombre\":\"$NOMBRE\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"tipoNegocio\":\"$NEGOCIO\"}" \
  | jq -r '.code' || true

echo "-> login"
TOKEN=$(api POST /usuarios/login "{\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" | jq -r '.data.token')
[ "$TOKEN" != "null" ] && [ -n "$TOKEN" ] || { echo "login failed"; exit 1; }
echo "   token acquired"

MONTH=$(date +%Y-%m)
echo "-> monthly goal for $MONTH (works Mon-Sat)"
api POST /finanzas/metas \
  '{"montoObjetivo":3000,"diasLaborables":[1,2,3,4,5,6]}' | jq -r '.code'

# tipo|monto|dia|descripcion  -- spread across the current month
TX=(
  "INGRESO|180.50|02|Carreras manana"
  "EGRESO|45.00|02|Combustible"
  "INGRESO|210.00|05|Carreras dia completo"
  "EGRESO|30.00|05|Almuerzo"
  "INGRESO|95.75|08|Carreras tarde"
  "EGRESO|120.00|08|Mantenimiento"
  "INGRESO|260.00|12|Dia pico"
  "INGRESO|140.00|15|Carreras noche"
  "EGRESO|50.00|15|Combustible"
)

echo "-> seeding ${#TX[@]} transactions"
for row in "${TX[@]}"; do
  IFS='|' read -r tipo monto dia desc <<<"$row"
  api POST /finanzas/transacciones \
    "{\"monto\":$monto,\"tipo\":\"$tipo\",\"descripcion\":\"$desc\",\"fecha\":\"$MONTH-${dia}T10:00:00\"}" \
    | jq -r '"   \(.code) \(.data.tipo // "") \(.data.monto // "")"'
done

echo "-> daily quota after seed"
api GET /finanzas/cuota-diaria | jq '.data'
echo "done. login: $EMAIL / $PASSWORD"
