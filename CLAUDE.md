# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A home energy management app: reads battery SOC from a Deye hybrid inverter over the local network, pulls PV production forecast from Forecast.Solar (two planes: 3kWp east + 5kWp west, both 30° tilt), and makes exactly **two decisions a day** — matching PGE G12's two cheap tariff windows — on whether to charge the battery from the grid: once at 21:45 for the overnight window (22:00–06:00, using tomorrow's forecast) and once at 12:45 for the short afternoon window (13:00–15:00, using the rest of today's forecast). Each decision is written to the inverter's own Time-of-Use schedule and logged for the frontend to display.

Monorepo: `backend/` (Quarkus, Java 21) + `frontend/` (Angular 21, standalone/signals). Quinoa builds the Angular app and serves it from the same Quarkus jar — one process, one port, meant to end up running on a LAN-only device (currently developed on macOS).

## Commands

Backend (run from `backend/`):
- `./mvnw quarkus:dev` — dev mode; Quinoa also starts `ng serve` and proxies frontend requests, so this alone gives you the full stack on `http://localhost:8080`.
- `./mvnw test` — full backend test suite.
- `./mvnw test -Dtest=ChargeDecisionPolicyTest` — single test class; `-Dtest=ClassName#methodName` for a single method.
- `./mvnw package` — builds `target/quarkus-app/quarkus-run.jar` (run with `java -jar target/quarkus-app/quarkus-run.jar`), embedding the built Angular app.

Frontend (run from `frontend/`, only needed standalone — normally Quinoa drives it):
- `npm start` / `ng serve` — dev server on `:4200` (won't have a working backend behind `/api` unless Quarkus is also running).
- `npm test` / `ng test` — Vitest unit tests.
- `npm run build` / `ng build` — production build to `dist/frontend/browser`.

Requires Node compatible with Angular 21's engine check (`^20.19.0 || ^22.12.0 || >=24.0.0`); Quinoa is configured with `quarkus.quinoa.package-manager-install=false` to use the system `node`/`npm` on PATH rather than downloading its own (a pinned older Node version there previously broke `ng serve` — don't reintroduce `package-manager-install=true` with an old `node-version` pin).

## Architecture

### Hexagonal, package-by-feature (not package-by-layer)

Everything lives under one bounded context, `energyplanning`, structured as domain → ports → application → adapters — deliberately not `service/`/`dao/`/`controller/` packages:

```
energyplanning/
  domain/       pure logic, zero framework annotations: BatteryStatus, ProductionForecast,
                TariffWindow/TariffRate/TariffCalendar (G12 window math, dashboard display only),
                ChargeWindow (OVERNIGHT/AFTERNOON), ChargeSchedule, DecisionReason, ChargeDecision,
                PlanningPolicyConfig, ChargeDecisionPolicy (the decision engine, called once per window)
  port/in/      PlanOvernightChargeUseCase, PlanAfternoonTopUpUseCase, GetEnergyStatusUseCase,
                GetPlanningHistoryUseCase
  port/out/     InverterPort, ForecastPort (forecastFor + remainingToday), PlanningHistoryPort, ClockPort
  application/  EnergyPlanner — the only orchestrator; wires ports + domain, no business logic itself
  adapter/in/   web/ (JAX-RS resource), scheduler/ (two @Scheduled crons, 21:45 and 12:45)
  adapter/out/  inverter/solarman/, forecast/forecastsolar/, persistence/, clock/
```

`ChargeDecisionPolicy` and `TariffCalendar` are tested with plain JUnit (no `@QuarkusTest`, no mocks) — that's intentional and is where the actual business rules live; if you change the charge/no-charge logic, start there. `platform/security/` (Basic Auth) sits outside the `energyplanning` hexagon as a cross-cutting platform concern.

`ChargeDecision` deliberately does **not** carry a rendered message — `ChargeDecisionPolicy` returns a `DecisionReason` enum value plus a `Map<String, Double> reasonParams` (e.g. `soc`, `minSoc`, `forecastKwh`, `requiredKwh`, `targetSoc`). Turning that into a sentence is the frontend's job (see i18n below); if you add a new branch to the policy, add the matching enum value and a `reason.<NAME>` key to *both* `frontend/public/i18n/en.json` and `pl.json`, with placeholders matching whatever keys you put in `reasonParams`. `PanacheChargeDecisionRepository` is the only place that knows the reason params are stored as a JSON blob (`reason_params_json` column) — that serialization detail doesn't belong anywhere else.

