# Honeycomb Example

This example app consumes the Honeycomb jar as a dependency and exercises the full feature set: cell discovery, CRUD, shared methods, per-cell servers, routing, rate limiting, API key security, audit logs, metrics, autoscaling, WebSocket events, admin UI, and OpenAPI/Swagger.

## Build

From the repository root:

1) Build and install the Honeycomb jar into your local Maven repo:

```
cd honeycomb
mvn -DskipTests install
```

2) Build and run this example:

```
cd Example/honeycomb-example
mvn spring-boot:run
```

For production-like defaults (security, retries, autoscale), use:

```
SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run
```

To enable OAuth2 client wiring, add the oauth2 profile:

```
SPRING_PROFILES_ACTIVE=oauth2 mvn spring-boot:run
```

Or combine with prod:

```
SPRING_PROFILES_ACTIVE=prod,oauth2 mvn spring-boot:run
```

To enable per‑cell storage routing (Redis + Hibernate Reactive) locally, use:

```
SPRING_PROFILES_ACTIVE=routing mvn spring-boot:run
```

The routing profile also enables JSON schema validation and idempotency for create/update requests.

If you don’t already have Postgres running locally, start the included compose file:

```sh
cd Example/honeycomb-example
docker compose -f docker-compose.local-db.yml up -d
```

The app starts on port 8080 and automatically starts per‑cell servers on ports 9091–9093.

## What the example includes

- Three cells: `InventoryCell`, `CatalogCell`, and `PricingCell` (with shared methods).
- Shared methods via `@Sharedwall` including an `allowedFrom` allowlist.
- Per‑cell servers and per‑cell management ports.
- API key protection on `/honeycomb/**` endpoints.
- Rate limiting enabled with per‑cell overrides.
- Routing policy set to `round-robin`.
- Autoscaling enabled (based on request rate).
- Audit log and WebSocket event stream.
- Swagger UI and actuator endpoints.
- Storage is pluggable; per‑cell routing can be enabled via config.

## Quick demo (manual requests)

All `/honeycomb/**` endpoints require an API key header:

```
X-API-Key: admin-key
```

Shared methods should use OAuth2/Bearer when configured. Basic auth remains available for local testing.

For in-code usage, prefer the `SharedwallClient` helper in the Honeycomb library.

Example:

```java
SharedwallClient client = SharedwallClient.builder(webClient, "http://localhost:8080")
  .fromCell("demo-client")
  .registrationId("sharedwall-client")
  .build();

client.invoke("discount", Map.of("listPrice", 49.99, "discountPct", 0.15))
    .subscribe();
```

### Models + CRUD

```
curl -H 'X-API-Key: admin-key' http://localhost:8080/honeycomb/models
curl -H 'X-API-Key: admin-key' http://localhost:8080/honeycomb/models/InventoryCell

curl -H 'X-API-Key: admin-key' \
  -H 'Content-Type: application/json' \
  -d '{"id":"inv-1","sku":"SKU-1","quantity":5,"warehouse":"west"}' \
  http://localhost:8080/honeycomb/models/InventoryCell/items

curl -H 'X-API-Key: admin-key' http://localhost:8080/honeycomb/models/InventoryCell/items
```

### Shared methods (direct)

`@Sharedwall` can be declared on an interface. If the interface has `@Sharedwall`, all methods are shared; if only method-level annotations are present, only those methods are shared.

OAuth2 (recommended):

```
curl -H 'Authorization: Bearer <access-token>' \
  -H 'X-From-Cell: demo-client' \
  -H 'Content-Type: application/json' \
  -d '{"listPrice":49.99,"discountPct":0.15}' \
  http://localhost:8080/honeycomb/shared/discount
```

Basic auth (local/dev):

```
curl -u shared:changeit \
  -H 'X-From-Cell: demo-client' \
  -H 'Content-Type: application/json' \
  -d '{"listPrice":49.99,"discountPct":0.15}' \
  http://localhost:8080/honeycomb/shared/discount
```

### Shared methods (via routing)

OAuth2 (recommended):

