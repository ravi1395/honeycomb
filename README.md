# Honeycomb

Honeycomb is a Spring Boot WebFlux framework for “cells”: lightweight model components that are discovered at runtime, exposed through a uniform CRUD + metadata API, and optionally hosted on dedicated per‑cell servers. It includes shared method routing, API‑key security, rate limiting, request metrics, audit logging, and autoscaling based on per‑cell request rates.

This README explains each feature with concrete examples. For a runnable demo, see the example app at examples/honeycomb-example/README.md.

## Modules

This repository is split into:

- `honeycomb-core` — the implementation/library (controllers, services, annotations, DTOs, config properties).
- `honeycomb` (starter) — Spring Boot auto-configuration that wires Honeycomb into a consuming app.

### Dependency coordinates

- Recommended (auto-config enabled): `com.honeycomb:honeycomb`
- Core-only (manual wiring): `com.honeycomb:honeycomb-core`

## Quick start

Build and run from the repo root:

```sh
mvn clean install
mvn -pl honeycomb-core spring-boot:run
```

Try it:

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
- Routing policies for inter-cell calls: all/one/random/round-robin/weighted/least-latency/circuit-aware.
- Autoscaling decisions based on per-cell request rates (configurable thresholds).
- Admin UI for live cells, metrics, and audit events.

### New in 1.2
- **Per-method invoke metrics** — counter + timer (p50/p95/p99) + outcome per method+version.
- **Observation-based distributed tracing** — automatic spans for every shared-method dispatch (Zipkin, OpenTelemetry compatible).
- **Batch invoke** — `POST /honeycomb/shared/batch` dispatches multiple methods in parallel.
- **Async fire-and-forget** — `POST /honeycomb/shared/async/{method}` returns 202 Accepted; execution proceeds in background.
- **Admin diagnostic endpoints** — view registered methods, circuit-breaker states, cache diagnostics, and force-reset breakers.
- **JSON Schema contract validation** — validate shared-method payloads against JSON Schema before dispatch.
- **Idempotency for shared dispatch** — `Idempotency-Key` header now honoured in shared-method invocations.

### New in 1.3
- **Event-driven cell communication** — publish/subscribe event bus with SSE streaming (`/honeycomb/events/stream`). Supports in-memory and Redis transports, topic filtering, and `@CellEventListener` annotation for declarative event handling.
- **Dynamic OpenAPI auto-generation** — CRUD paths for every discovered cell and invoke paths for every `@Sharedwall` method are injected into the Swagger spec at runtime. No manual OpenAPI annotations required.
- **Distributed Redis shared-method cache** — cross-instance cache synchronization via Redis pub/sub. Metadata publishing, cluster-wide invalidation, and admin endpoints at `/honeycomb/admin/cache/`.

### Shared cache admin + metrics
Honeycomb tracks shared-method cache health and allows manual refresh/invalidation.

**Cache endpoints**
- `GET /honeycomb/metrics/shared-cache` — cache stats
- `POST /honeycomb/metrics/shared-cache/refresh` — force refresh
- `DELETE /honeycomb/metrics/shared-cache` — invalidate all entries
- `DELETE /honeycomb/metrics/shared-cache/{method}` — invalidate a single method

**Metrics (Micrometer)**
- `honeycomb.shared.cache.refresh.duration` (timer, histograms + percentiles)
- `honeycomb.shared.cache.refreshes` (counter, tag: `result`)
- `honeycomb.shared.cache.requests` (counter, tags: `method`, `outcome`)
- `honeycomb.shared.cache.refresh.skips` (counter, tag: `reason`)
- `honeycomb.shared.cache.methods` (gauge)
- `honeycomb.shared.cache.last_refresh_duration_ms` (gauge)
- `honeycomb.shared.cache.last_refresh_age_ms` (gauge)
- `honeycomb.shared.cache.consecutive_failures` (gauge)
- `honeycomb.shared.invoker.fallbacks` (counter, tags: `method`, `cell`, `stage`)

**Config knobs**
```yaml
honeycomb:
  shared:
    cache:
      enabled: true
      warmup-enabled: true
      cache-refresh-ms: 60000
      refresh:
        backoff-base-ms: 1000
        backoff-max-ms: 30000
        jitter-ms: 250
```

