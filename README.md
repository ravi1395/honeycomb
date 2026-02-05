# Honeycomb

Honeycomb is a Spring Boot WebFlux framework for “cells”: lightweight model components that are discovered at runtime, exposed through a uniform CRUD + metadata API, and optionally hosted on dedicated per‑cell servers. It includes shared method routing, API‑key security, rate limiting, request metrics, audit logging, and autoscaling based on per‑cell request rates.

This README explains each feature with concrete examples. For a runnable demo, see the example app at Example/honeycomb-example/README.md.

## Quick start

Build and run from the repo root:

```sh
cd honeycomb
mvn package
mvn spring-boot:run
```

Try it:

```sh
curl http://localhost:8080/honeycomb/models
curl http://localhost:8080/honeycomb/models/SampleModel
```

## Production profile

For hardened defaults (security, retries, autoscale, metrics), use the `prod` profile:

```sh
SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run
```

## Core concepts

### 1) Cell discovery
Annotate any class with `@com.example.honeycomb.annotations.Cell`. Honeycomb scans the application context and classpath, then registers each cell with the `CellRegistry`.

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
```

**Invoke in code (Bearer token)**
```java
WebClient webClient = WebClient.builder().build();
HoneycombUtil.invokeSharedwall(
  webClient,
  "http://localhost:8080",
  "discount",
  Map.of("listPrice", 49.99, "discountPct", 0.15),
  "<access-token>",
  "pricing-client",
  MediaType.APPLICATION_JSON
).subscribe();
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
Enable idempotent create/update requests by providing an `Idempotency-Key` header.

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

See [src/main/resources/application.yml](src/main/resources/application.yml) for full configuration examples. The example app also provides a docker profile at Example/honeycomb-example/src/main/resources/application-docker.yml for multi‑instance setups.

## Running multiple instances (Docker Compose)

The example app includes a two‑instance setup behind Nginx plus Redis and Prometheus.

```sh
cd Example/honeycomb-example
docker compose up --build
```

Then access:

```
http://localhost:8080/honeycomb/swagger-ui.html
http://localhost:9090

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
