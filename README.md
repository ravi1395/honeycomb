# Honeycomb

`honeycomb` is a small Spring Boot project that demonstrates a hexagonal-style component which discovers domain model classes annotated with `@Domain` and exposes them via REST as generic "data point" descriptions.

How it works
- Annotate any class with `@com.example.honeycomb.annotations.Domain`.
- On startup the `DomainRegistry` scans the classpath and application context for `@Domain` types and records their metadata.
- The controller exposes endpoints:
  - `GET /honeycomb/models` — list discovered model names
  - `GET /honeycomb/models/{name}` — describe fields and class name for the given model

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
- Per-domain servers: the application can start extra HTTP servers bound to domain-specific ports. These servers are restricted to the `/honeycomb/**` routes and are useful to run domain-specific endpoints on their own port.

Health endpoint
- Each domain server exposes a health endpoint at `/honeycomb/health` which reports `status`, `port`, and `domain` (and domain metadata).

Running multiple domain instances
- A simple helper script is provided at `scripts/run-multi-domains.sh` to launch multiple JVM instances with per-domain overrides:

```sh
./scripts/run-multi-domains.sh SampleModel=9090
```

This will run the packaged JAR with `--domain.ports.SampleModel=9090`.

Notes
- The discovery defaults to scanning under `com.example` — adjust `DomainRegistry` if you want to scan particular packages.
- The registry currently returns field names and types; you can extend it to instantiate example objects or map arbitrary models to a common "data point" structure.
