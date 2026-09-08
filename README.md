# pvopt

A home energy management app for a PV installation with a battery storage system and a Deye hybrid inverter, billed under the Polish PGE G12 tariff.

Once an hour it:
1. reads the battery's state of charge from the inverter over the local network (Modbus, via the Solarman logger protocol),
2. fetches tomorrow's PV production forecast from [Forecast.Solar](https://forecast.solar),
3. decides whether to charge the battery from the grid during the cheap G12 tariff windows (22:00–06:00, 13:00–15:00) — skipping it when tomorrow's sunshine alone should cover consumption,
4. writes the resulting charge schedule back to the inverter and records the decision for the frontend to display.

Designed to run as a single process on a LAN-only device (e.g. a Raspberry Pi), with Basic Auth for the one shared household login.

## Stack

- **Backend**: Quarkus (Java 21), hexagonal architecture, SQLite persistence.
- **Frontend**: Angular 21 (standalone components/signals), English + Polish i18n, built and served by the same Quarkus process via Quinoa.

See [CLAUDE.md](CLAUDE.md) for the architecture in depth and the commands used day to day.

## Running locally

```bash
cd backend
./mvnw quarkus:dev
```

This starts the backend and, via Quinoa, the Angular dev server too — the full app is then available at `http://localhost:8080` (default login `admin` / `changeme`, see `backend/src/main/resources/application.properties`).

## Status

The Deye/Solarman inverter integration (`backend/.../adapter/out/inverter/solarman/`) has been verified end-to-end against real hardware: the logger's IP/port/serial number are confirmed working, and a Modbus register read succeeds over the real Solarman V5 connection. The specific register addresses (battery SOC, grid-charge enable/target) are still placeholders, though — they need to be identified for this inverter model before the app can reliably read real values or control charging.
