package com.example.honeycomb.util;

public final class HoneycombConstants {
    private HoneycombConstants() {}

    public static final class Headers {
        private Headers() {}
        public static final String REQUEST_ID = "X-Request-Id";
        public static final String FROM_CELL = "X-From-Cell";
        public static final String API_KEY = "X-API-Key";
        public static final String AUTHORIZATION = "Authorization";
        public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    }

    public static final class Paths {
        private Paths() {}
        public static final String HONEYCOMB_BASE = "/honeycomb";
        public static final String HONEYCOMB_CELLS = "/honeycomb/cells";
        public static final String HONEYCOMB_METRICS = "/honeycomb/metrics";
        public static final String HONEYCOMB_AUDIT = "/honeycomb/audit";
        public static final String HONEYCOMB_ADMIN = "/honeycomb/admin";
        public static final String HONEYCOMB_WS_EVENTS = "/honeycomb/ws/events";
        public static final String HONEYCOMB_SHARED = "/honeycomb/shared";
        public static final String HONEYCOMB_ACTUATOR = "/honeycomb/actuator";
        public static final String ACTUATOR_BASE = "/actuator";
        public static final String HONEYCOMB_SWAGGER_UI = "/honeycomb/swagger-ui";
        public static final String HONEYCOMB_API_DOCS = "/honeycomb/api-docs";
        public static final String HONEYCOMB_SWAGGER = "/honeycomb/swagger";
        public static final String HONEYCOMB_MODELS = "/honeycomb/models";
        public static final String CELLS_BASE = "/cells";
        public static final String CELLS_ADDRESSES = "/cells/addresses";
        public static final String ADDRESSES = "addresses";
        public static final String ADDRESSES_PATH = "/addresses";
        public static final String MODELS = "models";
        public static final String CELLS = "cells";
        public static final String ITEMS = "items";
        public static final String SHARED = "shared";
        public static final String INVOKE = "invoke";
        public static final String FORWARD = "forward";
        public static final String CELLS_INVOKE_SHARED = "/{from}/invoke/{to}/shared/{methodName}";
        public static final String CELLS_FORWARD = "/{from}/forward/{to}";
        public static final String NAME_VAR = "{name}";
        public static final String NAME_PATH = "/{name}";
        public static final String NAME_START = "/{name}/start";
        public static final String NAME_STOP = "/{name}/stop";
        public static final String NAME_RESTART = "/{name}/restart";
        public static final String ID_PATH = "/{id}";
    }

    public static final class Params {
        private Params() {}
        public static final String POLICY = "policy";
        public static final String METHOD = "method";
        public static final String PATH = "path";
        public static final String LIMIT = "limit";
    }

    public static final class Schemes {
        private Schemes() {}
        public static final String HTTP = "http://";
    }

    public static final class Hosts {
        private Hosts() {}
        public static final String LOCALHOST = "localhost";
    }

    public static final class Roles {
        private Roles() {}
        public static final String ACTUATOR = "ACTUATOR";
        public static final String SHARED_INVOKER = "SHARED_INVOCER";
        public static final String API_KEY = "ROLE_API_KEY";
    }

    public static final class HttpMethods {
        private HttpMethods() {}
        public static final String GET = "GET";
        public static final String POST = "POST";
        public static final String PUT = "PUT";
        public static final String DELETE = "DELETE";
    }

    public static final class Ops {
        private Ops() {}
        public static final String CREATE = "create";
        public static final String READ = "read";
        public static final String UPDATE = "update";
        public static final String DELETE = "delete";
        public static final String SHARED = "shared";
        public static final String UNKNOWN = "unknown";
    }

    public static final class ErrorKeys {
        private ErrorKeys() {}
        public static final String MISSING_API_KEY = "missing-api-key";
        public static final String INVALID_API_KEY = "invalid-api-key";
        public static final String CELL_ACCESS_DENIED = "cell-access-denied";
        public static final String MISSING_CLIENT_CERT = "missing-client-cert";
        public static final String CLIENT_CERT_NOT_ALLOWED = "client-cert-not-allowed";
        public static final String INSUFFICIENT_ROLES = "insufficient-roles";
        public static final String NO_CONFIGURED_PORT = "no-configured-port";
        public static final String NO_SHARED_METHOD = "no-shared-method";
        public static final String ACCESS_DENIED = "access-denied";
        public static final String JSON_DESERIALIZE_ERROR = "json-deserialize-error";
        public static final String INVOCATION_ERROR = "invocation-error";
    }

