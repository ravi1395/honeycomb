# Honeycomb

`honeycomb` is a Spring Boot WebFlux project that discovers cell model classes annotated with `@Cell` and exposes them via REST as generic "data point" descriptions. It also demonstrates per-cell runtimes, inter-cell shared-method invocation, and configurable CRUD controls, plus routing, security, metrics, audit logs, and autoscaling.

How it works
- Annotate any class with `@com.example.honeycomb.annotations.Cell`.
- Mark methods you want to expose cross-cell with `@com.example.honeycomb.annotations.Sharedwall`.
- On startup the `CellRegistry` scans the classpath and application context for `@Cell` types and records their metadata.
- The controller exposes endpoints:
  - `GET /honeycomb/models` — list discovered model names
  - `GET /honeycomb/models/{name}` — describe fields and class name for the given model
  - `GET /honeycomb/cells` — list cells with runtime status
  - `POST /honeycomb/cells/{name}/start|stop|restart` — manage cell servers
  - `POST /honeycomb/shared/{name}` — invoke shared methods
  - `GET /honeycomb/metrics/cells` — per-cell request counts
  - `GET /honeycomb/audit` — recent audit events
  - `GET /honeycomb/admin` — admin UI (HTML)
  - `GET /honeycomb/actuator/**` — actuator endpoints

Example

1. Build:
```sh
cd "honeycomb"
mvn package
```

2. Run:
```sh
mvn spring-boot:run
```

3. Try:
```sh
curl http://localhost:8080/honeycomb/models
curl http://localhost:8080/honeycomb/models/SampleModel
```

Additional features
- Per-cell servers: the application can start extra HTTP servers bound to cell-specific ports. These servers are restricted to the `/honeycomb/**` routes and are useful to run cell-specific endpoints on their own port.
- Shared methods: annotate methods with `@Sharedwall` to expose them to other cells at `/honeycomb/shared/{name}`. Use the `allowedFrom` attribute to restrict which caller cell names may invoke the method.
- Configurable CRUD: disable create/read/update/delete per-cell or globally via `honeycomb.disabled-operations` in `application.yml`.
- Reactive WebFlux stack with non-blocking WebClient forwarding for inter-cell interactions.
- Service discovery (Eureka client) and static discovery fallbacks for cell addresses.
- API key protection for `/honeycomb/**` endpoints, with per-cell allow lists.
- Rate limiting per cell with Resilience4j.
- Audit logging with WebSocket event stream at `/honeycomb/ws/events`.
- Request metrics (per-cell counts) and Prometheus endpoint.
- Routing policies for inter-cell calls: all/one/random/round-robin/weighted.
- Autoscaling decisions based on per-cell request rates (configurable thresholds).
- Admin UI for live cells, metrics, and audit events.

Health endpoint
- Each cell server exposes a health endpoint at `/honeycomb/health` which reports `status`, `port`, and `cell` (and cell metadata).

Configuration examples
- `src/main/resources/application.yml` contains example settings:

```yaml
honeycomb:
  disabled-operations:
    "*":
      - delete
    SampleModel:
      - create
  security:
    api-keys:
      enabled: false
      header: "X-API-Key"
      keys:
        admin: "admin-key"
      per-cell:
        "*": ["admin-key"]
  rate-limiter:
    enabled: true
    defaults:
      limit-for-period: 50
      refresh-period: 1s
  routing:
    default-policy: "all"
    per-cell-policy:
      "*": "all"
  autoscale:
    enabled: false
    evaluation-interval: 30s
    scale-up-rps: 5.0
    scale-down-rps: 0.5

shared:
  caller-header: "X-From-Cell"
```

Clients invoking shared methods should set the caller header (default `X-From-Cell`) to identify themselves. Shared methods can also declare `allowedFrom` to limit callers.

Running multiple cell instances
- A simple helper script is provided at `scripts/run-multi-cells.sh` to launch multiple JVM instances with per-cell overrides:

```sh
./scripts/run-multi-cells.sh SampleModel=9090
```

This will run the packaged JAR with `--cell.ports.SampleModel=9090`.

Notes
- The discovery defaults to scanning under `com.example` — adjust `CellRegistry` if you want to scan particular packages.
- The registry currently returns field names and types; you can extend it to instantiate example objects or map arbitrary models to a common "data point" structure.

Running tests
- Build and run the test suite with:

```sh
mvn test
```

Release
- To tag a release locally then push it:

```sh
git add -A && git commit -m "chore: prepare release"
git tag -a v0.1.0 -m "v0.1.0"
git push --follow-tags
```