### Event-driven cell communication (v1.3)

Honeycomb includes a reactive event bus for asynchronous inter-cell communication. Events are modelled as `CellEvent` records with well-known types (`cell.registered`, `item.created`, `shared.invoked`, etc.) and custom payloads.

**Transport options**
- `memory` (default) — in-process Reactor Sinks. Zero external dependencies; ideal for development and single-instance deployments.
- `redis` — Redis pub/sub. Events are broadcast across all instances in a cluster.

**Configuration**
```yaml
honeycomb:
  events:
    enabled: true
    transport: memory       # memory | redis
    default-topic: honeycomb.events
    buffer-size: 256        # in-memory sink buffer size
```

**REST endpoints**
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/honeycomb/events/stream` | GET (SSE) | Real-time stream of all cell events |
| `/honeycomb/events/stream/{topic}` | GET (SSE) | Stream events for a specific topic |
| `/honeycomb/events/publish` | POST | Publish a custom event |

**Publishing events programmatically**
```java
@Autowired CellEventPublisher eventPublisher;

CellEvent event = CellEvent.of("item.created", "InventoryCell",
                                Map.of("sku", "SKU-1", "qty", 5));
eventPublisher.publish(event).subscribe();
```

**Declarative event listeners**
```java
@Cell("CatalogCell")
public class CatalogCell {

    @CellEventListener(CellEvent.TYPE_ITEM_CREATED)
    public Mono<Void> onItemCreated(CellEvent event) {
        // react to new inventory items
        return Mono.empty();
    }
}
```

`@CellEventListener` supports type filtering, source-cell filtering (`fromCells`), and ordering.

**Metrics (Micrometer)**
- `honeycomb.events.published` (counter, tag: `transport`)
- `honeycomb.events.publish.errors` (counter, tag: `transport`)
- `honeycomb.events.routed` (counter)
- `honeycomb.events.dropped` (counter)

### Dynamic OpenAPI auto-generation (v1.3)

Honeycomb automatically generates OpenAPI path items for every discovered cell and shared method at runtime. The Swagger UI at `/honeycomb/swagger-ui.html` will include:

- **Cell CRUD (Dynamic)** tag — list/get/create/update/delete paths for each `@Cell` class with accurate schemas derived from class fields.
- **Shared Methods (Dynamic)** tag — `POST` invoke paths for each `@Sharedwall` method with request/response schemas, version headers, `X-From-Cell`, and `Idempotency-Key` parameters.

No additional annotations are required — the customizer introspects the `CellRegistry` and `SharedwallMethodCache` at runtime.

### Distributed Redis shared-method cache (v1.3)

For multi-instance deployments, shared-method cache metadata can be synchronized across instances via Redis. Each instance publishes its method inventory to a Redis hash and listens for invalidation signals on a pub/sub channel.

**Configuration**
```yaml
honeycomb:
  shared:
    cache:
      type: redis                   # local | redis
      redis-key-prefix: honeycomb:shared-cache
      redis-invalidate-channel: honeycomb:cache:invalidate
      redis-ttl-seconds: 120        # TTL for cached metadata (0 = no expiry)
      sync-enabled: true
```

**Admin endpoints** (only active when `type=redis` and Redis is available)
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/honeycomb/admin/cache/cluster` | GET | Cluster-wide shared method cache metadata |
| `/honeycomb/admin/cache/invalidate` | POST | Broadcast cache invalidation (`?method=discount` or `*`) |
| `/honeycomb/admin/cache/sync` | POST | Force-sync local cache metadata to Redis |

**Metrics (Micrometer)**
- `honeycomb.shared.cache.redis.sync` (counter, tag: `result`)
- `honeycomb.shared.cache.redis.invalidation.received` (counter)

## Production profile

For hardened defaults (security, retries, autoscale, metrics), use the `prod` profile:

```sh
SPRING_PROFILES_ACTIVE=prod mvn -pl honeycomb-core spring-boot:run
```

## Core concepts

### 1) Cell discovery
Annotate any class with `@com.honeycomb.core.annotations.Cell`. Honeycomb scans the application context and classpath, then registers each cell with the `CellRegistry`.

