# Postman collection and environment for Honeycomb Example

This folder contains a Postman collection and environment file for the `honeycomb-example` app.

Files
- `honeycomb-example.postman_collection.json` — collection with common endpoints
- `honeycomb-example.postman_environment.dev.json` — dev environment (`baseUrl`, `apiKey`)
- `honeycomb-example.postman_environment.prod.json` — prod environment (`baseUrl`, `apiKey`)

Import
- In Postman: File → Import → select the collection and one environment JSON file.

Defaults
- `baseUrl` (dev): `http://localhost:8080`
- `apiKey` (dev): `admin-key`

Service endpoints
- Service endpoints are exposed via `@Cell` + `@MethodType` (including interface-declared annotations).
- Routes follow `/honeycomb/service/{cell}/{method}` and optional `/honeycomb/service/{cell}/{method}/{id}`.

Examples

List cells (sends `X-API-Key`):

```bash
curl -i -H "X-API-Key: admin-key" http://localhost:8080/honeycomb/cells
```

Create an address:

```bash
curl -i -X POST -H "Content-Type: application/json" -H "X-API-Key: admin-key" \
  -d '{"host":"127.0.0.1","port":8080,"protocol":"http"}' \
  http://localhost:8080/cells/addresses
```

If you change the API key in the app, update the `apiKey` value in the imported Postman environment.