`ChargeDecisionPolicy.decide(...)` is a single pure function shared by both daily decisions — it takes `ChargeWindow`, a `ProductionForecast`, and an already-computed `requiredKwh`, not a tariff check or a "which window is this" branch internally. **Computing `requiredKwh` differently per window is `EnergyPlanner`'s job, not the policy's**: the overnight decision uses `dailyConsumptionEstimateKwh * forecastSafetyMarginRatio` against `forecastPort.forecastFor(tomorrow)`; the afternoon decision uses `afternoonConsumptionEstimateKwh` against `forecastPort.remainingToday(now)`. `PlanningPolicyConfig` only holds the two SOC thresholds (`minSocPercent`/`fullSocPercent`) for exactly this reason — don't add window-specific fields to it.

There is a second, much smaller bounded context, `io.github.jaroslawdabrowski.preferences`, mirroring the same domain/port/application/adapter shape, for the single persisted language setting (`Language` enum EN/PL). `GET`/`PUT /api/preferences/language` read/write a single-row SQLite table (`PanacheLanguagePreferenceRepository`, fixed id) — this is what the frontend's language switcher persists to, instead of just `localStorage`.

### Inverter integration: the protocol is fully isolated in one adapter package

Deye/Solarman stick loggers don't speak plain Modbus TCP — they wrap a Modbus RTU frame inside a proprietary "Solarman V5" envelope (start/length/control-code/logger-serial/checksum framing) over TCP, typically port 8899. That whole protocol — `SolarmanV5Frame` (envelope), `ModbusRtuFrame` (function 0x03/0x04/0x10 PDUs), `ModbusCrc16` — lives entirely in `adapter/out/inverter/solarman/`, and only `SolarmanInverterAdapter` (implementing `InverterPort`) is visible outside it. No register numbers or byte arrays should ever leak into `domain/` or `application/` — if you're tempted to reference a register address outside this package, it belongs in `SolarmanInverterConfig` instead.

**Writes must use Modbus function 0x10 (write multiple registers), not 0x06 (write single register)** - confirmed against real hardware: 0x06 is silently rejected by this inverter (a non-standard 2-byte `05 00` response, no error, but the value never actually changes - this is what caused the original "safety incident" write to look successful while doing nothing). `ModbusRtuFrame.writeMultipleRegisters(slave, register, new int[]{value})` with a one-element array is the correct way to write a single register on this device; there is no `writeSingleRegister` anymore. `ModbusRtuFrame.checkWriteMultipleRegistersResponse` validates the echo (start register + count) and raises on rejection instead of assuming success - always call it after a write.

The connection details (`pvopt.inverter.solarman.host/port/logger-serial`), `battery-soc-register` (holding register 588, raw value = percent, no scaling), and the TOU (Time-of-Use) register bases are all **confirmed working** against the real logger on the LAN, cross-checked against the inverter's own LCD panel. The inverter has **6 fixed TOU slots** (visible on the LCD under System Work Mode), one register per slot per field, not single global registers: Time `148–153` (HHMM, e.g. `100`=01:00), Power `154–159` (untouched, `10000` per slot is fine as-is), Batt%-target `166–171`, Grid Charge enable `172–177` (confirmed live: toggling a slot's checkbox on the LCD flipped its register `0`→`1`, nothing else changed). `160–165` is an unidentified column; the Gen-enable block was never located (not needed for grid charging). `TouSlots.java` centralizes the slot-index knowledge: which of the 6 slots means what, and which slot(s) a `ChargeWindow` governs (`OVERNIGHT` → slots 5 and 0, since the night window spans midnight and is two device table entries; `AFTERNOON` → slot 2 only).