    public static final class JsonKeys {
        private JsonKeys() {}
        public static final String ERROR = "error";
        public static final String ID = "id";
        public static final String STATUS = "status";
        public static final String CONTENT_TYPE = "contentType";
        public static final String BODY = "body";
        public static final String RESULT = "result";
        public static final String METHOD = "method";
        public static final String TARGETS = "targets";
        public static final String PATH = "path";
        public static final String REASON = "reason";
        public static final String CLASS_NAME = "className";
        public static final String NAME = "name";
        public static final String TYPE = "type";
        public static final String FIELDS = "fields";
        public static final String SHARED_METHODS = "sharedMethods";
        public static final String PORT = "port";
    }

    public static final class KeyPrefixes {
        private KeyPrefixes() {}
        public static final String CELL = "honeycomb:cell";
        public static final String IDEMPOTENCY = "honeycomb:idempotency";
    }

    public static final class Audit {
        private Audit() {}
        public static final String ACTOR_SYSTEM = "system";
        public static final String ACTION_ITEM_CREATE = "item.create";
        public static final String ACTION_ITEM_UPDATE = "item.update";
        public static final String ACTION_ITEM_DELETE = "item.delete";
        public static final String ACTION_CELL_START = "cell.start";
        public static final String ACTION_CELL_STOP = "cell.stop";
        public static final String ACTION_CELL_RESTART = "cell.restart";
        public static final String ACTION_CELL_INVOKE = "cell.invoke";
        public static final String ACTION_CELL_FORWARD = "cell.forward";
    }

    public static final class Status {
        private Status() {}
        public static final String OK = "ok";
        public static final String DENIED = "denied";
        public static final String ERROR = "error";
        public static final String NOOP = "noop";
        public static final String NOT_FOUND = "not-found";
        public static final String NO_TARGETS = "no-targets";
    }

