# Honeycomb

`honeycomb` is a small Spring Boot project that discovers domain model classes annotated with `@Cell` and exposes them via REST as generic "data point" descriptions. It also demonstrates per-cell runtimes, inter-cell shared-method invocation, and simple configurable CRUD controls.

How it works
- Annotate any class with `@com.example.honeycomb.annotations.Cell`.
- Mark methods you want to expose cross-cell with `@com.example.honeycomb.annotations.Sharedwall`.
- On startup the `DomainRegistry` scans the classpath and application context for `@Cell` types and records their metadata.
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
- Shared methods: annotate methods with `@Sharedwall` to expose them to other cells at `/honeycomb/shared/{name}`. Use the `allowedFrom` attribute to restrict which caller cell names may invoke the method.
- Configurable CRUD: disable create/read/update/delete per-domain or globally via `honeycomb.disabled-operations` in `application.yml`.

Health endpoint
- Each domain server exposes a health endpoint at `/honeycomb/health` which reports `status`, `port`, and `domain` (and domain metadata).

Configuration examples
- `src/main/resources/application.yml` contains example settings:

```yaml
honeycomb:
  disabled-operations:
    "*":
      - delete
    SampleModel:
      - create

shared:
  caller-header: "X-From-Cell"
```

Clients invoking shared methods should set the caller header (default `X-From-Cell`) to identify themselves. Shared methods can also declare `allowedFrom` to limit callers.

Running multiple domain instances
- A simple helper script is provided at `scripts/run-multi-domains.sh` to launch multiple JVM instances with per-domain overrides:

```sh
./scripts/run-multi-domains.sh SampleModel=9090
```

This will run the packaged JAR with `--domain.ports.SampleModel=9090`.

Notes
- The discovery defaults to scanning under `com.example` — adjust `DomainRegistry` if you want to scan particular packages.
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
