# Honeycomb Tutorials

Comprehensive, step-by-step guides for building, running, and managing microservices with the Honeycomb framework.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Tutorial 1 — Build a New App from Scratch](#tutorial-1--build-a-new-app-from-scratch)
3. [Tutorial 2 — Building Microservices with Honeycomb](#tutorial-2--building-microservices-with-honeycomb)
4. [Tutorial 3 — Managing Microservices at Runtime](#tutorial-3--managing-microservices-at-runtime)
5. [Tutorial 4 — Multi-Instance & Deployment](#tutorial-4--multi-instance--deployment)
6. [Tutorial 5 — Observability, Security & Production Hardening](#tutorial-5--observability-security--production-hardening)
7. [Quick Reference](#quick-reference)
8. [Authoring Guide](#authoring-guide)

---

## Prerequisites

| Requirement | Version |
|-------------|---------|
| Java (JDK) | 21+ |
| Maven | 3.9+ |
| Docker | 24+ (optional, for container tutorials) |
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
mvn clean install     # installs honeycomb-core 1.2.0 and honeycomb (starter) 1.2.0
```

This makes the following artifacts available in `~/.m2/repository`:

| Module | Coordinates |
|--------|-------------|
| Core library | `com.honeycomb:honeycomb-core:1.2.0` |
| Starter (auto-config) | `com.honeycomb:honeycomb:1.2.0` |

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
  </properties>

  <dependencies>
    <!-- Honeycomb starter (auto-configures everything) -->
    <dependency>
      <groupId>com.honeycomb</groupId>
      <artifactId>honeycomb</artifactId>
      <version>1.2.0</version>
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

A **cell** is any class annotated with `@Cell`. Honeycomb discovers it at runtime, provides CRUD endpoints, and optionally starts a dedicated HTTP server on the declared port.

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

---

## Tutorial 2 — Building Microservices with Honeycomb

This tutorial builds on Tutorial 1 and adds multiple cells, shared methods for cross-cell communication, service-style cells, and inter-cell routing.

### 2.1 Add more cells

Create `src/main/java/com/myorg/app/cells/OrderCell.java`:

```java
package com.myorg.app.cells;

import com.honeycomb.core.annotations.Cell;

@Cell(port = 9092)
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

    // getters + setters …
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

    // getters + setters …
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
        // In production, query a real data store
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

Honeycomb 1.2 supports invoking multiple shared methods at once, or firing asynchronously:

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

Now any call to `checkStock` without a valid `productId` string is rejected with a 400 before your handler runs.

### 2.7 Idempotency

Use the `Idempotency-Key` header to make shared-method calls safe to retry:

```bash
curl -X POST -H "Content-Type: application/json" \
     -H "X-API-Key: my-secret-key" \
     -H "Idempotency-Key: order-123-reserve" \
     -d '{"productId": "p-1", "quantity": 5}' \
     http://localhost:8080/honeycomb/shared/reserveStock
```

Replying the exact same request with the same key returns the cached result without re-executing the handler.

Enable in config:

```yaml
honeycomb:
  idempotency:
    enabled: true
    store: memory       # memory | redis
    ttl-seconds: 300
```

### 2.8 Disable specific CRUD operations

You can selectively disable operations globally or per cell:

```yaml
honeycomb:
  disabled-operations:
    "__all__":
      - delete           # Disable DELETE on all cells by default
    OrderCell:
      - create           # Disable manual order creation (use shared method instead)
```

---

## Tutorial 3 — Managing Microservices at Runtime

Once your cells are running, Honeycomb provides built-in tools for runtime management, monitoring, and troubleshooting.

### 3.1 Cell discovery and addresses

```bash
# List all known cell addresses (includes per-cell server ports)
curl -H "X-API-Key: my-secret-key" http://localhost:8080/cells/addresses

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

### 3.3 Admin diagnostic endpoints (1.2+)

```bash
# List all registered shared methods
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/admin/shared-methods

# View circuit breaker states
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/admin/circuit-breakers

# Force-reset a tripped circuit breaker
curl -X POST -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/admin/circuit-breakers/reset

# Cache diagnostics
curl -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/metrics/shared-cache

# Invalidate a specific method's cache
curl -X DELETE -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/metrics/shared-cache/checkStock

# Force full cache refresh
curl -X POST -H "X-API-Key: my-secret-key" \
     http://localhost:8080/honeycomb/metrics/shared-cache/refresh
```

### 3.4 Request metrics and Prometheus

Honeycomb exposes per-cell and per-method counters/timers:

```bash
# Prometheus scrape endpoint
curl http://localhost:8080/actuator/prometheus | grep honeycomb

# Key metrics:
#   honeycomb_shared_invoke_total{method="checkStock", outcome="success"}
#   honeycomb_shared_invoke_duration_seconds{method="checkStock", quantile="0.95"}
#   honeycomb_shared_cache_requests_total{method="checkStock", outcome="hit"}
```

### 3.5 Routing policies for inter-cell calls

When you have multiple instances of the same cell (or cell addresses), configure how Honeycomb routes:

```yaml
honeycomb:
  routing:
    default-policy: round-robin    # Options: all, one, random,
                                   # round-robin, weighted,
                                   # least-latency, circuit-aware
```

### 3.6 Audit log and WebSocket events

```bash
# Fetch recent audit entries
curl -H "X-API-Key: my-secret-key" http://localhost:8080/honeycomb/audit

# Stream events in real time (WebSocket)
# Connect to: ws://localhost:8080/honeycomb/ws/events
```

### 3.7 Rate limiting

Enable globally or override per cell:

```yaml
honeycomb:
  rate-limiter:
    enabled: true
    # per-cell overrides
    # ProductCell:
    #   limit-for-period: 50
    #   limit-refresh-period: 1s
```

### 3.8 Autoscaling decisions

Honeycomb can generate scale-up/down signals based on request rates:

```yaml
honeycomb:
  autoscale:
    enabled: true
    # Per-cell thresholds
    # ProductCell:
    #   scale-up-threshold: 100
    #   scale-down-threshold: 10
```

```bash
# Check autoscale recommendations
curl -H "X-API-Key: my-secret-key" http://localhost:8080/honeycomb/autoscale
```

---

## Tutorial 4 — Multi-Instance & Deployment

### 4.1 Local multi-instance run (shell script)

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

### 4.2 Docker Compose multi-instance

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

### 4.3 Systemd deployment (Linux)

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

---

## Tutorial 5 — Observability, Security & Production Hardening

### 5.1 Distributed tracing (Observation API)

Honeycomb 1.2 creates Observation spans for every shared-method dispatch. To export traces, add a tracer (e.g., Zipkin or OpenTelemetry) to your classpath:

```xml
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-zipkin</artifactId>
</dependency>
```

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

Every call to `/honeycomb/shared/{method}` and `/honeycomb/shared/batch` automatically produces a span with method name, version, outcome, and caller cell tags.

### 5.2 API-key + JWT security

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
        "*": ["readonly"]
    # Optional JWT validation
    # jwt:
    #   enabled: true
    #   issuer-uri: https://auth.myorg.com
```

### 5.3 Production profile

For hardened defaults (tighter timeouts, security, autoscale, metrics):

```bash
SPRING_PROFILES_ACTIVE=prod mvn spring-boot:run
```

### 5.4 Health checks

```bash
curl http://localhost:8080/actuator/health
```

Honeycomb adds a reactive health indicator for cell servers so container orchestrators can detect unhealthy instances.

---

## Quick Reference

### Endpoint cheat sheet

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/honeycomb/models` | GET | List discovered cell names |
| `/honeycomb/models/{cell}` | GET | Cell metadata and fields |
| `/honeycomb/models/{cell}/items` | GET/POST | List / create items |
| `/honeycomb/models/{cell}/items/{id}` | GET/PUT/DELETE | Read / update / delete item |
| `/honeycomb/service/{cell}/{method}` | GET/POST | Service-style cell methods |
| `/honeycomb/shared/{method}` | POST | Invoke a shared method |
| `/honeycomb/shared/batch` | POST | Batch invoke multiple methods |
| `/honeycomb/shared/async/{method}` | POST | Async fire-and-forget |
| `/honeycomb/cells` | GET | Cell server status |
| `/honeycomb/cells/{cell}/start` | POST | Start a cell server |
| `/honeycomb/cells/{cell}/stop` | POST | Stop a cell server |
| `/cells/addresses` | GET | List cell addresses |
| `/honeycomb/admin/shared-methods` | GET | Registered shared methods |
| `/honeycomb/admin/circuit-breakers` | GET | Circuit breaker states |
| `/honeycomb/metrics/shared-cache` | GET | Cache stats |
| `/honeycomb/audit` | GET | Recent audit entries |
| `/honeycomb/ws/events` | WS | Live event stream |
| `/honeycomb/autoscale` | GET | Autoscale recommendations |
| `/actuator/prometheus` | GET | Prometheus metrics |
| `/actuator/health` | GET | Health check |

### Key annotations

| Annotation | Target | Purpose |
|------------|--------|---------|
| `@Cell` | Class | Mark as a Honeycomb cell (optional `port` for dedicated server) |
| `@Cell("name")` | Class | Explicit cell name |
| `@Sharedwall` | Interface/Method | Expose method(s) for shared invocation |
| `@Sharedwall(value, version, allowedFrom)` | Method | Named + versioned + access-controlled |
| `@MethodType(MethodOp.READ)` | Method | Expose as service-cell READ operation |
| `@MethodType(MethodOp.CREATE)` | Method | Expose as service-cell CREATE operation |

### Configuration quick reference

```yaml
honeycomb:
  security:
    api-keys: { enabled: true, header: "X-API-Key", keys: { admin: "key" } }
  routing:
    default-policy: round-robin
  rate-limiter:
    enabled: true
  autoscale:
    enabled: true
  validation:
    enabled: true
    schema-dir: schemas
  idempotency:
    enabled: true
    store: memory
    ttl-seconds: 300
  shared:
    cache:
      enabled: true
      cache-refresh-ms: 60000
  disabled-operations:
    "__all__": [delete]
```

---

## Authoring Guide

When adding a new tutorial:

1. Create a new markdown file in this folder (e.g., `tutorials/05-custom-storage.md`)
2. Include clear prerequisites and expected outputs
3. Provide copy-paste command blocks
4. Add a troubleshooting section for common failures
5. Link to it from the **Table of Contents** above