```java
@Cell(port = 8081)
public class SampleModel {
  private String id;
  private String name;
  private int value;
}
```

**Endpoints**
- `GET /honeycomb/models` — list cell names
- `GET /honeycomb/models/{name}` — fields + class metadata

Example:
```sh
curl http://localhost:8080/honeycomb/models
curl http://localhost:8080/honeycomb/models/SampleModel
```

### 2) Generic CRUD for cells
Honeycomb provides generic CRUD per cell. These are dynamic and operate on `Map<String,Object>`.

**Endpoints**
- `POST /honeycomb/models/{cell}/items`
- `GET /honeycomb/models/{cell}/items`
- `GET /honeycomb/models/{cell}/items/{id}`
- `PUT /honeycomb/models/{cell}/items/{id}`
- `DELETE /honeycomb/models/{cell}/items/{id}`

Example:
```sh
curl -H 'Content-Type: application/json' \
  -d '{"id":"s-1","name":"Sample","value":10}' \
  http://localhost:8080/honeycomb/models/SampleModel/items

curl http://localhost:8080/honeycomb/models/SampleModel/items
```

### 2b) ServiceCell (method-level exposure)
For service-driven cells, annotate a Spring bean with `@Cell` and expose only the methods you want using `@MethodType`.

Example:
```java
public interface CatalogServiceApi {
  @MethodType(MethodOp.READ)
  List<Map<String,Object>> listItems();

  @MethodType(MethodOp.CREATE)
  Map<String,Object> createItem(Map<String,Object> body);
}

@Cell("CatalogService")
public class CatalogServiceCell implements CatalogServiceApi {
  public List<Map<String,Object>> listItems() { ... }
  public Map<String,Object> createItem(Map<String,Object> body) { ... }
}
```

Calls are routed to `/honeycomb/service/{cell}/{method}`. If your method expects an `id`, you can pass it as a path segment:

```sh
# list
curl -H 'X-API-Key: admin-key' http://localhost:8080/honeycomb/service/CatalogService/listItems

# get by id (uses path id)
curl -H 'X-API-Key: admin-key' http://localhost:8080/honeycomb/service/CatalogService/getItem/item-1
```

### 3) Per‑cell servers
Each cell can run on a dedicated port. These servers only serve `/honeycomb/**` routes.

**Config**
```yaml
cell:
  ports:
    SampleModel: "8081"
```

**Runtime control**
- `POST /honeycomb/cells/{name}/start`
- `POST /honeycomb/cells/{name}/stop`
- `POST /honeycomb/cells/{name}/restart`
- `GET /honeycomb/cells` — list runtime status

Example:
```sh
curl -X POST http://localhost:8080/honeycomb/cells/SampleModel/start
curl http://localhost:8081/honeycomb/models/SampleModel
```

### 4) Shared methods (cross‑cell invocation)
Methods annotated with `@Sharedwall` are exposed at `/honeycomb/shared/{name}`.

`@Sharedwall` can also be placed on an interface. If the interface is annotated, all methods are shared. If only method-level annotations are present, only those methods are shared.

```java
@Sharedwall(value = "discount", allowedFrom = {"pricing-client"})
public DiscountResult applyDiscount(DiscountRequest req) { ... }
```

**Endpoint**
- `POST /honeycomb/shared/{name}`

Example:
```sh
curl -H 'X-From-Cell: pricing-client' \
  -H 'Content-Type: application/json' \
  -d '{"listPrice":49.99,"discountPct":0.15}' \
  http://localhost:8080/honeycomb/shared/discount
```

**Invoke in code (SharedwallClient, recommended)**
```java
SharedwallClient client = SharedwallClient.builder(oauthClient, "http://localhost:8080")
  .fromCell("pricing-client")
  .registrationId("sharedwall-client")
  .build();

client.invoke("discount", Map.of("listPrice", 49.99, "discountPct", 0.15))
      .subscribe();

// Fallback value
client.invoke("discount", Map.of("listPrice", 49.99, "discountPct", 0.15),
      Map.of("discountedPrice", 49.99, "source", "fallback-value"))
  .subscribe();

// Fallback supplier
client.invoke("discount", Map.of("listPrice", 49.99, "discountPct", 0.15),
      () -> Map.of("discountedPrice", 49.99, "source", "fallback-supplier"))
  .subscribe();

// Fallback function (error-aware)
client.invoke("discount", Map.of("listPrice", 49.99, "discountPct", 0.15),
      ex -> Mono.just(Map.of("discountedPrice", 49.99,
             "source", "fallback-function",
             "reason", ex.getClass().getSimpleName())))
  .subscribe();
```