    public static final class Messages {
        private Messages() {}
        public static final String STARTED = "started";
        public static final String STOPPED = "stopped";
        public static final String ALREADY_STOPPED = "already-stopped";
        public static final String RESTARTED = "restarted";
        public static final String DISABLED = "disabled";
        public static final String UNKNOWN = "unknown";
        public static final String EMPTY = "";
        public static final String CELL_ADDRESS_READ_ONLY = "Cell address registry is discovery-based and read-only";
        public static final String CELL_ADDRESS_DISCOVERY_BASED = "Cell address registry is discovery-based";
        public static final String JWT_MISSING_ISSUER_OR_JWK = "JWT enabled but no issuer-uri or jwk-set-uri configured";
        public static final String AUTO_SCALE_START = "autoscale start cell={} rate={} started={}";
        public static final String AUTO_SCALE_STOP = "autoscale stop cell={} rate={} stopped={}";
        public static final String AUDIT_LOG = "audit action={} cell={} status={} actor={} details={}";
        public static final String SERVER_NO_PORT = "No configured port for cell '{}'";
        public static final String SERVER_ALREADY_RUNNING = "Server for cell '{}' already running on port {}";
        public static final String SERVER_STARTED = "Started cell server '{}' on port {}";
        public static final String SERVER_MGMT_STARTED = "Started management server for cell '{}' on port {}";
        public static final String SERVER_MGMT_FAILED = "Failed to start management server for cell {}: {}";
        public static final String SERVER_START_FAILED = "Failed to start server for cell {}: {}";
        public static final String SERVER_SHUTDOWN_ERROR = "Error shutting down server {}: {}";
        public static final String SERVER_MGMT_SHUTDOWN_ERROR = "Error shutting down mgmt server {}: {}";
        public static final String SERVER_SHUTDOWN = "Shutting down cell server '{}'";
        public static final String SCHEMA_MISSING = "schema-missing: ";
        public static final String SCHEMA_VALIDATION_FAILED = "schema-validation-failed: ";
        public static final String CELL_DATASTORE_MISSING = "No CellDataStore configured for type: ";
        public static final String DISPATCH_SHARED_DEBUG = "Dispatch shared method={}, headers={}, bodyMono={}";
        public static final String SHARED_METHOD_NOT_FOUND = "No shared method '{}' found locally";
        public static final String INVOKE_CANDIDATE = "Invoking candidate {}.{}";
        public static final String ACCESS_DENIED_INVOKE = "Access denied invoking {}.{} from caller={}";
        public static final String JSON_DESERIALIZE_ERROR = "JSON deserialize error for {}.{}: {}";
        public static final String INVOCATION_SUCCESS = "Invocation {}.{} succeeded";
        public static final String INVOCATION_ERROR = "Invocation error on {}.{}: {}";
        public static final String READ_DISABLED = "Read operation disabled for {}";
        public static final String GET_DISABLED = "Get operation disabled for {}/{}";
        public static final String CREATE_DISABLED = "Create operation disabled for {}";
        public static final String UPDATE_DISABLED = "Update operation disabled for {}/{}";
        public static final String DELETE_DISABLED = "Delete operation disabled for {}/{}";
        public static final String CALLER_PREFIX = "caller='";
        public static final String CALLER_NOT_ALLOWED_SUFFIX = "' not allowed";
        public static final String SPACE = " ";
        public static final String LOG_BAD_REQUEST = "Bad request: {}";
        public static final String LOG_VALIDATION_ERROR = "Validation error: {}";
        public static final String LOG_BINDING_ERROR = "Binding error: {}";
        public static final String LOG_CIRCUIT_OPEN = "Circuit breaker open: {}";
        public static final String LOG_RATE_LIMIT = "Rate limit exceeded: {}";
        public static final String LOG_TIMEOUT = "Request timeout: {}";
        public static final String LOG_RESOURCE_NOT_FOUND = "Static resource not found: {}";
        public static final String LOG_UNHANDLED = "Unhandled error: {}";
    }

    public static final class Names {
        private Names() {}
        public static final String MGMT_SUFFIX = "-mgmt";
        public static final String LIMITER_CELL_PREFIX = "cell-";
        public static final String LIMITER_GLOBAL = "global";
        public static final String STORE_MEMORY = "memory";
        public static final String STORE_REDIS = "redis";
        public static final String STORE_HIBERNATE = "hibernate";
        public static final String SEPARATOR_COLON = ":";
        public static final String SEPARATOR_SLASH = "/";
        public static final String SEPARATOR_COMMA = ",";
        public static final String OPEN_BRACE = "{";
        public static final String CLOSE_BRACE = "}";
        public static final String LIST_SEPARATOR = "; ";
    }

    public static final class Prefixes {
        private Prefixes() {}
        public static final String CLASSPATH = "classpath:";
    }

    public static final class Suffixes {
        private Suffixes() {}
        public static final String SCHEMA_JSON = ".schema.json";
    }

    public static final class Regex {
        private Regex() {}
        public static final String SLASH = "/";
        public static final String PROTOCOL_SEPARATOR = "://";
        public static final String HTTP_PREFIX = "^https?://";
        public static final String LEADING_SLASHES = "^/+";
        public static final String TRAILING_SLASHES = "/+$";
    }

    public static final class Patterns {
        private Patterns() {}
        public static final String CLASS_RESOURCE_SUFFIX = "/**/*.class";
    }

    public static final class Persistence {
        private Persistence() {}
        public static final String TABLE_CELL_RECORDS = "cell_records";
        public static final String COL_RECORD_KEY = "record_key";
        public static final String COL_CELL_NAME = "cell_name";
        public static final String COL_ITEM_ID = "item_id";
        public static final String COL_PAYLOAD_JSON = "payload_json";
        public static final String HQL_FIND_BY_CELL = "from CellRecord where cellName = :cell";
        public static final String PARAM_CELL = "cell";
    }

