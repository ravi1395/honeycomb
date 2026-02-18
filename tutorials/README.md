# Honeycomb Tutorials

Comprehensive, step-by-step guides for building, running, and managing microservices with the Honeycomb framework **v1.5.0**.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Tutorial 1 — Build a New App from Scratch](#tutorial-1--build-a-new-app-from-scratch)
3. [Tutorial 2 — Building Microservices with Honeycomb](#tutorial-2--building-microservices-with-honeycomb)
4. [Tutorial 3 — Managing Microservices at Runtime](#tutorial-3--managing-microservices-at-runtime)
5. [Tutorial 4 — Multi-Tenancy](#tutorial-4--multi-tenancy)
6. [Tutorial 5 — Cell Versioning & Traffic Splits](#tutorial-5--cell-versioning--traffic-splits)
7. [Tutorial 6 — Distributed Locking & Leader Election](#tutorial-6--distributed-locking--leader-election)
8. [Tutorial 7 — Contract Testing](#tutorial-7--contract-testing)
9. [Tutorial 8 — Events, Streaming & WebSocket](#tutorial-8--events-streaming--websocket)
10. [Tutorial 9 — Observability: OpenTelemetry, Metrics & Audit](#tutorial-9--observability-opentelemetry-metrics--audit)
11. [Tutorial 10 — Security: API Keys, JWT & mTLS](#tutorial-10--security-api-keys-jwt--mtls)
12. [Tutorial 11 — Production Hardening](#tutorial-11--production-hardening)
13. [Tutorial 12 — The Plugin System](#tutorial-12--the-plugin-system)
14. [Tutorial 13 — gRPC Transport](#tutorial-13--grpc-transport)
15. [Tutorial 14 — Testing with @HoneycombTest](#tutorial-14--testing-with-honeycombtest)
16. [Tutorial 15 — Multi-Instance & Deployment](#tutorial-15--multi-instance--deployment)
17. [Quick Reference](#quick-reference)
18. [Configuration Reference](#configuration-reference)
19. [Authoring Guide](#authoring-guide)

---

## Prerequisites

| Requirement | Version |
|-------------|---------|
| Java (JDK) | 21+ |
| Maven | 3.9+ |
| Docker | 24+ (optional, for container tutorials) |
| Redis | 7+ (optional, for distributed locking / caching / events) |
| curl / httpie | any (for testing endpoints) |

Verify:

```bash
java -version   # should print 21+
mvn -v           # should print 3.9+
```

---

## Tutorial 1 — Build a New App from Scratch

This tutorial walks you through creating a brand-new Spring Boot application that uses Honeycomb as its runtime framework, from an empty directory to a running microservice with cells, CRUD, and shared methods.

### Step 1: Install Honeycomb to your local Maven repository

Clone (or use) the Honeycomb repo and install the modules:

```bash
cd honeycomb          # project root
mvn clean install     # installs all modules at version 1.5.0
```

This makes the following artifacts available in `~/.m2/repository`:

| Module | Coordinates |
|--------|-------------|
| Core library | `com.honeycomb:honeycomb-core:1.5.0` |
| gRPC transport | `com.honeycomb:honeycomb-grpc:1.5.0` |
| Starter (auto-config) | `com.honeycomb:honeycomb:1.5.0` |

### Step 2: Create the project skeleton

Create a new directory and `pom.xml`:

```bash
mkdir my-honeycomb-app && cd my-honeycomb-app
```

Create `pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
           https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.10</version>
    <relativePath/>
  </parent>

  <groupId>com.myorg</groupId>
  <artifactId>my-honeycomb-app</artifactId>
  <version>0.1.0</version>

  <properties>
    <java.version>21</java.version>
    <spring-cloud.version>2024.0.2</spring-cloud.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-dependencies</artifactId>
        <version>${spring-cloud.version}</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <dependencies>
    <!-- Honeycomb starter (auto-configures everything) -->
    <dependency>
      <groupId>com.honeycomb</groupId>
      <artifactId>honeycomb</artifactId>
      <version>1.5.0</version>
    </dependency>

    <!-- WebFlux (Honeycomb is reactive) -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>

    <!-- Actuator (metrics, health, info) -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <!-- Test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

### Step 3: Create the Spring Boot main class

```bash
mkdir -p src/main/java/com/myorg/app
```

Create `src/main/java/com/myorg/app/MyHoneycombApp.java`:

```java
package com.myorg.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MyHoneycombApp {
    public static void main(String[] args) {
        SpringApplication.run(MyHoneycombApp.class, args);
    }
}
```

### Step 4: Define your first cell (data model)

A **cell** is any class annotated with `@Cell`. Honeycomb discovers it at runtime, provides CRUD endpoints at `/honeycomb/models/{name}/items`, and optionally starts a dedicated HTTP server on the declared port.

Create `src/main/java/com/myorg/app/cells/ProductCell.java`:

```java
package com.myorg.app.cells;

import com.honeycomb.core.annotations.Cell;

@Cell(port = 9091)
public class ProductCell {
    private String id;
    private String name;
    private String category;
    private double price;
    private int stock;

    // Default constructor (required for deserialization)
    public ProductCell() {}

    public ProductCell(String id, String name, String category, double price, int stock) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.stock = stock;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
}
```

### Step 5: Configure the application

Create `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: my-honeycomb-app
  main:
    allow-bean-definition-overriding: true

server:
  port: 8080

honeycomb:
  security:
    api-keys:
      enabled: true
      header: "X-API-Key"
      keys:
        admin: "my-secret-key"
      per-cell:
        "*": ["my-secret-key"]
```

### Step 6: Build and run

```bash
mvn clean package -DskipTests
mvn spring-boot:run
```

### Step 7: Verify it works

```bash
# List discovered cells
curl -H "X-API-Key: my-secret-key" http://localhost:8080/honeycomb/models
# Expected: ["ProductCell"]

# Get cell metadata
curl -H "X-API-Key: my-secret-key" http://localhost:8080/honeycomb/models/ProductCell

# Create an item
curl -X POST -H "Content-Type: application/json" \
     -H "X-API-Key: my-secret-key" \
     -d '{"id":"p-1","name":"Widget","category":"Hardware","price":29.99,"stock":100}' \
     http://localhost:8080/honeycomb/models/ProductCell/items

# List items
curl -H "X-API-Key: my-secret-key" http://localhost:8080/honeycomb/models/ProductCell/items

# Get by ID
curl -H "X-API-Key: my-secret-key" http://localhost:8080/honeycomb/models/ProductCell/items/p-1

# Update
curl -X PUT -H "Content-Type: application/json" \
     -H "X-API-Key: my-secret-key" \
     -d '{"id":"p-1","name":"Widget Pro","category":"Hardware","price":39.99,"stock":80}' \
     http://localhost:8080/honeycomb/models/ProductCell/items/p-1

# Delete
curl -X DELETE -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/models/ProductCell/items/p-1
```

> **Congratulations!** You have a running Honeycomb app with automatic cell discovery, CRUD, API-key security, and a dedicated per-cell server on port 9091.

### Step 8: API versioning (v1.5.0)

With API versioning enabled (the default), all endpoints are also available under `/v1/...`:

```bash
# Both paths return the same result:
curl -H "X-API-Key: my-secret-key" http://localhost:8080/honeycomb/models
curl -H "X-API-Key: my-secret-key" http://localhost:8080/v1/honeycomb/models

# The response includes X-API-Version header
```

Configure versioning in `application.yml`:

```yaml
honeycomb:
  api:
    versioning-enabled: true       # default: true
    current-version: "v1"          # default: "v1"
    legacy-paths-enabled: true     # keep unversioned paths active
    version-header: "X-API-Version"
```

---

## Tutorial 2 — Building Microservices with Honeycomb

This tutorial builds on Tutorial 1 and adds multiple cells, shared methods for cross-cell communication, service-style cells, cell dependencies, and inter-cell routing.

### 2.1 Add more cells

Create `src/main/java/com/myorg/app/cells/OrderCell.java`:

```java
package com.myorg.app.cells;

import com.honeycomb.core.annotations.Cell;
import com.honeycomb.core.annotations.DependsOnCell;

@Cell(port = 9092)
@DependsOnCell(value = {"ProductCell"}, required = true)  // v1.5.0
public class OrderCell {
    private String id;
    private String productId;
    private int quantity;
    private String status;   // PENDING, CONFIRMED, SHIPPED

    public OrderCell() {}
    public OrderCell(String id, String productId, int quantity, String status) {
        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
        this.status = status;
    }

    // getters + setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
```

Note the `@DependsOnCell` annotation (since v1.5.0): Honeycomb validates at startup that all declared dependencies are registered. If a required dependency is missing, the app fails to start.

Create `src/main/java/com/myorg/app/cells/CustomerCell.java`:

```java
package com.myorg.app.cells;

import com.honeycomb.core.annotations.Cell;

@Cell(port = 9093)
public class CustomerCell {
    private String id;
    private String name;
    private String email;

    public CustomerCell() {}
    public CustomerCell(String id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    // getters + setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
```

Restart and verify:

```bash
curl -H "X-API-Key: my-secret-key" http://localhost:8080/honeycomb/models
# Expected: ["ProductCell", "OrderCell", "CustomerCell"]
```

All three cells now have independent CRUD endpoints and per-cell HTTP servers (8080, 9091, 9092, 9093).

### 2.2 Add shared methods for cross-cell communication

Shared methods let one cell expose logic that other cells (or external callers) can invoke via a uniform HTTP endpoint.

Create `src/main/java/com/myorg/app/shared/InventoryApi.java` (interface-based):

```java
package com.myorg.app.shared;

import com.honeycomb.core.annotations.Sharedwall;
import reactor.core.publisher.Mono;

import java.util.Map;

@Sharedwall
public interface InventoryApi {

    /**
     * Check stock for a product. Callable by any cell.
     */
    Mono<Map<String, Object>> checkStock(Map<String, Object> payload);

    /**
     * Reserve stock. Only callable from the OrderCell.
     */
    @Sharedwall(value = "reserveStock", allowedFrom = {"OrderCell"})
    Mono<Map<String, Object>> reserveStock(Map<String, Object> payload);
}
```

Implement the interface in a service cell:

Create `src/main/java/com/myorg/app/shared/InventoryService.java`:

```java
package com.myorg.app.shared;

import com.honeycomb.core.annotations.Cell;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Cell
@Component
public class InventoryService implements InventoryApi {

    @Override
    public Mono<Map<String, Object>> checkStock(Map<String, Object> payload) {
        String productId = (String) payload.getOrDefault("productId", "unknown");
        return Mono.just(Map.of(
            "productId", productId,
            "available", 42,
            "warehouse", "us-east-1"
        ));
    }

    @Override
    public Mono<Map<String, Object>> reserveStock(Map<String, Object> payload) {
        String productId = (String) payload.getOrDefault("productId", "unknown");
        int qty = ((Number) payload.getOrDefault("quantity", 0)).intValue();
        return Mono.just(Map.of(
            "productId", productId,
            "reserved", qty,
            "status", "RESERVED"
        ));
    }
}
```

Now add a method-level `@Sharedwall` on a standalone bean:

Create `src/main/java/com/myorg/app/shared/PricingService.java`:

```java
package com.myorg.app.shared;

import com.honeycomb.core.annotations.Cell;
import com.honeycomb.core.annotations.Sharedwall;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Cell
@Component
public class PricingService {

    @Sharedwall("calculatePrice")
    public Mono<Map<String, Object>> calculatePrice(Map<String, Object> payload) {
        double listPrice = ((Number) payload.getOrDefault("listPrice", 0)).doubleValue();
        double discount  = ((Number) payload.getOrDefault("discountPct", 0)).doubleValue();
        double finalPrice = listPrice * (1 - discount);
        return Mono.just(Map.of(
            "listPrice", listPrice,
            "discountPct", discount,
            "finalPrice", finalPrice
        ));
    }

    @Sharedwall(value = "bulkPrice", version = "v2")
    public Mono<Map<String, Object>> bulkPrice(Map<String, Object> payload) {
        double listPrice = ((Number) payload.getOrDefault("listPrice", 0)).doubleValue();
        int quantity     = ((Number) payload.getOrDefault("quantity", 1)).intValue();
        double discount  = quantity >= 100 ? 0.20 : quantity >= 50 ? 0.10 : 0.0;
        return Mono.just(Map.of(
            "unitPrice", listPrice * (1 - discount),
            "quantity", quantity,
            "total", listPrice * (1 - discount) * quantity,
            "discountApplied", discount
        ));
    }
}
```

### 2.3 Invoke shared methods

Restart the app and test the shared endpoints:

```bash
# Check stock (any caller)
curl -X POST -H "Content-Type: application/json" \
     -H "X-API-Key: my-secret-key" \
     -H "X-From-Cell: CustomerCell" \
     -d '{"productId": "p-1"}' \
     http://localhost:8080/honeycomb/shared/checkStock

# Reserve stock (only OrderCell allowed)
curl -X POST -H "Content-Type: application/json" \
     -H "X-API-Key: my-secret-key" \
     -H "X-From-Cell: OrderCell" \
     -d '{"productId": "p-1", "quantity": 5}' \
     http://localhost:8080/honeycomb/shared/reserveStock

# Calculate price
curl -X POST -H "Content-Type: application/json" \
     -H "X-API-Key: my-secret-key" \
     -d '{"listPrice": 49.99, "discountPct": 0.15}' \
     http://localhost:8080/honeycomb/shared/calculatePrice

# Bulk price (v2)
curl -X POST -H "Content-Type: application/json" \
     -H "X-API-Key: my-secret-key" \
     -d '{"listPrice": 29.99, "quantity": 100}' \
     "http://localhost:8080/honeycomb/shared/bulkPrice?version=v2"
```

### 2.4 Service-style cells with `@MethodType`

For cells where you own the persistence logic and want to expose only specific operations (not generic CRUD), use `@MethodType`:

```java
package com.myorg.app.services;

import com.honeycomb.core.annotations.Cell;
import com.honeycomb.core.annotations.MethodType;
import com.honeycomb.core.annotations.MethodOp;
import org.springframework.stereotype.Component;

import java.util.*;

@Cell("ReportService")
@Component
public class ReportServiceCell {

    private final List<Map<String, Object>> reports = new ArrayList<>();

    @MethodType(MethodOp.READ)
    public List<Map<String, Object>> listReports() {
        return Collections.unmodifiableList(reports);
    }

    @MethodType(MethodOp.CREATE)
    public Map<String, Object> generateReport(Map<String, Object> params) {
        Map<String, Object> report = new HashMap<>(params);
        report.put("id", UUID.randomUUID().toString());
        report.put("generatedAt", System.currentTimeMillis());
        reports.add(report);
        return report;
    }
}
```

Service cell endpoints:

```bash
# Generate a report (CREATE)
curl -X POST -H "Content-Type: application/json" \
     -H "X-API-Key: my-secret-key" \
     -d '{"type": "monthly-sales", "month": "2026-01"}' \
     http://localhost:8080/honeycomb/service/ReportService/generateReport

# List reports (READ)
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/service/ReportService/listReports
```

### 2.5 Batch and async dispatch

Invoke multiple shared methods at once, or fire asynchronously:

```bash
# Batch — invoke multiple methods in parallel
curl -X POST -H "Content-Type: application/json" \
     -H "X-API-Key: my-secret-key" \
     -d '[
       {"method": "checkStock",      "payload": {"productId": "p-1"}},
       {"method": "calculatePrice",  "payload": {"listPrice": 49.99, "discountPct": 0.1}}
     ]' \
     http://localhost:8080/honeycomb/shared/batch

# Async fire-and-forget — returns 202 Accepted immediately
curl -X POST -H "Content-Type: application/json" \
     -H "X-API-Key: my-secret-key" \
     -d '{"productId": "p-1", "quantity": 5}' \
     http://localhost:8080/honeycomb/shared/async/reserveStock
```

### 2.6 JSON Schema validation for shared methods

Add a schema file to validate payloads before they reach your handler:

Create `src/main/resources/schemas/checkStock.schema.json`:

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "required": ["productId"],
  "properties": {
    "productId": { "type": "string", "minLength": 1 }
  },
  "additionalProperties": false
}
```

Enable validation in `application.yml`:

```yaml
honeycomb:
  validation:
    enabled: true
    schema-dir: "schemas"
    fail-on-missing-schema: false
```

Now any call to `checkStock` without a valid `productId` string is rejected with an RFC 7807 problem detail response (status 400) before your handler runs.

### 2.7 Idempotency

Use the `Idempotency-Key` header to make shared-method calls safe to retry:

```bash
curl -X POST -H "Content-Type: application/json" \
     -H "X-API-Key: my-secret-key" \
     -H "Idempotency-Key: order-123-reserve" \
     -d '{"productId": "p-1", "quantity": 5}' \
     http://localhost:8080/honeycomb/shared/reserveStock
```

Replaying the exact same request with the same key returns the cached result without re-executing the handler.

Enable in config:

```yaml
honeycomb:
  idempotency:
    enabled: true
    store: memory       # memory | redis
    ttl-seconds: 300
```

### 2.8 Disable specific CRUD operations

Selectively disable operations globally or per cell:

```yaml
honeycomb:
  disabled-operations:
    "__all__":
      - delete           # Disable DELETE on all cells by default
    OrderCell:
      - create           # Disable manual order creation (use shared method instead)
```

### 2.9 Shared method policies

Configure per-method timeouts, retry logic, and circuit-breaking:

```yaml
honeycomb:
  shared:
    methods:
      schema-validation-enabled: true
      policies:
        default:
          timeout: 5s
          retry-count: 1
          retry-backoff: 200ms
          circuit-breaker-enabled: true
        reserveStock:
          timeout: 10s
          retry-count: 3
          retry-backoff: 500ms
```

---

## Tutorial 3 — Managing Microservices at Runtime

Once your cells are running, Honeycomb provides built-in tools for runtime management, monitoring, and troubleshooting.

### 3.1 Cell discovery and addresses

```bash
# List all known cell addresses (includes per-cell server ports)
curl -H "X-API-Key: my-secret-key" http://localhost:8080/cells/addresses

# Get addresses for a specific cell
curl -H "X-API-Key: my-secret-key" http://localhost:8080/cells/ProductCell/addresses

# Register a new cell address manually
curl -X POST -H "Content-Type: application/json" \
     -H "X-API-Key: my-secret-key" \
     -d '{"host":"10.0.1.5","port":9091,"protocol":"http"}' \
     http://localhost:8080/cells/addresses

# View cell server status
curl -H "X-API-Key: my-secret-key" http://localhost:8080/honeycomb/cells
```

### 3.2 Per-cell server management

Honeycomb can start/stop/restart individual cell HTTP servers at runtime:

```bash
# Stop a cell's dedicated server
curl -X POST -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/cells/ProductCell/stop

# Start it again
curl -X POST -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/cells/ProductCell/start

# Restart
curl -X POST -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/cells/ProductCell/restart
```

### 3.3 Admin diagnostic endpoints

```bash
# List all registered shared methods with metadata
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/admin/shared/methods

# View circuit breaker states
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/admin/shared/circuit-breakers

# Get a specific circuit breaker
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/admin/shared/circuit-breakers/reserveStock/v1

# Force-reset a tripped circuit breaker
curl -X POST -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/admin/shared/circuit-breakers/reserveStock/v1/reset

# Cache admin diagnostics
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/admin/shared/cache

# Metrics: cache stats
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/metrics/shared-cache

# Invalidate a specific method's cache
curl -X DELETE -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/metrics/shared-cache/checkStock

# Force full cache refresh
curl -X POST -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/metrics/shared-cache/refresh
```

### 3.4 Admin dashboard

Honeycomb ships a built-in admin HTML dashboard:

```bash
open http://localhost:8080/honeycomb/admin
```

### 3.5 Per-cell OpenAPI / Swagger

Honeycomb auto-generates OpenAPI specs for each cell:

```bash
# Swagger UI
open http://localhost:8080/honeycomb/swagger-ui.html

# Full OpenAPI JSON
curl http://localhost:8080/honeycomb/api-docs

# Per-cell OpenAPI spec
curl http://localhost:8080/honeycomb/swagger/ProductCell
```

### 3.6 Routing policies for inter-cell calls

When you have multiple instances of the same cell (or cell addresses), configure how Honeycomb routes:

```yaml
honeycomb:
  routing:
    default-policy: round-robin
    per-cell-policy:
      ProductCell: least-latency
      OrderCell: circuit-aware
    weights:
      ProductCell:
        "10.0.1.5:9091": 70
        "10.0.1.6:9091": 30
```

Available policies: `one`, `random`, `round-robin`, `weighted`, `least-latency`, `circuit-aware`, `all`

### 3.7 Rate limiting

Enable globally or override per cell:

```yaml
honeycomb:
  rate-limiter:
    enabled: true
    defaults:
      limit-for-period: 50
      refresh-period: 1s
      timeout: 0ms
    per-cell:
      ProductCell:
        limit-for-period: 100
        refresh-period: 1s
```

### 3.8 Autoscaling decisions

Honeycomb can generate scale-up/down signals based on request rates:

```yaml
honeycomb:
  autoscale:
    enabled: true
    evaluation-interval: 30s
    scale-up-rps: 5.0
    scale-down-rps: 0.5
    per-cell-enabled:
      ProductCell: true
```

### 3.9 Request correlation

Every request automatically gets an `X-Request-Id` header (generated if not present). This ID is propagated through all inter-cell calls and appears in logs and traces.

```bash
# The response includes X-Request-Id
curl -v -H "X-API-Key: my-secret-key" http://localhost:8080/honeycomb/models
# < X-Request-Id: 550e8400-e29b-41d4-a716-446655440000

# You can also supply your own:
curl -H "X-API-Key: my-secret-key" \
     -H "X-Request-Id: my-correlation-123" \
     http://localhost:8080/honeycomb/models
```

---

## Tutorial 4 — Multi-Tenancy

> **Since v1.4.3** — Honeycomb supports per-tenant data isolation, metrics scoping, and tenant-aware rate limiting.

### 4.1 Enable multi-tenancy

```yaml
honeycomb:
  tenant:
    enabled: true
    header-name: "X-Tenant-Id"      # header that carries the tenant identifier
    default-tenant: ""               # empty = reject requests without tenant header
    allowed-tenants: []              # empty = accept any tenant
    enforce-header: true             # require the header on every request
    storage-key-template: "honeycomb:tenant:{tenant}:cell"
    scope-metrics: true              # prefix metrics with tenant tag
```

### 4.2 Send tenant-scoped requests

All cell CRUD and shared method endpoints become tenant-scoped automatically:

```bash
# Create an item under tenant "acme"
curl -X POST -H "Content-Type: application/json" \
     -H "X-API-Key: my-secret-key" \
     -H "X-Tenant-Id: acme" \
     -d '{"id":"p-1","name":"Widget","category":"Hardware","price":29.99,"stock":100}' \
     http://localhost:8080/honeycomb/models/ProductCell/items

# List items for tenant "acme"
curl -H "X-API-Key: my-secret-key" \
     -H "X-Tenant-Id: acme" \
     http://localhost:8080/honeycomb/models/ProductCell/items

# Items from tenant "beta" are isolated
curl -H "X-API-Key: my-secret-key" \
     -H "X-Tenant-Id: beta" \
     http://localhost:8080/honeycomb/models/ProductCell/items
# Expected: [] (empty — different tenant namespace)
```

### 4.3 Restrict allowed tenants

```yaml
honeycomb:
  tenant:
    enabled: true
    allowed-tenants:
      - acme
      - beta
      - gamma
```

Requests with an unrecognized tenant ID receive a 403 Forbidden response.

### 4.4 Tenant info endpoints

```bash
# Get current tenant from request context
curl -H "X-API-Key: my-secret-key" \
     -H "X-Tenant-Id: acme" \
     http://localhost:8080/honeycomb/tenants/current

# List all allowed tenants
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/tenants/allowed

# Full tenant configuration
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/tenants/config
```

### 4.5 Per-tenant rate limiting

Combine tenancy with rate limiting for fair usage enforcement:

```yaml
honeycomb:
  rate-limiter:
    enabled: true
    tenant-aware: true           # v1.5.0
    per-tenant:
      acme:
        limit-for-period: 200
        refresh-period: 1s
      beta:
        limit-for-period: 50
        refresh-period: 1s
```

---

## Tutorial 5 — Cell Versioning & Traffic Splits

> **Since v1.4.2** — Deploy multiple versions of the same cell for blue-green or canary rollouts.

### 5.1 Define versioned cells

Use `@CellVersion` to create multiple implementations of the same cell name:

```java
package com.myorg.app.cells;

import com.honeycomb.core.annotations.Cell;
import com.honeycomb.core.annotations.CellVersion;

@Cell("ProductCell")
@CellVersion("v1")
public class ProductCellV1 {
    private String id;
    private String name;
    private double price;
    // getters + setters ...
}
```

```java
package com.myorg.app.cells;

import com.honeycomb.core.annotations.Cell;
import com.honeycomb.core.annotations.CellVersion;

@Cell("ProductCell")
@CellVersion("v2")
public class ProductCellV2 {
    private String id;
    private String name;
    private double price;
    private String sku;        // new field in v2
    private String imageUrl;   // new field in v2
    // getters + setters ...
}
```

### 5.2 Configure traffic splits

```yaml
honeycomb:
  versioning:
    enabled: true
    default-version: "v1"
    version-header: "X-Cell-Version"
    traffic-split:
      ProductCell:
        v1: 80    # 80% of traffic goes to v1
        v2: 20    # 20% goes to v2 (canary)
```

### 5.3 Manage splits at runtime

```bash
# View all traffic splits
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/versioning/splits

# View split for a specific cell
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/versioning/splits/ProductCell

# Update traffic split (shift more traffic to v2)
curl -X PUT -H "Content-Type: application/json" \
     -H "X-API-Key: my-secret-key" \
     -d '{"v1": 50, "v2": 50}' \
     http://localhost:8080/honeycomb/versioning/splits/ProductCell

# List all versions of a cell
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/versioning/cells/ProductCell/versions

# Promote v2 to 100% (instant rollout)
curl -X POST -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/versioning/cells/ProductCell/promote/v2
```

### 5.4 Request a specific version

Clients can override the traffic split by specifying the version header:

```bash
curl -H "X-API-Key: my-secret-key" \
     -H "X-Cell-Version: v2" \
     http://localhost:8080/honeycomb/models/ProductCell/items
```

---

## Tutorial 6 — Distributed Locking & Leader Election

> **Since v1.4.2** — Redis-backed distributed locks and leader election for clustered deployments.

### 6.1 Enable distributed locking

```yaml
honeycomb:
  locking:
    enabled: true
    type: redis                  # currently only "redis" is supported
    key-prefix: "honeycomb:lock:"
    default-ttl: 30s
    retry-delay: 100ms
    max-retries: 3
    leader-election:
      enabled: true
      key: "honeycomb:leader"
      ttl: 30s
      renewal-interval: 10s
```

Ensure Redis is available:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

### 6.2 Use locks via the API

```bash
# Acquire a named lock
curl -X POST -H "Content-Type: application/json" \
     -H "X-API-Key: my-secret-key" \
     -d '{"key": "order-processing", "ttlSeconds": 30}' \
     http://localhost:8080/honeycomb/locking/acquire

# Release the lock
curl -X POST -H "Content-Type: application/json" \
     -H "X-API-Key: my-secret-key" \
     -d '{"key": "order-processing"}' \
     http://localhost:8080/honeycomb/locking/release

# Check lock status
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/locking/status
```

### 6.3 Leader election

In a multi-instance deployment, Honeycomb automatically elects one instance as the leader:

```bash
# Check who is the current leader
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/locking/leader

# Voluntarily relinquish leadership
curl -X POST -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/locking/leader/relinquish
```

---

## Tutorial 7 — Contract Testing

> **Since v1.4.3** — Auto-generate and verify shared-method contracts.

### 7.1 Enable contract generation

```yaml
honeycomb:
  contracts:
    enabled: true
    output-dir: "target/honeycomb-contracts"
    format: "spring-cloud-contract"
    verify-on-startup: false
    publish-stubs: false
```

### 7.2 Generate and inspect contracts

```bash
# List all generated contracts
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/contracts

# Get contract for a specific shared method
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/contracts/checkStock

# Export all contracts (downloadable)
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/contracts/export
```

### 7.3 Verify contracts

```bash
# Offline verify — checks contracts against registered metadata
curl -X POST -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/contracts/verify

# Live verify — actually invokes methods and validates results
curl -X POST -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/contracts/verify/live
```

---

## Tutorial 8 — Events, Streaming & WebSocket

> **Since v1.3** — Publish events across cells, subscribe via SSE or WebSocket.

### 8.1 Configure the event system

```yaml
honeycomb:
  events:
    enabled: true
    transport: memory       # memory | redis
    default-topic: "honeycomb.events"
    buffer-size: 256
```

For Redis-backed pub/sub (multi-instance):

```yaml
honeycomb:
  events:
    enabled: true
    transport: redis
```

### 8.2 Publish events

```bash
curl -X POST -H "Content-Type: application/json" \
     -H "X-API-Key: my-secret-key" \
     -d '{"topic": "order.created", "data": {"orderId": "o-1", "total": 59.99}}' \
     http://localhost:8080/honeycomb/events/publish
```

### 8.3 Subscribe via Server-Sent Events (SSE)

```bash
# Stream all events
curl -N -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/events/stream

# Stream only events on a specific topic
curl -N -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/events/stream/order.created
```

### 8.4 WebSocket events

Connect to `ws://localhost:8080/honeycomb/ws/events` for real-time bidirectional event streaming.

---

## Tutorial 9 — Observability: OpenTelemetry, Metrics & Audit

### 9.1 OpenTelemetry distributed tracing (v1.5.0)

Honeycomb v1.5.0 has built-in OpenTelemetry support. Every shared-method dispatch, batch call, and inter-cell invocation produces a trace span.

```yaml
honeycomb:
  tracing:
    enabled: true
    otlp-endpoint: "http://localhost:4318/v1/traces"
    sampling-probability: 1.0
    propagate-w3c: true
    enrich-spans: true
    service-name: "my-honeycomb-app"
```

Span attributes include: method name, version, outcome, caller cell, tenant ID (if multi-tenancy is enabled), and request ID.

To run a local collector:

```bash
docker run -d --name otel-collector \
  -p 4317:4317 -p 4318:4318 \
  otel/opentelemetry-collector:latest
```

### 9.2 Prometheus metrics

```bash
# Prometheus scrape endpoint (mounted under /honeycomb/actuator)
curl http://localhost:8080/honeycomb/actuator/prometheus | grep honeycomb

# Key metrics:
#   honeycomb_requests_total{cell="ProductCell", status="200"}
#   honeycomb_latency_seconds{cell="ProductCell", quantile="0.95"}
#   honeycomb_shared_invoke_total{method="checkStock", outcome="success"}
#   honeycomb_shared_cache_requests_total{method="checkStock", outcome="hit"}
```

Per-cell request counts:

```bash
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/metrics/cells
```

### 9.3 HTTP audit logging (v1.5.0)

The `HttpAuditFilter` logs all Honeycomb requests at the HTTP level (method, path, status, duration, tenant, request ID):

```bash
# Fetch recent audit entries
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/audit
```

Configure audit retention:

```yaml
honeycomb:
  audit:
    max-entries: 500
```

### 9.4 Health checks

```bash
curl http://localhost:8080/honeycomb/actuator/health
```

Honeycomb adds a reactive health indicator for cell servers so container orchestrators can detect unhealthy instances.

---

## Tutorial 10 — Security: API Keys, JWT & mTLS

### 10.1 API-key authentication

```yaml
honeycomb:
  security:
    api-keys:
      enabled: true
      header: "X-API-Key"
      keys:
        admin: "strong-secret-key"
        readonly: "read-only-key"
      per-cell:
        ProductCell: ["admin"]
        OrderCell: ["admin"]
        "*": ["readonly"]       # all cells accept readonly key
```

### 10.2 JWT authentication

```yaml
honeycomb:
  security:
    jwt:
      enabled: true
      issuer-uri: https://auth.myorg.com
      jwk-set-uri: https://auth.myorg.com/.well-known/jwks.json
      audience: "honeycomb-api"
      roles-claim: "roles"
      role-prefix: "ROLE_"
      default-roles:
        - ROLE_USER
      per-cell-roles:
        ProductCell:
          - ROLE_PRODUCT_ADMIN
        OrderCell:
          - ROLE_ORDER_ADMIN
      per-cell-operation-roles:
        ProductCell:
          DELETE:
            - ROLE_SUPER_ADMIN
      shared-method-roles:
        reserveStock:
          - ROLE_ORDER_ADMIN
```

Test with a JWT:

```bash
curl -H "Authorization: Bearer <your-jwt-token>" \
     http://localhost:8080/honeycomb/models
```

### 10.3 Mutual TLS (mTLS)

```yaml
honeycomb:
  security:
    mtls:
      enabled: true
      require-client-cert: true
      allowed-subjects:
        - "CN=service-a,O=MyOrg"
        - "CN=service-b,O=MyOrg"
```

### 10.4 CORS

```yaml
honeycomb:
  security:
    cors-allowed-origins:
      - "http://localhost:3000"
      - "https://app.myorg.com"
```

---

## Tutorial 11 — Production Hardening

### 11.1 Vault secrets management (v1.5.0)

Integrate with HashiCorp Vault for secrets:

```yaml
honeycomb:
  secrets:
    enabled: true
    provider: vault
    vault-path: "secret/honeycomb"
    fail-on-missing: false
    refresh-interval: "0"      # "0" = disabled, or e.g. "5m"
    vault-role: "honeycomb"
```

### 11.2 Adaptive circuit breaker (v1.5.0)

The adaptive circuit breaker automatically adjusts thresholds based on observed error rates:

```yaml
honeycomb:
  circuit-breaker:
    adaptive-enabled: true
    initial-failure-rate-threshold: 50.0
    min-failure-rate-threshold: 20.0
    max-failure-rate-threshold: 80.0
    evaluation-window-size: 10
    adjustment-interval-seconds: 60
    wait-duration-in-open-state-seconds: 30
    sliding-window-size: 10
    permitted-calls-in-half-open: 5
    slow-call-duration-threshold-ms: 5000
    slow-call-rate-threshold: 80.0
```

### 11.3 Distributed cache

```yaml
honeycomb:
  shared:
    cache:
      type: redis              # local | redis
      redis-key-prefix: "honeycomb:shared-cache"
      redis-ttl-seconds: 120
      sync-enabled: true
      warmup-enabled: true     # pre-load cache on startup
```

Cache management:

```bash
# View cache cluster info
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/admin/cache/cluster

# Invalidate all caches
curl -X POST -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/admin/cache/invalidate

# Force cache sync across instances
curl -X POST -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/admin/cache/sync
```

### 11.4 Storage backends

Configure how cell data is stored:

```yaml
honeycomb:
  storage:
    type: memory     # memory | redis | hibernate
```

For Redis persistence:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379

honeycomb:
  storage:
    type: redis
```

### 11.5 Graceful cell deregistration (v1.5.0)

When the app shuts down, cells are gracefully deregistered from discovery services and in-flight requests are completed before servers stop.

### 11.6 Production profile

For hardened defaults:

```bash
SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run
```

---

## Tutorial 12 — The Plugin System

> **Since v1.5.0** — Extend Honeycomb with plugins using the Service Provider Interface (SPI).

### 12.1 Implement a plugin

Create a class that implements `HoneycombPlugin`:

```java
package com.myorg.plugins;

import com.honeycomb.core.plugin.HoneycombPlugin;
import com.honeycomb.core.plugin.PluginContext;

import java.util.Map;

public class AuditPlugin implements HoneycombPlugin {

    @Override
    public String getName() { return "audit-plugin"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public int getOrder() { return 50; }  // lower = higher priority

    @Override
    public void onStartup(PluginContext context) {
        System.out.println("AuditPlugin started in " +
            context.getProperty("spring.application.name"));
    }

    @Override
    public void onCellRegistered(String cellName, Class<?> cellClass) {
        System.out.println("Cell registered: " + cellName);
    }

    @Override
    public boolean onBeforeOperation(String cellName, String operation,
                                     Map<String, Object> context) {
        System.out.println("Before " + operation + " on " + cellName);
        return true;   // return false to abort the operation
    }

    @Override
    public void onAfterOperation(String cellName, String operation,
                                  Map<String, Object> context, Object result) {
        System.out.println("After " + operation + " on " + cellName);
    }

    @Override
    public void onOperationError(String cellName, String operation,
                                  Map<String, Object> context, Throwable error) {
        System.err.println("Error in " + operation + " on " + cellName +
            ": " + error.getMessage());
    }

    @Override
    public void onShutdown() {
        System.out.println("AuditPlugin shutting down");
    }
}
```

### 12.2 Register via SPI

Create the `META-INF/services` file:

```
# src/main/resources/META-INF/services/com.honeycomb.core.plugin.HoneycombPlugin
com.myorg.plugins.AuditPlugin
```

Or register as a Spring bean:

```java
@Bean
public HoneycombPlugin auditPlugin() {
    return new AuditPlugin();
}
```

### 12.3 Plugin lifecycle

| Event | When | Can abort? |
|-------|------|-----------|
| `onStartup(context)` | Framework initialization | No |
| `onCellRegistered(name, class)` | Cell discovered and registered | No |
| `onBeforeOperation(cell, op, ctx)` | Before any CRUD or shared operation | **Yes** (return `false`) |
| `onAfterOperation(cell, op, ctx, result)` | After successful operation | No |
| `onOperationError(cell, op, ctx, error)` | After failed operation | No |
| `onShutdown()` | Application shutdown | No |

`PluginContext` provides access to: `ApplicationContext`, `CellRegistry`, `Environment`, `getBean(Class)`, `getProperty(key)`.

---

## Tutorial 13 — gRPC Transport

> The `honeycomb-grpc` module provides Protocol Buffer–based inter-cell communication as an alternative to HTTP/JSON.

### 13.1 Add the gRPC dependency

```xml
<dependency>
  <groupId>com.honeycomb</groupId>
  <artifactId>honeycomb-grpc</artifactId>
  <version>1.5.0</version>
</dependency>
```

The gRPC module uses:
- gRPC 1.75.0
- Protobuf 4.28.2
- grpc-spring-boot-starter 3.1.0.RELEASE

### 13.2 Protocol definitions

Honeycomb auto-generates gRPC service definitions from your `@Sharedwall` methods. The proto files are generated during the build under `target/generated-sources`.

### 13.3 Configuration

```yaml
honeycomb:
  grpc:
    enabled: true
    port: 6565
```

Refer to [GRPC.md](../docs/GRPC.md) for the full gRPC architecture and advanced configuration.

---

## Tutorial 14 — Testing with @HoneycombTest

> **Since v1.4.3** — A specialized test slice that boots only the Honeycomb framework with sensible defaults for fast integration tests.

### 14.1 Write a test

```java
package com.myorg.app;

import com.honeycomb.core.annotations.HoneycombTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

@HoneycombTest
class ProductCellTest {

    @Autowired
    WebTestClient webClient;

    @Test
    void shouldListCells() {
        webClient.get().uri("/honeycomb/models")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray();
    }

    @Test
    void shouldCreateAndRetrieveProduct() {
        // Create
        webClient.post().uri("/honeycomb/models/ProductCell/items")
                .bodyValue(Map.of(
                    "id", "p-test",
                    "name", "Test Widget",
                    "category", "Test",
                    "price", 19.99,
                    "stock", 10
                ))
                .exchange()
                .expectStatus().isOk();

        // Retrieve
        webClient.get().uri("/honeycomb/models/ProductCell/items/p-test")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Test Widget");
    }
}
```

### 14.2 What @HoneycombTest configures

`@HoneycombTest` is a composite annotation that sets:

| Property | Test Value |
|----------|-----------|
| `honeycomb.storage.type` | `memory` |
| `honeycomb.idempotency.store` | `memory` |
| `eureka.client.enabled` | `false` |
| `spring.cloud.discovery.enabled` | `false` |
| `honeycomb.security.api-key.enabled` | `false` |
| `honeycomb.security.jwt.enabled` | `false` |
| `honeycomb.security.mtls.enabled` | `false` |
| `spring.security.enabled` | `false` |
| `honeycomb.events.enabled` | `false` |
| `honeycomb.locking.enabled` | `false` |
| `honeycomb.tenant.enabled` | `false` |
| `honeycomb.contracts.enabled` | `false` |
| `honeycomb.versioning.enabled` | `false` |
| `honeycomb.autoscale.enabled` | `false` |
| `server.port` | `0` (random) |

This gives you a fast, isolated test environment with no external dependencies (no Redis, no Eureka, no Vault).

---

## Tutorial 15 — Multi-Instance & Deployment

### 15.1 Local multi-instance run (shell script)

Use the included helper script to start multiple Honeycomb instances on different ports:

```bash
cd honeycomb   # project root
mvn clean package -DskipTests

# Start two instances
./scripts/run-multi-cells.sh ProductCell=9091 OrderCell=9092
```

Each instance gets its own log file under `./logs/`. Press `Ctrl+C` to stop all instances.

Environment overrides:

```bash
JAR=target/my-app.jar \
JAVA_OPTS="-Xmx512m" \
PROFILES=prod \
LOG_DIR=/tmp/honeycomb-logs \
  ./scripts/run-multi-cells.sh ProductCell=9091 OrderCell=9092
```

### 15.2 Docker Compose multi-instance

Use the provided `scripts/docker-compose.multi.yml`:

```bash
cd scripts
docker compose -f docker-compose.multi.yml up --build
```

This starts three Honeycomb instances behind an nginx reverse proxy:

| Service | Container port | Description |
|---------|---------------|-------------|
| honeycomb-1 | 8080 | Instance 1 |
| honeycomb-2 | 8080 | Instance 2 |
| honeycomb-3 | 8080 | Instance 3 |
| nginx | 80 → 8080 | Round-robin load balancer |

Test via the load balancer:

```bash
curl -H "X-API-Key: my-secret-key" http://localhost/honeycomb/models
```

### 15.3 Systemd deployment (Linux)

Copy the template unit file:

```bash
sudo cp scripts/honeycomb-cell@.service /etc/systemd/system/
sudo systemctl daemon-reload
```

Start instances:

```bash
sudo systemctl start honeycomb-cell@9091
sudo systemctl start honeycomb-cell@9092

# Check status
systemctl status honeycomb-cell@9091

# View logs
journalctl -u honeycomb-cell@9091 -f
```

### 15.4 Docker Compose with monitoring

The example app includes a full Docker Compose stack with Redis, Prometheus, and Nginx:

```bash
cd examples/honeycomb-example
docker compose up --build
```

---

## Quick Reference

### Endpoint cheat sheet

| Endpoint | Method | Description |
|----------|--------|-------------|
| **Cell CRUD** | | |
| `/honeycomb/models` | GET | List discovered cell names |
| `/honeycomb/models/{cell}` | GET | Cell metadata and fields |
| `/honeycomb/models/{cell}/items` | GET | List all items in cell |
| `/honeycomb/models/{cell}/items` | POST | Create item |
| `/honeycomb/models/{cell}/items/{id}` | GET | Get item by ID |
| `/honeycomb/models/{cell}/items/{id}` | PUT | Update item |
| `/honeycomb/models/{cell}/items/{id}` | DELETE | Delete item |
| **Service Cells** | | |
| `/honeycomb/service/{cell}/{method}` | * | Invoke service cell method |
| `/honeycomb/service/{cell}/{method}/{id}` | * | Invoke with ID param |
| **Shared Methods** | | |
| `/honeycomb/shared/{method}` | POST | Invoke a shared method |
| `/honeycomb/shared/batch` | POST | Batch invoke multiple methods |
| `/honeycomb/shared/async/{method}` | POST | Async fire-and-forget |
| `/honeycomb/shared/methods` | GET | List all shared methods |
| `/honeycomb/shared/methods/by-cell` | GET | Shared methods by cell |
| `/honeycomb/shared/methods/stub` | GET | Generate stub code |
| **Cell Admin** | | |
| `/honeycomb/cells` | GET | Cell server status |
| `/honeycomb/cells/{cell}` | GET | Single cell status |
| `/honeycomb/cells/{cell}/start` | POST | Start cell server |
| `/honeycomb/cells/{cell}/stop` | POST | Stop cell server |
| `/honeycomb/cells/{cell}/restart` | POST | Restart cell server |
| **Cell Addresses** | | |
| `/cells/addresses` | GET | List all addresses |
| `/cells/addresses/{id}` | GET | Get address by ID |
| `/cells/addresses` | POST | Register address |
| `/cells/addresses/{id}` | PUT | Update address |
| `/cells/addresses/{id}` | DELETE | Delete address |
| `/cells/{name}/addresses` | GET | Addresses for a cell |
| **Inter-Cell** | | |
| `/cells/{from}/invoke/{to}/shared/{method}` | POST | Cross-cell shared invoke |
| `/cells/{from}/forward/{to}` | * | Forward request |
| **Versioning** *(v1.4.2+)* | | |
| `/honeycomb/versioning/splits` | GET | All traffic splits |
| `/honeycomb/versioning/splits/{cell}` | GET | Traffic split for cell |
| `/honeycomb/versioning/splits/{cell}` | PUT | Update traffic split |
| `/honeycomb/versioning/cells/{cell}/versions` | GET | List cell versions |
| `/honeycomb/versioning/cells/{cell}/promote/{ver}` | POST | Promote version to 100% |
| **Contracts** *(v1.4.3+)* | | |
| `/honeycomb/contracts` | GET | List all contracts |
| `/honeycomb/contracts/{method}` | GET | Contract for a method |
| `/honeycomb/contracts/export` | GET | Export all contracts |
| `/honeycomb/contracts/verify` | POST | Verify contracts |
| `/honeycomb/contracts/verify/live` | POST | Live-verify contracts |
| **Tenants** *(v1.4.3+)* | | |
| `/honeycomb/tenants/current` | GET | Current tenant |
| `/honeycomb/tenants/allowed` | GET | Allowed tenants |
| `/honeycomb/tenants/config` | GET | Tenant config |
| **Locking** *(v1.4.2+)* | | |
| `/honeycomb/locking/acquire` | POST | Acquire a lock |
| `/honeycomb/locking/release` | POST | Release a lock |
| `/honeycomb/locking/status` | GET | Lock status |
| `/honeycomb/locking/leader` | GET | Leader info |
| `/honeycomb/locking/leader/relinquish` | POST | Relinquish leadership |
| **Events** *(v1.3+)* | | |
| `/honeycomb/events/stream` | GET | SSE stream (all events) |
| `/honeycomb/events/stream/{topic}` | GET | SSE stream (filtered) |
| `/honeycomb/events/publish` | POST | Publish an event |
| **Metrics & Cache** | | |
| `/honeycomb/metrics/cells` | GET | Per-cell request counts |
| `/honeycomb/metrics/shared-cache` | GET | Cache stats |
| `/honeycomb/metrics/shared-cache/refresh` | POST | Refresh cache |
| `/honeycomb/metrics/shared-cache` | DELETE | Invalidate all cache |
| `/honeycomb/metrics/shared-cache/{method}` | DELETE | Invalidate by method |
| **Admin** | | |
| `/honeycomb/admin` | GET | Admin HTML dashboard |
| `/honeycomb/admin/shared/methods` | GET | Shared methods metadata |
| `/honeycomb/admin/shared/circuit-breakers` | GET | Circuit breaker states |
| `/honeycomb/admin/shared/circuit-breakers/{m}/{v}` | GET | Specific CB |
| `/honeycomb/admin/shared/circuit-breakers/{m}/{v}/reset` | POST | Reset CB |
| `/honeycomb/admin/shared/cache` | GET | Cache admin info |
| `/honeycomb/admin/cache/cluster` | GET | Cache cluster info |
| `/honeycomb/admin/cache/invalidate` | POST | Invalidate cache |
| `/honeycomb/admin/cache/sync` | POST | Force cache sync |
| **Audit** | | |
| `/honeycomb/audit` | GET | Recent audit entries |
| **Swagger / OpenAPI** | | |
| `/honeycomb/swagger-ui.html` | GET | Swagger UI |
| `/honeycomb/api-docs` | GET | OpenAPI JSON |
| `/honeycomb/swagger/{cell}` | GET | Per-cell OpenAPI |
| **Actuator** | | |
| `/honeycomb/actuator/health` | GET | Health check |
| `/honeycomb/actuator/info` | GET | App info |
| `/honeycomb/actuator/prometheus` | GET | Prometheus metrics |
| `/honeycomb/actuator/metrics` | GET | Micrometer metrics |

> **API versioning:** When `honeycomb.api.versioning-enabled=true` (default), all endpoints are also available under `/v1/...` (e.g., `/v1/honeycomb/models`).

### Key annotations

| Annotation | Target | Since | Purpose |
|------------|--------|-------|---------|
| `@Cell` | Class | 1.0 | Mark as a Honeycomb cell (optional `port` for dedicated server) |
| `@Cell("name")` | Class | 1.0 | Explicit cell name |
| `@Sharedwall` | Interface/Method | 1.0 | Expose method(s) for shared invocation |
| `@Sharedwall(value, version, allowedFrom)` | Method | 1.0 | Named + versioned + access-controlled |
| `@MethodType(MethodOp.READ)` | Method | 1.0 | Expose as service-cell READ operation |
| `@MethodType(MethodOp.CREATE)` | Method | 1.0 | Expose as service-cell CREATE operation |
| `@CellVersion("v2")` | Class | 1.4.2 | Version tag for blue-green/canary deployments |
| `@DependsOnCell({"CellA"})` | Class | 1.5.0 | Declare cell dependencies (validated at startup) |
| `@HoneycombTest` | Class | 1.4.3 | Test slice: boots framework with in-memory defaults |

### Key headers

| Header | Direction | Purpose |
|--------|-----------|---------|
| `X-API-Key` | Request | API-key authentication |
| `Authorization` | Request | JWT Bearer token |
| `X-Request-Id` | Both | Request correlation ID (auto-generated) |
| `X-From-Cell` | Request | Identifies calling cell for `allowedFrom` checks |
| `X-Shared-Version` | Request | Target shared method version |
| `X-Tenant-Id` | Request | Tenant identifier (when multi-tenancy enabled) |
| `X-Cell-Version` | Request | Request specific cell version |
| `X-API-Version` | Response | Current API version |
| `Idempotency-Key` | Request | Idempotent request key |

### WebFilter execution order

| Order | Filter | Purpose |
|---|---|---|
| `HIGHEST - 0` | RequestIdFilter | Ensures `X-Request-Id` on every request |
| `HIGHEST + 5` | MtlsAuthFilter | Mutual TLS validation |
| `HIGHEST + 5` | ApiVersionFilter | Rewrites `/v1/...` paths, sets version header |
| `HIGHEST + 6` | HttpAuditFilter | HTTP-level audit logging |
| `HIGHEST + 10` | ApiKeyAuthFilter | API-key authentication |
| `HIGHEST + 20` | RateLimitFilter | Per-IP and per-tenant rate limiting |
| `LOWEST - 10` | JwtCellAccessFilter | Per-cell/shared-method JWT role enforcement |
| `LOWEST - 10` | RequestMetricsFilter | Per-cell request counts & latency metrics |

---

## Configuration Reference

Complete reference of all `honeycomb.*` properties:

```yaml
honeycomb:

  # --- Disabled operations ---
  disabled-operations:                             # Map<String, List<String>>
    "__all__": [delete]                            # Global wildcard keys: *, __all__, ALL, 0
    OrderCell: [create]

  # --- Security ---
  security:
    require-auth: false
    cors-allowed-origins: []
    api-keys:
      enabled: false
      header: "X-API-Key"
      keys: {}                                     # Map<name, key-value>
      per-cell: {}                                 # Map<cell, List<key-name>>
    jwt:
      enabled: false
      issuer-uri: null
      jwk-set-uri: null
      audience: null
      roles-claim: "roles"
      role-prefix: "ROLE_"
      scopes-claim: "scp"
      scope-prefix: "SCOPE_"
      shared-roles-claim: "shared_roles"
      shared-role-prefix: "ROLE_"
      default-roles: []
      per-cell-roles: {}
      per-cell-operation-roles: {}
      shared-method-roles: {}
    mtls:
      enabled: false
      require-client-cert: false
      allowed-subjects: []

  # --- Rate Limiter ---
  rate-limiter:
    enabled: true
    tenant-aware: false                            # since 1.5.0
    defaults:
      limit-for-period: 50
      refresh-period: 1s
      timeout: 0ms
    per-cell: {}                                   # Map<cell, RateLimitConfig>
    per-tenant: {}                                 # Map<tenant, RateLimitConfig>

  # --- API Versioning (since 1.5.0) ---
  api:
    versioning-enabled: true
    current-version: "v1"
    legacy-paths-enabled: true
    version-header: "X-API-Version"

  # --- OpenTelemetry Tracing (since 1.5.0) ---
  tracing:
    enabled: true
    otlp-endpoint: "http://localhost:4318/v1/traces"
    sampling-probability: 1.0
    propagate-w3c: true
    enrich-spans: true
    service-name: "honeycomb"

  # --- Vault Secrets (since 1.5.0) ---
  secrets:
    enabled: false
    provider: "vault"
    vault-path: "secret/honeycomb"
    fail-on-missing: false
    refresh-interval: "0"                          # "0" = disabled
    vault-role: "honeycomb"

  # --- Adaptive Circuit Breaker (since 1.5.0) ---
  circuit-breaker:
    adaptive-enabled: false
    initial-failure-rate-threshold: 50.0
    min-failure-rate-threshold: 20.0
    max-failure-rate-threshold: 80.0
    evaluation-window-size: 10
    adjustment-interval-seconds: 60
    wait-duration-in-open-state-seconds: 30
    sliding-window-size: 10
    permitted-calls-in-half-open: 5
    slow-call-duration-threshold-ms: 5000
    slow-call-rate-threshold: 80.0

  # --- Multi-Tenancy (since 1.4.3) ---
  tenant:
    enabled: false
    header-name: "X-Tenant-Id"
    default-tenant: ""
    allowed-tenants: []
    enforce-header: true
    storage-key-template: "honeycomb:tenant:{tenant}:cell"
    scope-metrics: true

  # --- Cell Versioning (since 1.4.2) ---
  versioning:
    enabled: false
    default-version: "v1"
    version-header: "X-Cell-Version"
    traffic-split: {}                              # Map<cell, Map<version, percentage>>

  # --- Contract Testing (since 1.4.3) ---
  contracts:
    enabled: false
    output-dir: "target/honeycomb-contracts"
    format: "spring-cloud-contract"
    include-packages: []
    verify-on-startup: false
    publish-stubs: false

  # --- Distributed Locking (since 1.4.2) ---
  locking:
    enabled: false
    type: "redis"
    key-prefix: "honeycomb:lock:"
    default-ttl: 30s
    retry-delay: 100ms
    max-retries: 3
    leader-election:
      enabled: false
      key: "honeycomb:leader"
      ttl: 30s
      renewal-interval: 10s

  # --- Shared Method Cache ---
  shared:
    cache:
      type: "local"                                # local | redis
      redis-key-prefix: "honeycomb:shared-cache"
      redis-invalidate-channel: "honeycomb:cache:invalidate"
      redis-ttl-seconds: 120
      sync-enabled: true
      warmup-enabled: true
    methods:
      schema-validation-enabled: false
      policies:                                    # Map<methodName, MethodPolicy>
        default:
          timeout: 5s
          retry-count: 1
          retry-backoff: 200ms
          circuit-breaker-enabled: true

  # --- Idempotency ---
  idempotency:
    enabled: false
    header: "Idempotency-Key"
    ttl-seconds: 300
    store: "memory"                                # memory | redis
    key-prefix: "honeycomb:idempotency"

  # --- Validation ---
  validation:
    enabled: false
    schema-dir: "schemas"
    per-cell: {}
    fail-on-missing-schema: false

  # --- Audit ---
  audit:
    max-entries: 500

  # --- Routing ---
  routing:
    default-policy: "round-robin"
    per-cell-policy: {}
    weights: {}
    # Policies: one, random, round-robin, weighted, least-latency, circuit-aware, all

  # --- Autoscale ---
  autoscale:
    enabled: false
    evaluation-interval: 30s
    scale-up-rps: 5.0
    scale-down-rps: 0.5
    per-cell-enabled: {}
    per-cell-scale-up-rps: {}
    per-cell-scale-down-rps: {}

  # --- Events ---
  events:
    enabled: true
    transport: "memory"                            # memory | redis
    default-topic: "honeycomb.events"
    buffer-size: 256
```

---

## Authoring Guide

When adding a new tutorial:

1. Create a new markdown file in this folder (e.g., `tutorials/16-custom-storage.md`)
2. Include clear prerequisites and expected outputs
3. Provide copy-paste command blocks
4. Add a troubleshooting section for common failures
5. Link to it from the **Table of Contents** above
