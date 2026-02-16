# Honeycomb gRPC Transport Module

> **Version:** 1.4.0  
> **Module:** `honeycomb-grpc`

## Overview

The Honeycomb gRPC module provides an alternative (or complementary) transport layer for inter-cell communication using [gRPC](https://grpc.io/) and [Protocol Buffers](https://protobuf.dev/). This enables high-performance, type-safe binary communication between Honeycomb cells, alongside or instead of the default HTTP/WebFlux transport.

### Key Features

- **Three transport modes:** `http` (default), `grpc`, or `both` — configurable per application
- **Full feature parity** with HTTP transport: shared method invocation, cell CRUD, health checks
- **Server-streaming support** for Flux-based shared methods via `InvokeStream` RPC
- **Automatic metadata propagation:** request IDs, caller cell identity, API keys
- **TLS support** for both server and client
- **Per-cell target routing** for heterogeneous deployments
- **Spring Boot auto-configuration** — zero boilerplate setup
- **gRPC reflection** for `grpcurl`/`grpcui` debugging

---

## Quick Start

### 1. Add the Dependency

```xml
<dependency>
    <groupId>com.honeycomb</groupId>
    <artifactId>honeycomb-grpc</artifactId>
    <version>1.4.0</version>
</dependency>
```

### 2. Enable in `application.yml`

```yaml
honeycomb:
  grpc:
    enabled: true
    transport: both   # http | grpc | both
    server:
      port: 9090
    client:
      default-target: "localhost:9090"
```

### 3. That's It

All `@Sharedwall` methods and cell CRUD endpoints are automatically exposed over gRPC on port 9090 alongside the existing HTTP server.

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  Honeycomb Application                   │
│                                                         │
│  ┌────────────────────┐   ┌──────────────────────────┐  │
│  │  HTTP Transport     │   │  gRPC Transport          │  │
│  │  (Port 8080)        │   │  (Port 9090)             │  │
│  │                     │   │                          │  │
│  │  SharedwallCtrl     │   │  SharedwallGrpcService   │  │
│  │  CellCrudCtrl       │   │  CellGrpcService         │  │
│  │  HealthEndpoint     │   │  HealthGrpcService        │  │
│  └─────────┬───────────┘   └────────────┬─────────────┘  │
│            │                            │                │
│            └─────────┬──────────────────┘                │
│                      │                                   │
│          ┌───────────▼────────────┐                      │
│          │  SharedwallMethodCache  │                      │
│          │  CellDataStoreRouter   │                      │
│          │  CellRegistry          │                      │
│          └────────────────────────┘                      │
└─────────────────────────────────────────────────────────┘
```

Both transports share the same underlying service layer — the same `SharedwallMethodCache`, `CellDataStoreRouter`, and `CellRegistry` handle requests regardless of transport.

---

## Transport Modes

| Mode   | HTTP Server | gRPC Server | gRPC Client | Use Case |
|--------|------------|-------------|-------------|----------|
| `http` | ✅          | ❌           | ✅           | Default. Call gRPC cells from HTTP-only cell |
| `grpc` | ❌          | ✅           | ✅           | gRPC-only deployment |
| `both` | ✅          | ✅           | ✅           | **Recommended.** Full interoperability |

---

## Configuration Reference

```yaml
honeycomb:
  grpc:
    # Master switch
    enabled: false                    # Enable/disable gRPC module
    transport: both                   # http | grpc | both

    # Server configuration
    server:
      port: 9090                      # gRPC listen port (0 = auto)
      max-inbound-message-size: 4194304  # 4MB default
      keep-alive-time: 5m            # Server keepalive interval
      keep-alive-timeout: 20s        # Keepalive ACK timeout
      tls:
        enabled: false
        cert-chain-path: /path/to/cert.pem
        private-key-path: /path/to/key.pem
        trust-cert-path: /path/to/ca.pem   # For mTLS

    # Client configuration (for outgoing inter-cell calls)
    client:
      default-target: "localhost:9090"  # Default target for all cells
      deadline: 10s                     # RPC timeout
      negotiation-type: plaintext       # plaintext | tls
      load-balancing-policy: round_robin
      max-retries: 1
      per-cell-targets:                 # Override per cell
        InventoryCell: "inventory-svc:9090"
        OrderCell: "order-svc:9090"
      tls:
        enabled: false
        trust-cert-path: /path/to/ca.pem

    # Feature toggles
    reflection-enabled: true    # gRPC reflection (for grpcurl)
    health-enabled: true        # gRPC health check service
```

---

## gRPC Services

### HoneycombSharedwallService

Mirrors the HTTP `SharedwallDispatcherController`. Invokes `@Sharedwall`-annotated methods.

| RPC | HTTP Equivalent | Description |
|-----|----------------|-------------|
| `Invoke` | `POST /honeycomb/shared/{method}` | Invoke a shared method |
| `ListMethods` | `GET /honeycomb/shared/methods` | List all shared methods |
| `InvokeStream` | — | Server-streaming per-cell results |

### HoneycombCellService

Mirrors the HTTP cell CRUD endpoints.

| RPC | HTTP Equivalent | Description |
|-----|----------------|-------------|
| `ListItems` | `GET /honeycomb/cells/{name}/items` | List items |
| `GetItem` | `GET /honeycomb/cells/{name}/items/{id}` | Get item |
| `CreateItem` | `POST /honeycomb/cells/{name}/items` | Create item |
| `UpdateItem` | `PUT /honeycomb/cells/{name}/items/{id}` | Update item |
| `DeleteItem` | `DELETE /honeycomb/cells/{name}/items/{id}` | Delete item |

### HoneycombHealthService

| RPC | HTTP Equivalent | Description |
|-----|----------------|-------------|
| `Check` | `GET /actuator/health` | Health status |

---

## Protobuf Schema

The service definitions are in `honeycomb-grpc/src/main/proto/honeycomb/grpc/honeycomb_service.proto`.

Key message types:

```protobuf
message SharedwallInvokeRequest {
    string method_name = 1;
    google.protobuf.Struct payload = 2;    // Arbitrary JSON as Struct
    string from_cell = 3;
    string version = 4;
    string request_id = 5;
    string idempotency_key = 6;
    string raw_json_payload = 7;           // Alternative: raw JSON string
}

message SharedwallInvokeResponse {
    google.protobuf.Struct results = 1;    // { "CellName": { "result": ... } }
    string request_id = 2;
    bool from_cache = 3;
    string error = 4;
    int32 status_code = 5;
}
```

---

## Client Usage

### GrpcSharedwallClient

Builder-pattern client for invoking shared methods:

```java
GrpcSharedwallClient client = GrpcSharedwallClient.builder()
        .target("order-service:9090")
        .fromCell("PaymentCell")
        .deadline(Duration.ofSeconds(5))
        .build();

// Unary invoke
Mono<Map<String, Object>> result = client.invoke("discount", Map.of("amount", 100));

// Typed invoke
Mono<DiscountResult> typed = client.invokeTyped("discount",
        Map.of("amount", 100), DiscountResult.class, "PricingCell");

// Server-streaming invoke
Flux<Map<String, Object>> stream = client.invokeStream("inventory", Map.of("query", "*"));

// List remote methods
Mono<List<SharedwallInvokeInfo>> methods = client.listMethods();
```

### GrpcCellClient

```java
GrpcCellClient cellClient = GrpcCellClient.create("inventory-service:9090");

Mono<List<Map<String, Object>>> items = cellClient.listItems("InventoryCell");
Mono<Map<String, Object>> item = cellClient.getItem("InventoryCell", "item-123");
Mono<Map<String, Object>> created = cellClient.createItem("InventoryCell", payload);
```

### Auto-Configured Client Bean

When `honeycomb.grpc.enabled=true`, a default `GrpcSharedwallClient` bean is auto-configured:

```java
@Autowired
private GrpcSharedwallClient grpcClient;
```

---

## Interceptors

### Server Interceptor

`HoneycombGrpcServerInterceptor` automatically:
- Extracts `x-request-id`, `x-from-cell`, `x-api-key` from gRPC metadata
- Populates gRPC `Context` keys for downstream propagation
- Sets SLF4J MDC for structured logging

### Client Interceptor

`HoneycombGrpcClientInterceptor` automatically:
- Attaches `x-request-id` (from MDC or generates UUID)
- Attaches `x-from-cell` identity

---

## Testing with grpcurl

When `reflection-enabled: true`, you can test with [grpcurl](https://github.com/fullstorydev/grpcurl):

```bash
# List services
grpcurl -plaintext localhost:9090 list

# List methods
grpcurl -plaintext localhost:9090 list honeycomb.grpc.HoneycombSharedwallService

# Invoke shared method
grpcurl -plaintext -d '{"method_name":"discount","raw_json_payload":"{\"amount\":100}"}' \
  localhost:9090 honeycomb.grpc.HoneycombSharedwallService/Invoke

# List items
grpcurl -plaintext -d '{"cell_name":"InventoryCell"}' \
  localhost:9090 honeycomb.grpc.HoneycombCellService/ListItems

# Health check
grpcurl -plaintext -d '{}' \
  localhost:9090 honeycomb.grpc.HoneycombHealthService/Check
```

---

## Spring Profile

Enable gRPC via profile:

```bash
java -jar honeycomb-example.jar --spring.profiles.active=grpc
```

The `application-grpc.yml` profile sets `honeycomb.grpc.enabled=true` with sensible defaults.

---

## Module Structure

```
honeycomb-grpc/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/honeycomb/grpc/
    │   │   ├── config/
    │   │   │   ├── HoneycombGrpcProperties.java        # Configuration properties
    │   │   │   └── HoneycombGrpcAutoConfiguration.java  # Auto-configuration
    │   │   ├── server/
    │   │   │   ├── SharedwallGrpcService.java           # @Sharedwall RPC service
    │   │   │   ├── CellGrpcService.java                 # Cell CRUD RPC service
    │   │   │   ├── HealthGrpcService.java               # Health check RPC
    │   │   │   └── HoneycombGrpcServerInterceptor.java  # Server interceptor
    │   │   ├── client/
    │   │   │   ├── GrpcSharedwallClient.java            # Sharedwall RPC client
    │   │   │   ├── GrpcCellClient.java                  # Cell CRUD RPC client
    │   │   │   └── HoneycombGrpcClientInterceptor.java  # Client interceptor
    │   │   └── util/
    │   │       └── ProtoJsonConverter.java              # Struct↔Map/JSON converter
    │   ├── proto/honeycomb/grpc/
    │   │   └── honeycomb_service.proto                  # Protobuf service definitions
    │   └── resources/META-INF/
    │       ├── spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
    │       └── additional-spring-configuration-metadata.json
    └── test/java/com/honeycomb/grpc/
        ├── config/HoneycombGrpcPropertiesTest.java
        ├── server/HoneycombGrpcServerInterceptorTest.java
        ├── client/
        │   ├── GrpcSharedwallClientTest.java
        │   ├── GrpcCellClientTest.java
        │   └── HoneycombGrpcClientInterceptorTest.java
        └── util/ProtoJsonConverterTest.java
```

---

## Dependency Tree

```
honeycomb-grpc
├── honeycomb-core
├── grpc-server-spring-boot-starter (net.devh)
├── grpc-client-spring-boot-starter (net.devh)
├── grpc-protobuf
├── grpc-stub
├── grpc-netty-shaded
├── protobuf-java / protobuf-java-util
├── reactor-core
├── jackson-databind
├── micrometer-observation
└── resilience4j-circuitbreaker
```

---

## Version History

| Version | Transport | Notes |
|---------|-----------|-------|
| 1.0.0 | HTTP | Initial release |
| 1.1.0 | HTTP | Observability, idempotency |
| 1.2.0 | HTTP | Event-driven, dynamic OpenAPI |
| 1.3.0 | HTTP | Redis cache, schema validation |
| **1.4.0** | **HTTP + gRPC** | **gRPC transport module, configurable transport modes** |