    public static final class Swagger {
        private Swagger() {}
        public static final String TAG_CELL = "Cell";
        public static final String SUMMARY_DESCRIBE = "Describe cell model";
        public static final String SUMMARY_LIST_ITEMS = "List all items in cell";
        public static final String SUMMARY_GET_ITEM = "Get item by ID";
        public static final String SUMMARY_CREATE_ITEM = "Create item";
        public static final String SUMMARY_UPDATE_ITEM = "Update item";
        public static final String SUMMARY_DELETE_ITEM = "Delete item";
        public static final String RESP_200 = "200";
        public static final String RESP_201 = "201";
        public static final String RESP_204 = "204";
        public static final String RESP_404 = "404";
        public static final String RESP_400 = "400";
        public static final String RESP_403 = "403";
        public static final String RESP_405 = "405";
        public static final String RESP_500 = "500";
        public static final String DESC_CELL = "Cell description";
        public static final String DESC_CELL_NOT_FOUND = "Cell not found";
        public static final String DESC_LIST_ITEMS = "List of items";
        public static final String DESC_ITEM_FOUND = "Item found";
        public static final String DESC_ITEM_NOT_FOUND = "Item not found";
        public static final String DESC_ITEM_CREATED = "Item created";
        public static final String DESC_ITEM_UPDATED = "Item updated";
        public static final String DESC_ITEM_DELETED = "Item deleted";
        public static final String INFO_TITLE_PREFIX = "Honeycomb Cell API - ";
        public static final String INFO_TITLE_ALL = "Honeycomb Cell APIs";
        public static final String INFO_VERSION = "1.0";
    }

    public static final class Health {
        private Health() {}
        public static final String LIVENESS_COMPONENT = "cellLiveness";
        public static final String STATUS_UP = "UP";
        public static final String DETAIL_CELL_COUNT = "cellCount";
        public static final String DETAIL_CELLS = "cells";
    }

    public static final class Examples {
        private Examples() {}
        public static final String SHARED_ECHO = "echo";
        public static final String SHARED_CONCAT = "concat";
        public static final String SHARED_SUM_LIST = "sumList";
        public static final String SHARED_BOOM = "boom";
        public static final String SHARED_TEST_CLIENT = "test-client";
        public static final String ECHO_PREFIX = "echo:";
        public static final String RECEIVED_KEYS = "receivedKeys";
        public static final String ORIGINAL = "original";
        public static final String BOOM = "boom";
        public static final String BOOM_EXCEPTION = "boom-exception";
        public static final String SERIALIZATION = "serialization";
    }