**Discover invokable shared methods**
- `GET /honeycomb/shared/methods`
- `GET /honeycomb/shared/methods/by-cell`
- `GET /honeycomb/shared/methods/stub?interfaceName=SharedwallApi&packageName=com.example.client.generated`

Optional version filter:
- `GET /honeycomb/shared/methods?version=v2`

```sh
curl -H 'X-API-Key: admin-key' http://localhost:8080/honeycomb/shared/methods
```

**Typed method-call mapping (no shared URL hardcoding)**
```java
interface PricingApi {
  @SharedwallCall("discount")
  Mono<Map<String, Object>> discount(Map<String, Object> request);
}

interface PricingApiV2 {
  @SharedwallCall(value = "discount", version = "v2")
  Mono<Map<String, Object>> discount(Map<String, Object> request);
}

SharedwallClient client = SharedwallClient.builder(webClient, "http://localhost:8080")
    .fromCell("pricing-client")
  .discoveryTimeout(Duration.ofSeconds(5))
  .discoveryRetryCount(1)
  .discoveryCacheTtl(Duration.ofSeconds(30))
    .build();

PricingApi api = client.createTypedClient(PricingApi.class);
api.discount(Map.of("listPrice", 49.99, "discountPct", 0.15)).subscribe();

// Optional fail-fast validation at startup:
PricingApi validatedApi = client.createTypedClient(PricingApi.class, true);

// Optional strict validation options (deprecation + allowedFrom checks)
PricingApi strictApi = client.createTypedClient(
  PricingApi.class,
  true,
  new SharedwallValidationOptions(true, true)
);

// Optional: unwrap the shared response envelope into a typed DTO
record DiscountResult(String currency, java.math.BigDecimal listPrice,
            java.math.BigDecimal discountPct, java.math.BigDecimal discounted) {}

Mono<DiscountResult> typed = validatedApi.discount(Map.of("listPrice", 49.99, "discountPct", 0.15))
  .map(envelope -> {
    Map<String, Object> byCell = (Map<String, Object>) envelope.get("PricingCell");
    Map<String, Object> result = (Map<String, Object>) byCell.get("result");
    return new DiscountResult(
      String.valueOf(result.get("currency")),
      new java.math.BigDecimal(String.valueOf(result.get("listPrice"))),
      new java.math.BigDecimal(String.valueOf(result.get("discountPct"))),
      new java.math.BigDecimal(String.valueOf(result.get("discounted"))));
  });
```

Validation mode checks method alias/name and signature compatibility (parameter count/types and return payload type)
against `/honeycomb/shared/methods` before creating the proxy.

**Response mapping strategies (typed invoke)**
- `RAW_ENVELOPE` — returns full `{cell -> {result/error}}` map
- `FIRST_RESULT` — returns first cell's `result`
- `STRICT_SINGLE_CELL` — requires exactly one cell result (or target cell)
- `MERGED_RESULTS` — returns `{cell -> result}`

```java
interface EchoApi {
  @SharedwallResult(mode = SharedwallEnvelopeMode.FIRST_RESULT)
  Mono<String> echo(String input);
}
```

**Generate typed stubs during build/dev**

Use the utility class to fetch `/honeycomb/shared/methods/stub` and write an interface source file:

```bash
java -cp honeycomb-core/target/honeycomb-core-1.3.0.jar \
  com.honeycomb.core.client.SharedwallStubGenerator \
  http://localhost:8080 \
  src/main/java/com/example/client/generated/SharedwallApi.java \
  SharedwallApi \
  com.example.client.generated
```

