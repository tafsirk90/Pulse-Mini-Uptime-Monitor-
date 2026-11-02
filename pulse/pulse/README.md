# Pulse: a mini uptime monitor

Add a website. Pulse checks it every few seconds or minutes, records how fast it answers, opens an
**incident** when it goes down, closes it when it recovers, and can alert you by email or webhook
(Discord / Slack). Every user also gets a public status page.

Built with Java 17, Spring Boot 3, Spring Security + JWT, Spring Data JPA, H2, and a plain HTML/CSS/JS
frontend (no build step).

## Run it

```bash
mvn spring-boot:run
```

Open **http://localhost:8080** (use `localhost`, not your LAN IP, so the demo monitor works).

Other options: `mvn test` runs the unit tests. `docker build -t pulse . && docker run -p 8080:8080 -v pulse-data:/app/data pulse`
runs it in Docker. API docs are at `/swagger-ui.html`.

Data is stored in `./data/pulse.mv.db`, so it survives restarts. Delete the `data` folder for a clean start.

## 2-minute demo

1. Create an account.
2. Click **Add demo monitor**. It watches this app's own `/demo/flaky` page, which is healthy for 60 seconds
   and then returns HTTP 503 for 60 seconds, repeating.
3. Watch the heartbeat strip fill up. After 3 failed checks in a row the monitor turns **Down** and an
   incident opens. When the page recovers the incident closes with its duration.
4. Click **Details** for uptime, average and p95 response time, the chart, and the incident history.
5. Click **Public status page** to see what your visitors would see.
6. Add an alert channel (a Discord webhook is the easiest) and use **Send test**.

## How it works

| Piece | What it does |
|---|---|
| `CheckScheduler` | Every 5s: finds monitors whose `nextCheckAt` has passed and runs their checks **in parallel** on a thread pool. A "running" set prevents the same monitor being checked twice. |
| `HttpChecker` | One check: GET with a timeout, expected status (default any 2xx), optional keyword in the body. Redirects are followed by hand (max 3). Every error becomes a failed result. |
| `MonitorStateService` | The state machine (below). |
| `UptimeCalculator` | Uptime % and percentiles, as pure functions (unit tested). |
| `RetentionJob` | Nightly: deletes check results older than 30 days. Incidents are kept. |
| `AlertService` | Sends DOWN / RECOVERED alerts on a separate thread pool. |
| `UrlGuard` | SSRF protection (below). |

### The state machine (avoids false alarms and alert spam)

```
UP/UNKNOWN + 3 failures in a row  ->  DOWN : open an Incident (starts at the FIRST failure) + send DOWN alert
DOWN       + 1 success            ->  UP   : close the Incident + send RECOVERED alert
```

One failed check can be a network blip, so a monitor only goes down after `app.monitor.failure-threshold`
(default 3) failures in a row. Alerts fire only on a state change, so a long outage sends one alert, not one per check.

### Uptime is calculated from incidents, not from counting checks

```
uptime % = (window - downtime) / window * 100
```

Downtime is the sum of incident durations clipped to the window (24h / 7d / 30d). This does not depend on
the check interval. The window never starts before the monitor was created, so a new monitor is not
credited with time it was not watched. Pausing a monitor closes any open incident.

### SSRF protection

Users can enter any URL, so without protection they could make the server request `http://localhost/...`,
internal network addresses, or a cloud metadata endpoint (`169.254.169.254`). `UrlGuard`:

- allows only `http` and `https`, and rejects URLs containing a username or password;
- resolves the host and rejects loopback, private (10/8, 172.16/12, 192.168/16), link-local, CGNAT,
  multicast and IPv6 unique-local addresses;
- runs when a monitor or webhook is saved, before every check, and before every redirect hop;
- makes one deliberate exception: this app's own `/demo/` pages, so the demo monitor works.

Known limit: the host is resolved once for the check and again by the HTTP client, so a hostile DNS server could
in theory answer differently the second time (DNS rebinding). A production system would pin the resolved IP.

## API

All `/api/monitors` and `/api/channels` endpoints need `Authorization: Bearer <token>`.

| Method | Path | |
|---|---|---|
| POST | `/api/auth/register`, `/api/auth/login` | public |
| GET / POST | `/api/monitors` | list (with state, 24h uptime, recent checks) / create |
| PUT / DELETE | `/api/monitors/{id}` | edit / delete |
| PUT | `/api/monitors/{id}/active?value=false` | pause / resume |
| GET | `/api/monitors/{id}/stats?range=24h\|7d\|30d` | uptime, avg, p95, chart points |
| GET | `/api/monitors/{id}/incidents` | incident history |
| GET / POST / DELETE | `/api/channels`, `/api/channels/{id}` | alert channels |
| POST | `/api/channels/{id}/test` | send a test alert |
| GET | `/api/public/status/{slug}` | public status data (no login) |
| GET | `/demo/flaky` | the fake flaky website |

## Configuration (`application.properties`)

| Property | Default | Meaning |
|---|---|---|
| `app.jwt.secret` | placeholder | **Change this** (32+ characters) before deploying |
| `app.monitor.failure-threshold` | 3 | failures in a row before DOWN |
| `app.monitor.max-per-user` | 20 | monitors per account |
| `app.monitor.check-threads` | 8 | parallel checks |
| `app.monitor.retention-days` | 30 | how long raw check results are kept |
| `app.mail.enabled` | false | set true and fill in `spring.mail.*` for real emails |

## Database tables

`users`, `monitors`, `check_results`, `incidents`, `alert_channels`.

## Project layout

```
config/      SecurityConfig, ExecutorConfig, OpenApiConfig
controller/  Auth, Monitor, Channel, Public, StatusPage, Demo
dto/         Dtos (records)
entity/      User, Monitor, CheckResult, Incident, AlertChannel
net/         UrlGuard (SSRF), HttpChecker, CheckOutcome
scheduler/   CheckScheduler, RetentionJob
security/    JwtService, JwtAuthFilter, UserDetailsServiceImpl, CurrentUser
service/     MonitorStateService, MonitorService, StatsService, AlertService, ...
util/        UptimeCalculator, Formats
static/      index.html (dashboard), status.html (public page), css/, js/
```

## Limits

- Runs as a single instance (the scheduler is not distributed).
- H2 file database: fine for a project and demos; switch the datasource for heavier use.
- Only GET requests are monitored.