    public static final class Docs {
        private Docs() {}
        public static final String TAG_METRICS = "Metrics";
        public static final String TAG_METRICS_DESC = "Per-cell metrics overview";
        public static final String METRICS_CELL_COUNTS = "Per-cell request counts since start";
        public static final String TAG_AUDIT = "Audit";
        public static final String TAG_AUDIT_DESC = "Audit log and event history";
        public static final String AUDIT_LIST = "List recent audit events";
        public static final String TAG_CELL_ADMIN = "Cell Administration";
        public static final String TAG_CELL_ADMIN_DESC = "List and manage cell servers";
        public static final String CELL_ADMIN_LIST = "List all cells with runtime status";
        public static final String CELL_ADMIN_GET = "Get runtime status for a cell";
        public static final String CELL_ADMIN_START = "Start a cell server";
        public static final String CELL_ADMIN_STOP = "Stop a cell server";
        public static final String CELL_ADMIN_RESTART = "Restart a cell server";
        public static final String TAG_CELL_REGISTRY = "Cell Registry";
        public static final String TAG_CELL_REGISTRY_DESC = "CRUD operations for cell models and instances";
        public static final String REGISTRY_LIST_MODELS = "List all registered cell models";
        public static final String REGISTRY_LIST_MODELS_DESC = "List of cell names";
        public static final String REGISTRY_DESCRIBE = "Describe a cell model";
        public static final String REGISTRY_DESCRIBE_DESC = "Returns fields and shared methods for the cell";
        public static final String REGISTRY_LIST_ITEMS = "List all items in a cell";
        public static final String REGISTRY_GET_ITEM = "Get a specific item by ID";
        public static final String REGISTRY_CREATE_ITEM = "Create a new item";
        public static final String REGISTRY_UPDATE_ITEM = "Update an existing item";
        public static final String REGISTRY_DELETE_ITEM = "Delete an item";
        public static final String TAG_SHARED_DISPATCHER = "Shared Method Dispatcher";
        public static final String TAG_SHARED_DISPATCHER_DESC = "Invoke shared methods across cells using @Sharedwall annotations";
        public static final String SHARED_DISPATCH_SUMMARY = "Invoke a shared method";
        public static final String SHARED_DISPATCH_DESC = "Dispatches a call to methods annotated with @Sharedwall in @Cell beans. "
                + "Supports zero, single, or multi-parameter methods with JSON body binding.";
        public static final String SHARED_METHOD_PARAM = "Name or alias of the shared method to invoke";
        public static final String SHARED_OK = "Method invoked successfully";
        public static final String SHARED_FORBIDDEN = "Access denied - caller not in allowedFrom list";
        public static final String SHARED_NOT_FOUND = "Method not found";
        public static final String SHARED_ERROR = "Invocation error";
        public static final String CELL_ADMIN_LIST_DESC = "List of cells";
        public static final String CELL_ADMIN_STATUS_DESC = "Cell status";
        public static final String CELL_ADMIN_NOT_FOUND_DESC = "Cell not found";
        public static final String CELL_ADMIN_STARTED_DESC = "Cell started";
        public static final String CELL_ADMIN_NO_PORT_DESC = "Cell has no configured port";
        public static final String CELL_ADMIN_STOPPED_DESC = "Cell stopped";
        public static final String CELL_ADMIN_RESTARTED_DESC = "Cell restarted";
        public static final String REGISTRY_ITEM_LIST_DESC = "List of items";
        public static final String REGISTRY_ITEM_FOUND_DESC = "Item found";
        public static final String REGISTRY_ITEM_NOT_FOUND_DESC = "Item not found";
        public static final String REGISTRY_ITEM_CREATED_DESC = "Item created";
        public static final String REGISTRY_ITEM_UPDATED_DESC = "Item updated";
        public static final String REGISTRY_ITEM_DELETED_DESC = "Item deleted";
        public static final String REGISTRY_READ_DISABLED_DESC = "Read operation disabled";
        public static final String REGISTRY_CREATE_DISABLED_DESC = "Create operation disabled";
        public static final String REGISTRY_UPDATE_DISABLED_DESC = "Update operation disabled";
        public static final String REGISTRY_DELETE_DISABLED_DESC = "Delete operation disabled";
        public static final String PARAM_CELL_NAME = "Cell name";
        public static final String PARAM_ITEM_ID = "Item ID";
    }

    public static final class ErrorCodes {
        private ErrorCodes() {}
        public static final String INTERNAL_ERROR = "internal-error";
        public static final String BAD_REQUEST = "bad-request";
        public static final String VALIDATION_ERROR = "validation-error";
        public static final String CELL_NOT_FOUND = "cell-not-found";
        public static final String METHOD_NOT_FOUND = "method-not-found";
        public static final String METHOD_ACCESS_DENIED = "method-access-denied";
        public static final String OPERATION_DISABLED = "operation-disabled";
        public static final String ITEM_NOT_FOUND = "item-not-found";
        public static final String ITEM_CREATE_FAILED = "item-create-failed";
        public static final String CIRCUIT_OPEN = "circuit-open";
        public static final String RATE_LIMITED = "rate-limited";
        public static final String TIMEOUT = "timeout";
        public static final String FORWARD_FAILED = "forward-failed";
        public static final String JSON_PARSE_ERROR = "json-parse-error";
    }