**Invoke in code (Bearer token)**
```java
SharedwallClient tokenClient = SharedwallClient.builder(WebClient.builder().build(), "http://localhost:8080")
    .fromCell("pricing-client")
    .bearerTokenSupplier(() -> "<access-token>")
    .build();

tokenClient.invoke("discount", Map.of("listPrice", 49.99, "discountPct", 0.15)).subscribe();
```

### 5) Routing policies for inter‑cell calls
For shared methods invoked via routing (e.g., from a proxy or another cell), Honeycomb supports:

- `all` — call all instances
- `one` — pick one instance
- `random` — random instance
- `round-robin` — cycle through instances
- `weighted` — weights per instance

**Config**
```yaml
honeycomb:
  routing:
    default-policy: "round-robin"
    per-cell-policy:
      "*": "round-robin"
    weights:
      SampleModel:
        "localhost:8081": 2
        "localhost:8082": 1
```

### 6) Static discovery or service registry
Honeycomb can use service discovery (Eureka) or static addresses.

**Static addresses**
```yaml
cell:
  addresses:
    SampleModel: "host-a:8081,host-b:8081"
```

### 7) Security (API keys + OAuth2/Bearer for shared)
API keys protect `/honeycomb/**` endpoints; shared methods support OAuth2/Bearer (recommended) and basic auth.

**Config**
```yaml
honeycomb:
  security:
    api-keys:
      enabled: true
      header: "X-API-Key"
      keys:
        admin: "admin-key"
        cell: "cell-key"
      per-cell:
        "*": ["admin-key", "cell-key"]
        SampleModel: ["admin-key"]
```

Example:
```sh
curl -H 'X-API-Key: admin-key' http://localhost:8080/honeycomb/models
```

**JWT (optional)**
```yaml
honeycomb:
  security:
    require-auth: true
    jwt:
      enabled: true
      issuer-uri: "https://issuer.example.com/"
      jwk-set-uri: "https://issuer.example.com/.well-known/jwks.json"
      audience: "honeycomb-api"
      roles-claim: "roles"
      role-prefix: "ROLE_"
      scopes-claim: "scp"
      scope-prefix: "SCOPE_"
      shared-roles-claim: "shared_roles"
      shared-role-prefix: "ROLE_"
      default-roles: ["ROLE_USER"]
      per-cell-roles:
        "*": ["ROLE_USER"]
        SampleModel: ["ROLE_ADMIN"]
      per-cell-operation-roles:
        "*":
          read: ["ROLE_USER"]
          create: ["ROLE_ADMIN"]
        SampleModel:
          delete: ["ROLE_ADMIN"]
      shared-method-roles:
        "*": ["ROLE_USER"]
        discount: ["ROLE_PRICING"]
```

**Programmatic OAuth2 wiring (utility)**
```java
@Bean
public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http,
                                                    HoneycombSecurityProperties securityProperties) {
    // ... your other rules
    HoneycombUtil.configureOAuth2(http, securityProperties);
    return http.build();
}
```

**mTLS (optional)**
```yaml
honeycomb:
  security:
    mtls:
      enabled: true
      require-client-cert: true
      allowed-subjects:
        - "CN=honeycomb-client,O=Example Corp,L=NYC,ST=NY,C=US"

server:
  ssl:
    enabled: true
    key-store: "classpath:certs/server.p12"
    key-store-password: "changeit"
    key-store-type: "PKCS12"
    trust-store: "classpath:certs/truststore.p12"
    trust-store-password: "changeit"
    trust-store-type: "PKCS12"
    client-auth: need
```

### 8) Rate limiting (Resilience4j)
Per‑cell limits can be configured with global defaults and cell overrides.

```yaml
honeycomb:
  rate-limiter:
    enabled: true
    defaults:
      limit-for-period: 50
      refresh-period: 1s
      timeout: 0ms
    per-cell:
      SampleModel:
        limit-for-period: 10
        refresh-period: 1s
        timeout: 0ms
```

### 9) Metrics and audit
Honeycomb emits request counters and latency timers. It also keeps an in‑memory audit log and streams events via WebSocket.

**Endpoints**
- `GET /honeycomb/metrics/cells`
- `GET /honeycomb/metrics/shared-cache` — shared method cache stats
- `GET /honeycomb/audit`
- `GET /honeycomb/actuator/prometheus`
- `ws://localhost:8080/honeycomb/ws/events`