**The TOU slot *times* have been rewritten** via `TouSlotSetupManualTest` (same manual-diagnostic pattern as `RegisterScannerManualTest` below, `@Disabled` by default - re-run it if the device ever gets reset/reconfigured) — registers 148–153 now hold `TouSlots.START_TIMES_HHMM` (00:00/06:00/13:00/15:00/21:00/22:00, confirmed by re-reading after the write; 21:00 is a deliberate filler slot, always OFF, G12 has no real boundary there). It requires **both** `-Dpvopt.setup.confirm=yes` and `-Dpvopt.setup.writeEnabled=true` to actually write (prints old→new and dry-runs otherwise). Getting this to actually stick is also what surfaced the 0x06-vs-0x10 issue above — the inverter also has a "Lock Out All Changes" LCD setting, which turned out *not* to be the cause here (disabling it alone didn't fix the 0x06 writes) but is worth checking first if a write mysteriously doesn't persist on different hardware.

**Safety switch, born from a real incident**: an earlier, since-removed hourly scheduler once fired against unverified placeholder registers using the (silently-broken) 0x06 write. `SolarmanInverterConfig.writeEnabled` (`pvopt.inverter.solarman.write-enabled`, **default `false`**) gates every actual Modbus write in `SolarmanInverterAdapter.applyChargeSchedule` (and in `TouSlotSetupManualTest`) — with it false, the adapter only logs what it would have written. `SolarmanInverterAdapterTest.doesNotTouchTheNetworkWhenWriteIsDisabled` is a regression guard (points the adapter at an unroutable host — RFC 5737 TEST-NET-1 — and asserts nothing throws, proving no socket is ever opened when disabled). The write path (both the TOU slot time rewrite and the runtime grid-charge-enable/target-SOC registers) is now confirmed working end-to-end with 0x10, read back and matched. **`write-enabled` is still `false` in `application.properties`** — flipping it to `true` is a deliberate, separate decision (the two daily scheduled jobs then run unattended, forever) that hasn't been made yet.

To find an unknown register empirically, `RegisterScannerManualTest` (in the same test package, `@Disabled` by default) reads a range of holding/input registers over real hardware and prints every value - run it with `-Dtest=RegisterScannerManualTest -DfailIfNoTests=false -Dpvopt.scan.host=... -Dpvopt.scan.loggerSerial=... -Dpvopt.scan.from=... -Dpvopt.scan.to=... -Dpvopt.scan.expectedValue=...` (and temporarily comment out its `@Disabled` - Surefire won't run a disabled test even when selected with `-Dtest`). This inverter's logger doesn't implement Modbus function 0x04 (input registers) at all - every input-register read comes back as the same 2-byte non-response - so only function 0x03 (holding registers) is useful here.

`SolarmanV5Frame`'s byte offsets were derived by cross-checking pysolarmanv5's source against a real captured response frame, and the two don't perfectly agree: encoding a request (`encodeRequest`) builds an 11-byte header + a 15-byte fixed trailer before the Modbus payload (so Modbus data logically starts at absolute offset 26), but a genuine response frame's Modbus data starts one byte earlier, at offset 25 (`MODBUS_FRAME_OFFSET`) — matching pysolarmanv5's own `v5_frame[25:-2]` decode slice. Request and response framing are apparently not symmetric in this reverse-engineered protocol; don't "fix" this apparent off-by-one without re-testing against real hardware. `SolarmanV5FrameTest` includes a fixture built from an actual captured response for exactly this reason — treat it as the source of truth over the doc comment's byte-offset table if they ever disagree.

`SolarmanInverterAdapter` connects with an explicit connect-timeout (`socket.connect(new InetSocketAddress(...), timeoutMillis)`), not the bare `Socket(host, port)` constructor — that constructor blocks on the OS default TCP timeout (can be minutes) when the inverter is unreachable, which previously stalled all Vert.x worker threads on one bad request.

### Forecast: two PV planes, two API calls

The installation has separate east- and west-facing arrays, so `ForecastSolarAdapter` always calls api.forecast.solar **twice** (`ForecastSolarConfig.east()`/`west()`, each its own declination/azimuth/kwp) and sums the results - there's no single-plane path. `forecastFor(date)` sums each plane's `watt_hours_day`; `remainingToday(now)` (used by the afternoon decision) sums each plane's `watt_hours` (a cumulative-since-midnight hourly series) by subtracting the value at the latest timestamp ≤ `now` from the day's total - if you're debugging a wrong "remaining today" number, check that math in `ForecastSolarAdapter.remainingWattHoursForPlane` before suspecting the API response itself.

### Security gotcha specific to this project

Basic Auth needs `io.quarkus:quarkus-security` declared explicitly in `pom.xml`. Having `io.quarkus.elytron-security-common` (for `BcryptUtil`) and `quarkus-vertx-http`'s transitive `io.quarkus.security:quarkus-security` API jar is *not* enough — that's a different Maven coordinate (groupId `io.quarkus.security`, not `io.quarkus`) providing only the interfaces, not the extension that actually wires `IdentityProviderManager` into the HTTP layer. Without the real `io.quarkus:quarkus-security` extension, `@Authenticated`, `quarkus.http.auth.permission.*`, and any custom `IdentityProvider` are silently no-ops — every request gets through regardless of credentials, in both dev and packaged/prod mode, with no warning or error logged. If auth ever appears to stop enforcing, check that `security` is still in the "Installed features" line Quarkus logs at startup.

Auth itself: single shared LAN user, configured via `pvopt.security.username` / `pvopt.security.password-hash` (BCrypt) in `application.properties` — not a properties file per the original ask, but achieves the same "hashed password in config" goal. `BasicAuthIdentityProvider` + `SingleUserSecurityIdentity` implement this with no roles/permissions (single user, so `hasRole` always returns false and isn't used).

### Persistence

SQLite via `quarkus-jdbc-sqlite` + `hibernate-community-dialects` (`org.hibernate.community.dialect.SQLiteDialect` — the jdbc-sqlite extension does not itself bundle a Hibernate dialect, that's a separate dependency). DB file at `backend/data/pvopt.db`; the `data/` directory must exist beforehand (SQLite won't create parent directories) — it's committed with a `.gitkeep`, the `.db` file itself is gitignored.

### Frontend

Standalone Angular components with signals, no NgModules. Auth is HTTP Basic, not session/cookie based: `AuthService` stores the `Basic <base64>` header in `sessionStorage` after a successful login probe against `/api/status`, and `authInterceptor` attaches it to every request whose URL starts with `/api`. `authGuard` redirects to `/login` when nothing is stored. There's no token refresh/expiry handling — a 401 from the backend (e.g. wrong stored credentials) surfaces as a failed API call, not an automatic redirect to login.

`quarkus.quinoa.enable-spa-routing=true` is required in `application.properties` - without it, a hard refresh (or directly opening a URL) on any Angular client-side route like `/dashboard` 404s at the Quarkus level, since Quinoa/Quarkus otherwise only serves `index.html` for `/` and falls through to a real 404 for anything else it doesn't recognize as a static asset or backend route.

### i18n (English + Polish)

`@ngx-translate/core` + `@ngx-translate/http-loader`, wired via `provideTranslateService(...)` in `app.config.ts` (no NgModule). Translation JSON lives in `frontend/public/i18n/{en,pl}.json` — that's the `public/` folder (served at the site root by the Angular builder), so the loader prefix is `/i18n/`, *not* the ngx-translate default `/assets/i18n/`. Components inject `TranslatePipe` directly (standalone imports) rather than importing a shared `TranslateModule`.

Error signals in components (`Login`, `Dashboard`, `History`) hold a translation *key* (e.g. `errorKey = signal<string | null>('login.error')`), not a rendered message — the template applies `| translate` at render time so the message reflects whatever language is active. Follow that pattern for any new user-facing error state instead of setting hardcoded text.

`LanguageService` (`core/language.service.ts`) is the only place that talks to `/api/preferences/language`: `init()` (called from an `effect()` in `App` once `AuthService.isAuthenticated()` goes true — the endpoint requires Basic Auth, so it can't be loaded before login) fetches the persisted language and activates it; `changeLanguage()` (used by `LanguageSwitcher`) switches `TranslateService` immediately and persists the choice in the background. Because the preference read is behind auth, the login page itself always renders in the `provideTranslateService` default (`en`) — it can't know the saved language before the user has authenticated.