    public static final class ErrorMessages {
        private ErrorMessages() {}
        public static final String INTERNAL_ERROR = "An unexpected error occurred";
        public static final String BAD_REQUEST = "Invalid request format or parameters";
        public static final String VALIDATION_ERROR = "Request validation failed";
        public static final String CELL_NOT_FOUND = "The requested cell was not found";
        public static final String METHOD_NOT_FOUND = "The requested shared method was not found";
        public static final String METHOD_ACCESS_DENIED = "Caller is not authorized to invoke this method";
        public static final String OPERATION_DISABLED = "This operation is disabled for the cell";
        public static final String ITEM_NOT_FOUND = "The requested item was not found";
        public static final String ITEM_CREATE_FAILED = "Failed to create the item";
        public static final String CIRCUIT_OPEN = "Service temporarily unavailable due to circuit breaker";
        public static final String RATE_LIMITED = "Too many requests - rate limit exceeded";
        public static final String TIMEOUT = "Request timed out";
        public static final String FORWARD_FAILED = "Failed to forward request to remote cell";
        public static final String JSON_PARSE_ERROR = "Failed to parse JSON payload";
    }

    public static final class Metrics {
        private Metrics() {}
        public static final String REQUESTS = "honeycomb.requests";
        public static final String LATENCY = "honeycomb.latency";
        public static final String TAG_CELL = "cell";
        public static final String TAG_ROUTE = "route";
        public static final String TAG_STATUS = "status";
    }

    public static final class ConfigKeys {
        private ConfigKeys() {}
        public static final String GLOBAL_WILDCARD = "*";
        public static final String GLOBAL_ALL = "__all__";
        public static final String GLOBAL_ALL_UPPER = "ALL";
        public static final String GLOBAL_ZERO = "0";
        public static final String MGMT_BASE_PATH = "management.endpoints.web.base-path";
        public static final String CELL_PORTS_PREFIX = "cell.ports.";
        public static final String CELL_MGMT_PORT_PREFIX = "cell.managementPort.";
        public static final String CELL_ADDRESSES_PREFIX = "cell.addresses.";
        public static final String SERVICE_DISCOVERY_BASE_URL = "service.discovery.base-url";
        public static final String JWT_ENABLED = "honeycomb.security.jwt.enabled";
        public static final String VALIDATION_PREFIX = "honeycomb.validation";
        public static final String AUTOSCALE_EVAL_INTERVAL = "${honeycomb.autoscale.evaluation-interval:30s}";
        public static final String STORAGE_TYPE = "honeycomb.storage.type";
        public static final String STORAGE_ROUTING_ENABLED = "honeycomb.storage.routing.enabled";
        public static final String STORAGE_HIBERNATE_ENABLED = "honeycomb.storage.hibernate.enabled";
        public static final String STORAGE_HIBERNATE_ANNOTATION_FREE = "honeycomb.storage.hibernate.annotation-free";
        public static final String IDEMPOTENCY_STORE = "honeycomb.idempotency.store";
        public static final String STORAGE_PREFIX = "honeycomb.storage";
        public static final String HONEYCOMB_PREFIX = "honeycomb";
        public static final String ROUTING_PREFIX = "honeycomb.routing";
        public static final String AUTOSCALE_PREFIX = "honeycomb.autoscale";
        public static final String AUDIT_PREFIX = "honeycomb.audit";
        public static final String RATE_LIMITER_PREFIX = "honeycomb.rate-limiter";
        public static final String SECURITY_PREFIX = "honeycomb.security";
        public static final String IDEMPOTENCY_PREFIX = "honeycomb.idempotency";
    }

    public static final class Defaults {
        private Defaults() {}
        public static final String SCHEMAS_DIR = "schemas";
        public static final String BASE_PACKAGE = "com.example";
        public static final String AUDIT_LIMIT = "100";
    }

    public static final class Values {
        private Values() {}
        public static final String TRUE = "true";
        public static final String FALSE = "false";
    }

        public static final class StorageDefaults {
        private StorageDefaults() {}
        public static final String TYPE = Names.STORE_MEMORY;
        public static final String HIBERNATE_URL = "postgresql://localhost:5432/honeycomb";
        public static final String HIBERNATE_USERNAME = "honeycomb";
        public static final String HIBERNATE_PASSWORD = "honeycomb";
        public static final String HIBERNATE_DIALECT = "org.hibernate.dialect.PostgreSQLDialect";
        public static final String HIBERNATE_HBM2DDL = "update";
        public static final String HIBERNATE_FORMAT_SQL = "false";
        public static final String HIBERNATE_ARCHIVE_AUTODETECTION = "class";
        public static final String PERSISTENCE_UNIT = "honeycomb-reactive";
        }

