#!/usr/bin/env python3
"""Seed a realistic 6-month history for a Lima taxi driver demo user.

Hits the real API on :9090 so the net-based quota engine, categories, envelope
and validations all run. Reproducible (fixed random seed) so re-running against
a fresh user yields the same story. Local dev only.

Story: driver joined ~6 months ago (current date 2026-07-08). Started earning
~S/1800/month with almost no expense control (net near zero), and climbed month
over month as they tracked finances -- gross income rises and the expense ratio
falls until they reach financial stability with a solid net surplus.

Reset for a clean slate: use a NEW email (default already unique-ish), or stop
the server, delete ./data and restart (nukes ALL local users).
Usage:  python scripts/seed-demo-history.py
Env override: BASE, EMAIL, PASSWORD
"""
import json
import os
import random
import urllib.request
import urllib.error
from calendar import monthrange
from datetime import date, datetime

BASE = os.environ.get("BASE", "http://localhost:9090/api/v1")
EMAIL = os.environ.get("EMAIL", "roonymirko@gmail.com")
PASSWORD = os.environ.get("PASSWORD", "Chepen1234@")
NOMBRE = os.environ.get("NOMBRE", "Roony Mirko")
TODAY = date(2026, 7, 8)  # user-declared "current date"

random.seed(42)  # reproducible history

# (year, month, gross_target, fuel_fraction_of_day_income)
# gross rises; fuel fraction falls -> net margin improves month over month,
# tracing the path from "spends almost everything" to financial stability.
MONTHS = [
    (2026, 2, 1800, 0.38),  # unstable start: high expense ratio, net near zero
    (2026, 3, 2100, 0.34),
    (2026, 4, 2500, 0.30),
    (2026, 5, 2850, 0.26),
    (2026, 6, 3200, 0.22),  # solid net surplus
    (2026, 7, 3400, 0.21),  # partial: only days up to TODAY -- stable
]

token = None


def api(method, path, payload=None):
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode())


def working_days(year, month):
    """Pick ~5 varying weekdays per calendar week; cap July at TODAY."""
    last = monthrange(year, month)[1]
    days = []
    week = {}
    for d in range(1, last + 1):
        dt = date(year, month, d)
        if dt > TODAY:
            break
        week.setdefault(dt.isocalendar()[1], []).append(d)
    for _, week_days in week.items():
        # work 5 of the (up to 7) days this week, chosen at random
        n = min(5, len(week_days))
        days.extend(sorted(random.sample(week_days, n)))
    return sorted(days)


def main():
    global token
    print(f"-> register {EMAIL} (tolerates existing)")
    print("  ", api("POST", "/usuarios/registro", {
        "nombre": NOMBRE, "email": EMAIL,
        "password": PASSWORD, "tipoNegocio": "TAXI"}).get("code"))

    login = api("POST", "/usuarios/login", {"email": EMAIL, "password": PASSWORD})
    token = login["data"]["token"]
    print("-> login ok, usuarioId", login["data"]["usuarioId"])

    cats = {}
    for nombre in ["Combustible", "Comida", "Mantenimiento", "Peajes"]:
        res = api("POST", "/finanzas/categorias", {"nombre": nombre, "tipo": "EGRESO"})
        cats[nombre] = res["data"]["id"]
    print("-> categories:", cats)

    def tx(monto, tipo, desc, dt, cat=None):
        payload = {
            "monto": round(monto, 2), "tipo": tipo, "descripcion": desc,
            "fecha": dt.strftime("%Y-%m-%dT%H:%M:%S")}
        if cat:
            payload["categoriaId"] = cats[cat]
        api("POST", "/finanzas/transacciones", payload)

    print(f"\n{'Mes':<9}{'Bruto':>9}{'Egreso':>9}{'Neto':>9}{'Dias':>6}")
    for year, month, gross_target, fuel_frac in MONTHS:
        wdays = working_days(year, month)
        if not wdays:
            continue
        # Divide the monthly target by a TYPICAL working-day count, not the
        # actual list -- otherwise a partial current month crams a whole
        # month's income into a few days. ~22 working days/month at 5/week.
        base = gross_target / 22
        gross = egreso = 0.0
        # one maintenance lump per month, heavier early (worse upkeep habits)
        maint_day = random.choice(wdays)
        maint_amt = random.uniform(80, 190) * (1.4 if month <= 4 else 0.8)
        for d in wdays:
            day_income = base * random.uniform(0.7, 1.35)
            hour = random.randint(6, 21)
            dt = datetime(year, month, d, hour, random.randint(0, 59))
            desc = "Carreras noche" if hour >= 18 else "Carreras dia"
            tx(day_income, "INGRESO", desc, dt)
            gross += day_income
            # daily expenses
            fuel = day_income * fuel_frac * random.uniform(0.85, 1.15)
            tx(fuel, "EGRESO", "Gasolina", dt.replace(hour=min(hour, 22)), "Combustible")
            egreso += fuel
            comida = random.uniform(10, 22)
            tx(comida, "EGRESO", "Almuerzo", dt, "Comida")
            egreso += comida
            if random.random() < 0.5:
                peaje = random.uniform(4, 12)
                tx(peaje, "EGRESO", "Peaje", dt, "Peajes")
                egreso += peaje
            if d == maint_day:
                tx(maint_amt, "EGRESO", "Mantenimiento", dt, "Mantenimiento")
                egreso += maint_amt
        print(f"{year}-{month:02d}{gross:>9.0f}{egreso:>9.0f}{gross-egreso:>9.0f}{len(wdays):>6}")

    # current-month goal: aspirational NET target (engine compares against net)
    print("\n-> meta (July) net goal 1800, works Mon/Tue/Thu/Fri/Sat")
    print("  ", api("POST", "/finanzas/metas", {
        "montoObjetivo": 1800, "diasLaborables": [1, 2, 4, 5, 6]}).get("code"))

    print("-> cuota-diaria:", api("GET", "/finanzas/cuota-diaria")["data"])
    print("-> salud-financiera:",
          [a["code"] for a in api("GET", "/finanzas/salud-financiera")["data"]])
    print(f"done. login: {EMAIL} / {PASSWORD}")


if __name__ == "__main__":
    main()
