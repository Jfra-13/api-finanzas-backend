# Demo seed — 6-month path to financial stability

Loads a reproducible 6-month history for a Lima taxi-driver demo user by hitting
the **live API** on `:9090`. Going through the API (not raw SQL) means the
net-based quota engine, category creation, the response envelope and all
validations run exactly as in production. Local dev / H2 only.

## Demo user

| Field | Value |
|-------|-------|
| Email | `roonymirko@gmail.com` |
| Password | `Chepen1234@` |
| Name | `Roony Mirko` |
| Business | `TAXI` |

## Story

Driver joined ~6 months ago (declared current date `2026-07-08`). Starts earning
~S/1800/month with almost no expense control (net near zero) and improves month
over month as they track finances — gross income rises, expense ratio falls —
until they reach stability with a solid net surplus.

| Month | Gross target | Expense ratio | Meaning |
|-------|--------------|---------------|---------|
| 2026-02 | 1800 | 0.38 | Unstable start, spends almost everything |
| 2026-03 | 2100 | 0.34 | Begins tracking |
| 2026-04 | 2500 | 0.30 | Margin improving |
| 2026-05 | 2850 | 0.26 | Gaining control |
| 2026-06 | 3200 | 0.22 | Solid net surplus |
| 2026-07 | 3400 | 0.21 | Stable (partial month, days up to `2026-07-08`) |

## Rebuild & load (local H2)

The seed appends data, so start from a clean schema to avoid duplicates.

1. **Stop** the running server.
2. **Delete** the local H2 store — nukes ALL local users:
   ```bash
   rm -rf ./data
   ```
3. **Start** the server (Flyway rebuilds the schema from migrations):
   ```bash
   ./mvnw spring-boot:run        # Windows: .\mvnw spring-boot:run
   ```
4. **Seed** (server must be up):
   ```bash
   python scripts/seed-demo-history.py
   ```
5. **Log in** with the credentials above.

## Notes

- Reproducible: fixed `random.seed(42)` — same story every run against a fresh user.
- Registration sends **no** email (OTP is only for password recovery), so the
  real Gmail address is never contacted.
- Overridable via env: `BASE`, `EMAIL`, `PASSWORD`, `NOMBRE`. Point `BASE` at a
  non-local host at your own risk — this is intended for local H2 only.
