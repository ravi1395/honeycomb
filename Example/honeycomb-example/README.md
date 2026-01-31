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

## Quick demo (manual requests)

All `/honeycomb/**` endpoints require an API key header:

```
X-API-Key: admin-key
```

Shared methods require basic auth:

```
shared / changeit
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

```
curl -u shared:changeit \
  -H 'X-From-Cell: demo-client' \
  -H 'Content-Type: application/json' \
  -d '{"listPrice":49.99,"discountPct":0.15}' \
  http://localhost:8080/honeycomb/shared/discount
```

### Shared methods (via routing)

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
curl -H 'X-API-Key: admin-key' http://localhost:8080/honeycomb/audit

open http://localhost:8080/honeycomb/admin
open http://localhost:8080/honeycomb/swagger-ui.html
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