        public static final class HibernateConfigKeys {
        private HibernateConfigKeys() {}
        public static final String CONNECTION_URL = "hibernate.connection.url";
        public static final String CONNECTION_USERNAME = "hibernate.connection.username";
        public static final String CONNECTION_PASSWORD = "hibernate.connection.password";
        public static final String DIALECT = "hibernate.dialect";
        public static final String HBM2DDL = "hibernate.hbm2ddl.auto";
        public static final String SHOW_SQL = "hibernate.show_sql";
        public static final String FORMAT_SQL = "hibernate.format_sql";
        public static final String POOL_SIZE = "hibernate.connection.pool_size";
        public static final String ARCHIVE_AUTODETECTION = "hibernate.archive.autodetection";
        }

        public static final class ConfigExpressions {
        private ConfigExpressions() {}
        public static final String STORAGE_REDIS_OR_ROUTING =
            "'${honeycomb.storage.type:memory}'=='redis' || '${honeycomb.storage.routing.enabled:false}'=='true'";
        public static final String STORAGE_HIBERNATE_OR_ROUTING =
            "'${honeycomb.storage.type:memory}'=='hibernate' || '${honeycomb.storage.routing.enabled:false}'=='true'";
        public static final String STORAGE_MEMORY_OR_ROUTING =
            "'${honeycomb.storage.routing.enabled:false}'=='true' || '${honeycomb.storage.type:memory}'=='memory'";
        public static final String STORAGE_HIBERNATE_ENABLED =
            "'${honeycomb.storage.type:memory}'=='hibernate' || '${honeycomb.storage.hibernate.enabled:false}'=='true'";
        public static final String IDEMPOTENCY_REDIS =
            "'${honeycomb.idempotency.store:memory}'=='redis'";
        }

    public static final class RoutingPolicies {
        private RoutingPolicies() {}
        public static final String ONE = "one";
        public static final String RANDOM = "random";
        public static final String ROUND_ROBIN = "round-robin";
        public static final String WEIGHTED = "weighted";
        public static final String LEAST_LATENCY = "least-latency";
        public static final String CIRCUIT_AWARE = "circuit-aware";
        public static final String ALL = "all";
    }

    public static final class PropertyValues {
        private PropertyValues() {}
        public static final String SERVICE_DISCOVERY_BASE_URL = "${service.discovery.base-url:http://localhost}";
        public static final String MGMT_BASE_PATH = "${management.endpoints.web.base-path:/actuator}";
        public static final String ACTUATOR_USER = "${actuator.user:admin}";
        public static final String ACTUATOR_PASSWORD = "${actuator.password:changeit}";
        public static final String SHARED_USER = "${shared.user:shared}";
        public static final String SHARED_PASSWORD = "${shared.password:changeit}";
        public static final String SERVER_PORT = "${server.port:8080}";
    }

    public static final class OpenApi {
        private OpenApi() {}
        public static final String TITLE = "Honeycomb API";
        public static final String DESCRIPTION = "Per-cell microservice runtime with shared method invocation, CRUD operations, and cell registry";
        public static final String VERSION = "0.1.0";
        public static final String CONTACT_NAME = "Honeycomb Team";
        public static final String CONTACT_EMAIL = "team@honeycomb.example.com";
        public static final String LICENSE_NAME = "MIT License";
        public static final String LICENSE_URL = "https://opensource.org/licenses/MIT";
        public static final String SERVER_DESC = "Local development server";
    }

    public static final class SecurityDefaults {
        private SecurityDefaults() {}
        public static final String ROLES_CLAIM = "roles";
        public static final String ROLE_PREFIX = "ROLE_";
        public static final String SCOPES_CLAIM = "scp";
        public static final String SCOPE_PREFIX = "SCOPE_";
        public static final String SHARED_ROLES_CLAIM = "shared_roles";
        public static final String SHARED_ROLE_PREFIX = "ROLE_";
    }
}