```
curl -H 'Authorization: Bearer <access-token>' \
  -H 'X-From-Cell: demo-client' \
  -H 'Content-Type: application/json' \
  -d '{"listPrice":49.99,"discountPct":0.10}' \
  http://localhost:8080/cells/demo-client/invoke/PricingCell/shared/discount?policy=round-robin
```

Basic auth (local/dev):

```
curl -u shared:changeit \
  -H 'X-From-Cell: demo-client' \
  -H 'Content-Type: application/json' \
  -d '{"listPrice":49.99,"discountPct":0.10}' \
  http://localhost:8080/cells/demo-client/invoke/PricingCell/shared/discount?policy=round-robin
```

### Metrics, audit, admin UI

```
curl -H 'X-API-Key: admin-key' http://localhost:8080/honeycomb/metrics/cells
curl -H 'X-API-Key: admin-key' http://localhost:8080/honeycomb/metrics/shared-cache
curl -H 'X-API-Key: admin-key' http://localhost:8080/honeycomb/audit

open http://localhost:8080/honeycomb/admin
open http://localhost:8080/honeycomb/swagger-ui.html
```

Shared dispatch settings (optional):

- `honeycomb.shared.scheduler` (default: `boundedElastic`, options: `parallel`)
- `honeycomb.shared.log-sample-rate` (default: `0.1`, range: 0..1)

### Service cells (method-level exposure)

The example includes a service-style cell implemented with `@Cell` + an interface that declares `@MethodType` annotations:

- Interface: `CatalogServiceApi`
- Implementation: `CatalogServiceCell`
- Routes: `/honeycomb/service/{cell}/{method}`

Examples:

```
# Create
curl -H 'X-API-Key: admin-key' -H 'Content-Type: application/json' \
  -d '{"id":"item-1","title":"Example","category":"books","listPrice":9.99}' \
  http://localhost:8080/honeycomb/service/CatalogService/createItem

# List
curl -H 'X-API-Key: admin-key' \
  http://localhost:8080/honeycomb/service/CatalogService/listItems

# Get by id (path id)
curl -H 'X-API-Key: admin-key' \
  http://localhost:8080/honeycomb/service/CatalogService/getItem/item-1
```

### WebSocket audit events

Connect to the audit event stream:

```
ws://localhost:8080/honeycomb/ws/events
```

## Notes

- Autoscaling uses request rate. With no traffic, it may stop a cell server. Use `/honeycomb/cells/{name}/start` to start it again.
- Per‑cell management ports are exposed for actuator endpoints (example: `http://localhost:9191/honeycomb/actuator/health`).
- Rate limiting is enabled. If you send too many requests quickly, you will receive HTTP 429 responses.
- JWT and mTLS can be enabled via the `honeycomb.security` section in the app config.
- OAuth2 client settings are in `spring.security.oauth2.client` and `example.oauth2.*` (including `registration-id`).

Environment placeholders for OAuth2 client:

```
EXAMPLE_OAUTH2_CLIENT_ID
EXAMPLE_OAUTH2_CLIENT_SECRET
EXAMPLE_OAUTH2_TOKEN_URI
```

## Per‑cell storage routing (optional)
You can route storage per cell without changing cell code:

```yaml
honeycomb:
  storage:
    type: memory
    routing:
      enabled: true
      per-cell:
        InventoryCell: redis
        CatalogCell: hibernate
    hibernate:
      enabled: true
      url: postgresql://localhost:5432/honeycomb
      username: honeycomb
      password: honeycomb
      annotation-free: true
```

## Scaling (multi‑instance + load balancer)

This repo includes a Docker Compose setup that runs two Honeycomb instances behind Nginx, uses static cell addresses, enables autoscaling with per‑cell overrides, stores state in Redis, and includes PostgreSQL for Hibernate Reactive storage:

```
cd Example/honeycomb-example
docker compose up --build
```

Access the app through the load balancer:

```
http://localhost:8080/honeycomb/swagger-ui.html
```

Prometheus is available at:

```
http://localhost:9090
```

Configuration is in `application-docker.yml`.