Example:
```sh
curl http://localhost:8080/honeycomb/metrics/cells
curl http://localhost:8080/honeycomb/metrics/shared-cache
```

**Shared dispatch settings**

- `honeycomb.shared.scheduler` (default: `boundedElastic`, options: `parallel`)
- `honeycomb.shared.log-sample-rate` (default: `0.1`, range: 0..1)
- `honeycomb.shared.methods.policies` per method/version resilience tuning:
  - key format: `methodName:version` (example: `discount:v2`)
  - properties: `timeout`, `retry-count`, `retry-backoff`, `circuit-breaker-enabled`

Example:

```yaml
honeycomb:
  shared:
    methods:
      policies:
        default:
          timeout: 5s
          retry-count: 1
          retry-backoff: 200ms
          circuit-breaker-enabled: true
        discount:v2:
          timeout: 2s
          retry-count: 2
          retry-backoff: 100ms
          circuit-breaker-enabled: true
```

### 9b) Per-method invoke metrics & distributed tracing

Honeycomb instruments every shared-method dispatch with Micrometer counters, timers, and Observation-based spans.

**Micrometer metrics**

| Metric | Type | Tags | Description |
|--------|------|------|-------------|
| `honeycomb.shared.invoke.total` | counter | `method`, `version` | Total invocations dispatched |
| `honeycomb.shared.invoke.duration` | timer (p50/p95/p99) | `method`, `version` | End-to-end dispatch latency |
| `honeycomb.shared.invoke.outcome` | counter | `method`, `version`, `outcome` | Success / error / exception counts |

**Observation tracing**

When `micrometer-observation` is on the classpath (included by default), each dispatch creates an `Observation` named `honeycomb.shared.invoke` with:

- Low-cardinality keys: `method`, `version`
- High-cardinality key: `request.id`

Pair with any Observation-compatible tracer (Zipkin, OpenTelemetry) to get end-to-end spans automatically.

### 9c) Batch invoke

Invoke multiple shared methods in a single HTTP call. All methods execute in parallel; results are returned in order.

**Endpoint**: `POST /honeycomb/shared/batch`

```sh
curl -X POST -H 'Content-Type: application/json' -H 'X-API-Key: admin-key' \
  -d '[
    {"methodName":"discount","version":"v1","body":{"listPrice":49.99}},
    {"methodName":"sumList","version":"v1","body":{"numbers":[1,2,3]}}
  ]' \
  http://localhost:8080/honeycomb/shared/batch
```

Response: array of `{ methodName, version, status, result, error, durationMs }`.

### 9d) Async fire-and-forget

Submit a shared-method call for background execution. The server returns 202 Accepted immediately with a tracking ID.

**Endpoint**: `POST /honeycomb/shared/async/{methodName}`

```sh
curl -X POST -H 'Content-Type: application/json' -H 'X-API-Key: admin-key' \
  -H 'X-Shared-Version: v2' \
  -d '{"listPrice":49.99}' \
  http://localhost:8080/honeycomb/shared/async/discount
```

Response: `{ "trackingId": "...", "status": "accepted" }`

### 9e) Admin diagnostic endpoints

Operational endpoints for inspecting shared-method state at runtime.

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/honeycomb/admin/shared/methods` | GET | All registered shared methods with metadata |
| `/honeycomb/admin/shared/circuit-breakers` | GET | All shared-method circuit breaker states |
| `/honeycomb/admin/shared/circuit-breakers/{method}/{version}` | GET | Specific circuit breaker state |
| `/honeycomb/admin/shared/circuit-breakers/{method}/{version}/reset` | POST | Force-reset a circuit breaker |
| `/honeycomb/admin/shared/cache` | GET | Cache diagnostics (method counts, staleness) |

### 9f) Shared-method JSON Schema validation

Validate shared-method request payloads against JSON Schema files before dispatch.

**Config**
```yaml
honeycomb:
  shared:
    methods:
      schema-validation-enabled: true
