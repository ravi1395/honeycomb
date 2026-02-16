# Honeycomb Roadmap

Last updated: 2026-02-14

## Version 1.3 — Scaling, Ecosystem & Developer Experience

### Tier 1 — High Impact (v1.3.0)

| # | Feature | Status | Target |
|---|---------|--------|--------|
| 1 | **Event-driven cell communication** — Kafka/RabbitMQ event bus for inter-cell async messaging; replaces HTTP-only comms with pub/sub patterns | ✅ In Progress | v1.3.0 |
| 2 | **Distributed shared-method cache (Redis)** — Redis-backed `SharedwallMethodCache` for cross-instance consistency | ✅ In Progress | v1.3.0 |
| 3 | **Dynamic OpenAPI spec auto-generation** — Programmatic OpenAPI paths for every cell CRUD + shared method, generated from `CellRegistry` and `SharedwallMethodCache` metadata | ✅ In Progress | v1.3.0 |

### Tier 2 — Strategic Scaling (v1.4)

| # | Feature | Status | Target |
|---|---------|--------|--------|
| 4 | **GraalVM native image support** — Spring AOT hints + native build plugin for sub-100ms startup | ✅ Complete | v1.4.2 |
| 5 | **gRPC transport for inter-cell calls** — Protobuf-based shared-method dispatch option for internal hot-path latency | ✅ Complete | v1.4.2 |
| 6 | **Cell versioning & blue-green dispatch** — Run v1/v2 of a cell simultaneously with configurable traffic split (90/10, canary) | ✅ Complete | v1.4.2 |
| 7 | **Kubernetes operator / Helm chart** — `HoneycombCell` CRD auto-creating Deployments, Services, HPA per cell | ✅ Complete | v1.4.2 |
| 8 | **Distributed locking (Redis/Zookeeper)** — Coordinated idempotency, leader election for autoscale decisions | ✅ Complete | v1.4.2 |

### Tier 3 — Developer Experience (v1.5)

| # | Feature | Status | Target |
|---|---------|--------|--------|
| 9 | **CLI scaffolding tool** — `honeycomb init`, `honeycomb add-cell`, `honeycomb add-shared` generators | 📋 Planned | v1.5.0 |
| 10 | **Contract testing (Pact / Spring Cloud Contract)** — Auto-generate consumer-driven contracts from shared methods | 📋 Planned | v1.5.0 |
| 11 | **Multi-tenancy support** — `X-Tenant-Id` routing, scoped storage + metrics | 📋 Planned | v1.5.0 |
| 12 | **Cell dependency graph visualization** — Introspect `allowedFrom` + routing config → live Mermaid/D3 graph in admin UI | 📋 Planned | v1.5.0 |

### Tier 4 — Polish & Hardening (ongoing)

| # | Feature | Status | Target |
|---|---------|--------|--------|
| 13 | **RFC 7807 Problem+JSON errors** — `application/problem+json` for all error paths | 📋 Planned | v1.5+ |
| 14 | **Request correlation IDs** — Propagate `X-Request-Id` / `traceparent` through full dispatch chain | 📋 Planned | v1.5+ |
| 15 | **JMH benchmark suite** — CI-integrated benchmarks for shared dispatch, cache refresh, routing | 📋 Planned | v1.5+ |
| 16 | **`@HoneycombTest` test slice** — Boots only Honeycomb wiring + in-memory storage for fast integration tests | 📋 Planned | v1.5+ |

---

## Implementation Plan for v1.3.0

### Feature 1: Event-Driven Cell Communication

**Goal:** Enable cells to publish and subscribe to events via Kafka (or an in-memory fallback), decoupling producers from consumers.

**Components:**
- `CellEvent` DTO — standardized event envelope (type, source cell, payload, timestamp, correlation ID)
- `CellEventPublisher` — reactive event publishing abstraction
- `@CellEventListener` annotation — marks methods as event consumers
- `KafkaCellEventPublisher` — Kafka-backed implementation via `spring-kafka` reactive
- `InMemoryCellEventPublisher` — in-memory `Sinks.Many` fallback for dev/test
- `CellEventListenerRegistry` — discovers and routes events to annotated handlers
- Config properties: `honeycomb.events.enabled`, `honeycomb.events.transport` (kafka | memory), Kafka broker settings
- Integration: dispatch events on CRUD mutations, shared-method invocations, cell lifecycle changes
- REST endpoint: `GET /honeycomb/events/stream` (SSE) for real-time event monitoring

### Feature 2: Dynamic OpenAPI Auto-Generation

**Goal:** Programmatically register OpenAPI path items for every discovered cell (CRUD endpoints) and shared method at startup, so Swagger UI always reflects actual runtime capabilities.

**Components:**
- `DynamicOpenApiCustomizer` (implements `GlobalOpenApiCustomizer`) — runs after springdoc generates the base spec
- Uses `CellRegistry.getCellNames()` + `describeCell()` to generate CRUD path items per cell
- Uses `SharedwallMethodCache.getAllCandidates()` to generate shared-method path items with parameter schemas
- Generates proper request/response schemas from cell field metadata and method parameter types
- Tags grouped by cell name and "Shared Methods"
- API version stamped from project `1.3.0`

### Feature 3: Distributed Redis Cache for Shared Methods

**Goal:** Replace/augment the in-memory `SharedwallMethodCache` with a Redis-backed metadata cache so all instances share the same view.

**Components:**
- `HoneycombCacheProperties` — config: `honeycomb.shared.cache.type` (local | redis), Redis key prefix, TTL
- `RedisSharedMethodCacheSync` — publishes cache metadata to Redis after refresh; reads from Redis on startup
- Redis pub/sub channel `honeycomb:cache:invalidate` to broadcast invalidation across instances
- `SharedwallMethodCache` enhanced: after local refresh → publish metadata to Redis; on invalidation message → local invalidate
- Fallback: if Redis unavailable, gracefully degrade to local-only cache (current behavior)
- Micrometer metrics: `honeycomb.shared.cache.redis.sync` counters (success/failure/fallback)