```

Place schema files in `classpath:schemas/shared/{methodName}-{version}.schema.json`. If no schema file exists, validation is skipped for that method. Invalid payloads receive a 400 response with validation details.

### 10) Autoscaling
Autoscaling decisions use per‑cell request rates with global and per‑cell thresholds.

```yaml
honeycomb:
  autoscale:
    enabled: true
    evaluation-interval: 20s
    scale-up-rps: 2.0
    scale-down-rps: 0.1
    per-cell-enabled:
      "*": true
    per-cell-scale-up-rps:
      SampleModel: 1.0
    per-cell-scale-down-rps:
      SampleModel: 0.05
```

### 11) Externalized state (Redis / Hibernate Reactive) + per‑cell routing
The data store is pluggable. Default is in‑memory. You can use Redis or Hibernate Reactive globally, or route per cell.

**Global storage**
```yaml
honeycomb:
  storage:
    type: redis   # memory | redis | hibernate
```

**Hibernate Reactive (annotation‑free JSON storage)**
```yaml
honeycomb:
  storage:
    type: hibernate
    hibernate:
      url: postgresql://localhost:5432/honeycomb
      username: honeycomb
      password: honeycomb
      annotation-free: true
```

### 12) Schema validation (optional)
Enable JSON schema validation for create/update payloads. Schemas are loaded from classpath.

```yaml
honeycomb:
  validation:
    enabled: true
    schema-dir: "schemas"
    fail-on-missing-schema: false
    per-cell:
      SampleModel: "SampleModel.schema.json"
```

### 13) Idempotency (optional)
Enable idempotent create/update requests by providing an `Idempotency-Key` header. When enabled, shared-method dispatches also honour the `Idempotency-Key` header — repeated calls with the same key return the cached result instead of re-executing.

```yaml
honeycomb:
  idempotency:
    enabled: true
    store: "memory"   # memory | redis
    header: "Idempotency-Key"
    key-prefix: "honeycomb:idempotency"
    ttl-seconds: 300
```
**Per‑cell routing**
```yaml
honeycomb:
  storage:
    type: memory
    routing:
      enabled: true
      per-cell:
        SampleModel: redis
        InventoryCell: hibernate
    hibernate:
      enabled: true
      url: postgresql://localhost:5432/honeycomb
      username: honeycomb
      password: honeycomb
      annotation-free: true
```

## Admin UI
Honeycomb exposes a simple admin UI:

```
http://localhost:8080/honeycomb/admin
```

## Example configuration

See [honeycomb-core/src/main/resources/application.yml](honeycomb-core/src/main/resources/application.yml) for full configuration examples. The example app also provides a docker profile at examples/honeycomb-example/src/main/resources/application-docker.yml for multi‑instance setups.

## Running multiple instances (Docker Compose)

The example app includes a two‑instance setup behind Nginx plus Redis and Prometheus.

```sh
cd examples/honeycomb-example
docker compose up --build
```

Then access:

- http://localhost:8080/honeycomb/swagger-ui.html
- http://localhost:9090

## Postman and curl

A Postman collection and environment are provided for the `honeycomb-example` app in `docs/postman`.

- Collection: `docs/postman/honeycomb-example.postman_collection.json`
- Environment (dev): `docs/postman/honeycomb-example.postman_environment.dev.json`
- Environment (prod): `docs/postman/honeycomb-example.postman_environment.prod.json`

Import the collection and one of the environments into Postman (File → Import). The environment contains:

- `baseUrl` (dev default: `http://localhost:8080`, prod default: `https://api.example.com`)
- `apiKey` (dev default: `admin-key`, prod: set your production key)

Examples:

```bash
# List cells (sends X-API-Key header)
curl -i -H "X-API-Key: admin-key" http://localhost:8080/honeycomb/cells

# Create an address (JSON body)
curl -i -X POST -H "Content-Type: application/json" -H "X-API-Key: admin-key" \
  -d '{"host":"127.0.0.1","port":8080,"protocol":"http"}' \
  http://localhost:8080/cells/addresses
```

If your app uses a different API key, update the `apiKey` value in the imported environment.
```

## Tests

```sh
mvn test
```

## Release

```sh
git add -A && git commit -m "chore: prepare release"
git tag -a v0.1.0 -m "v0.1.0"
git push --follow-tags
```
