package com.honeycomb.core.config;

import com.honeycomb.core.service.CellRegistry;
import com.honeycomb.core.service.SharedwallMethodCache;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Dynamically generates OpenAPI paths for every discovered cell and shared method
 * at runtime. This customizer runs after springdoc builds the base spec, injecting
 * path items for:
 * <ul>
 *   <li>CRUD endpoints for each cell (list/get/create/update/delete items)</li>
 *   <li>Shared method invoke endpoints</li>
 * </ul>
 *
 * <p><b>Added in v1.3</b> — automatic OpenAPI documentation for dynamic endpoints.</p>
 *
 * <p>Implementation details:
 * <ul>
 *   <li>Cell schemas are derived via reflection on the cell's Java class fields</li>
 *   <li>Shared method request schemas are built from method parameter types</li>
 *   <li>Version, X-From-Cell, and Idempotency-Key headers are documented for shared methods</li>
 *   <li>Tag groups ("Cell CRUD (Dynamic)" and "Shared Methods (Dynamic)") organize the paths</li>
 * </ul>
 * </p>
 */
@Component
public class DynamicOpenApiCustomizer implements GlobalOpenApiCustomizer {
    private static final Logger log = LoggerFactory.getLogger(DynamicOpenApiCustomizer.class);

    private final CellRegistry cellRegistry;
    private final SharedwallMethodCache methodCache;

    public DynamicOpenApiCustomizer(CellRegistry cellRegistry,
                                    SharedwallMethodCache methodCache) {
        this.cellRegistry = cellRegistry;
        this.methodCache = methodCache;
    }

    @Override
    public void customise(io.swagger.v3.oas.models.OpenAPI openApi) {
        try {
            addCellCrudPaths(openApi);
            addSharedMethodPaths(openApi);
            log.debug("Dynamic OpenAPI customization completed");
        } catch (Exception ex) {
            log.warn("Error during dynamic OpenAPI customization: {}", ex.getMessage());
        }
    }

    // ─── Cell CRUD Paths ─────────────────────────────────────────
    // Generates list/get/create/update/delete paths for each @Cell class.
    // Schemas are derived from cell class fields via reflection.

    private void addCellCrudPaths(io.swagger.v3.oas.models.OpenAPI openApi) {
        Set<String> cellNames = cellRegistry.getCellNames();
        if (cellNames.isEmpty()) return;

        // Ensure tag exists
        Tag tag = new Tag().name("Cell CRUD (Dynamic)")
                .description("Auto-generated CRUD paths for discovered cells");
        if (openApi.getTags() == null) openApi.setTags(new ArrayList<>());
        boolean hasTag = openApi.getTags().stream().anyMatch(t -> "Cell CRUD (Dynamic)".equals(t.getName()));
        if (!hasTag) openApi.getTags().add(tag);

        for (String cellName : cellNames) {
            Optional<Class<?>> clsOpt = cellRegistry.getCellClass(cellName);
            Schema<?> cellSchema = clsOpt
                    .<Schema<?>>map(this::buildSchemaFromClass)
                    .orElseGet(ObjectSchema::new);
            cellSchema.setTitle(cellName);

            String basePath = "/honeycomb/models/" + cellName + "/items";
            String itemPath = basePath + "/{id}";

            // LIST + CREATE
            PathItem listPathItem = getOrCreatePathItem(openApi, basePath);
            listPathItem.get(buildListOperation(cellName, cellSchema));
            listPathItem.post(buildCreateOperation(cellName, cellSchema));

            // GET / UPDATE / DELETE by ID
            PathItem itemPathItem = getOrCreatePathItem(openApi, itemPath);
            itemPathItem.get(buildGetByIdOperation(cellName, cellSchema));
            itemPathItem.put(buildUpdateOperation(cellName, cellSchema));
            itemPathItem.delete(buildDeleteOperation(cellName));
        }
    }

    private Operation buildListOperation(String cellName, Schema<?> schema) {
        return new Operation()
                .operationId("list" + cellName + "Items")
                .summary("List all " + cellName + " items")
                .addTagsItem("Cell CRUD (Dynamic)")
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse()
                                .description("List of " + cellName + " items")
                                .content(new Content().addMediaType("application/json",
                                        new MediaType().schema(new ArraySchema().items(schema))))));
    }

    private Operation buildCreateOperation(String cellName, Schema<?> schema) {
        return new Operation()
                .operationId("create" + cellName + "Item")
                .summary("Create a " + cellName + " item")
                .addTagsItem("Cell CRUD (Dynamic)")
                .requestBody(new RequestBody()
                        .required(true)
                        .content(new Content().addMediaType("application/json",
                                new MediaType().schema(schema))))
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse()
                                .description("Created " + cellName + " item")
                                .content(new Content().addMediaType("application/json",
                                        new MediaType().schema(schema)))));
    }

    private Operation buildGetByIdOperation(String cellName, Schema<?> schema) {
        return new Operation()
                .operationId("get" + cellName + "Item")
                .summary("Get " + cellName + " item by ID")
                .addTagsItem("Cell CRUD (Dynamic)")
                .addParametersItem(idParameter())
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse()
                                .description(cellName + " item")
                                .content(new Content().addMediaType("application/json",
                                        new MediaType().schema(schema))))
                        .addApiResponse("404", new ApiResponse().description("Item not found")));
    }

    private Operation buildUpdateOperation(String cellName, Schema<?> schema) {
        return new Operation()
                .operationId("update" + cellName + "Item")
                .summary("Update " + cellName + " item")
                .addTagsItem("Cell CRUD (Dynamic)")
                .addParametersItem(idParameter())
                .requestBody(new RequestBody()
                        .required(true)
                        .content(new Content().addMediaType("application/json",
                                new MediaType().schema(schema))))
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse()
                                .description("Updated " + cellName + " item")
                                .content(new Content().addMediaType("application/json",
                                        new MediaType().schema(schema)))));
    }

    private Operation buildDeleteOperation(String cellName) {
        return new Operation()
                .operationId("delete" + cellName + "Item")
                .summary("Delete " + cellName + " item")
                .addTagsItem("Cell CRUD (Dynamic)")
                .addParametersItem(idParameter())
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse().description("Item deleted"))
                        .addApiResponse("404", new ApiResponse().description("Item not found")));
    }

    // ─── Shared Method Paths ─────────────────────────────────────
    // Generates POST invoke paths for each @Sharedwall method.
    // Includes request/response schemas, version headers, and access control documentation.

    private void addSharedMethodPaths(io.swagger.v3.oas.models.OpenAPI openApi) {
        Map<String, List<SharedwallMethodCache.MethodCandidate>> candidates = methodCache.getAllCandidates();
        if (candidates.isEmpty()) return;

        Tag tag = new Tag().name("Shared Methods (Dynamic)")
                .description("Auto-generated paths for @Sharedwall methods");
        if (openApi.getTags() == null) openApi.setTags(new ArrayList<>());
        boolean hasTag = openApi.getTags().stream().anyMatch(t -> "Shared Methods (Dynamic)".equals(t.getName()));
        if (!hasTag) openApi.getTags().add(tag);

        for (Map.Entry<String, List<SharedwallMethodCache.MethodCandidate>> entry : candidates.entrySet()) {
            String methodName = entry.getKey();
            List<SharedwallMethodCache.MethodCandidate> mcList = entry.getValue();

            String path = "/honeycomb/shared/" + methodName;
            PathItem pathItem = getOrCreatePathItem(openApi, path);

            // Build a combined request schema from the first candidate's parameters
            SharedwallMethodCache.MethodCandidate primary = mcList.get(0);
            Schema<?> requestSchema = buildRequestSchemaFromMethod(primary);

            // Build response schema: { "<cellName>": { "result": ... } }
            Schema<?> responseSchema = new ObjectSchema()
                    .description("Aggregated result keyed by cell name");

            // Build list of implementing cells for the description
            List<String> cells = mcList.stream()
                    .map(c -> c.getBean().getClass().getSimpleName())
                    .distinct()
                    .toList();

            String version = primary.getSharedwall() != null && primary.getSharedwall().version() != null
                    ? primary.getSharedwall().version() : "v1";
            String[] allowedFrom = primary.getSharedwall() != null ? primary.getSharedwall().allowedFrom() : new String[0];
            String allowedDesc = allowedFrom.length > 0
                    ? " | AllowedFrom: " + String.join(", ", allowedFrom)
                    : "";

            Operation postOp = new Operation()
                    .operationId("invoke_" + methodName)
                    .summary("Invoke shared method: " + methodName)
                    .description("Version: " + version + " | Cells: " + String.join(", ", cells) + allowedDesc)
                    .addTagsItem("Shared Methods (Dynamic)");

            if (requestSchema != null) {
                postOp.requestBody(new RequestBody()
                        .content(new Content().addMediaType("application/json",
                                new MediaType().schema(requestSchema))));
            }

            postOp.responses(new ApiResponses()
                    .addApiResponse("200", new ApiResponse()
                            .description("Dispatch result")
                            .content(new Content().addMediaType("application/json",
                                    new MediaType().schema(responseSchema))))
                    .addApiResponse("403", new ApiResponse().description("Access denied"))
                    .addApiResponse("404", new ApiResponse().description("Method not found"))
                    .addApiResponse("400", new ApiResponse().description("Schema validation failed"))
                    .addApiResponse("500", new ApiResponse().description("Invocation error")));

            // Add standard Honeycomb headers to the shared method invoke operation:
            // - X-Shared-Version: method version routing (default: v1)
            // - X-From-Cell: caller identity for allowedFrom access control
            // - Idempotency-Key: safe retry support (v1.2+)

            // Add version header parameter
            postOp.addParametersItem(new Parameter()
                    .name("X-Shared-Version")
                    .in("header")
                    .required(false)
                    .description("Method version (default: v1)")
                    .schema(new StringSchema()._default("v1")));

            // Add from-cell header
            postOp.addParametersItem(new Parameter()
                    .name("X-From-Cell")
                    .in("header")
                    .required(false)
                    .description("Caller cell identity")
                    .schema(new StringSchema()));

            // Add idempotency key header
            postOp.addParametersItem(new Parameter()
                    .name("Idempotency-Key")
                    .in("header")
                    .required(false)
                    .description("Idempotency key for safe retries")
                    .schema(new StringSchema()));

            pathItem.post(postOp);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────
    // Schema generation utilities: builds OpenAPI schemas from Java types via reflection.
    // Handles primitives, collections, maps, and falls back to ObjectSchema for complex types.

    private PathItem getOrCreatePathItem(io.swagger.v3.oas.models.OpenAPI openApi, String path) {
        if (openApi.getPaths() == null) {
            openApi.setPaths(new io.swagger.v3.oas.models.Paths());
        }
        return openApi.getPaths().computeIfAbsent(path, k -> new PathItem());
    }

    private Parameter idParameter() {
        return new Parameter()
                .name("id")
                .in("path")
                .required(true)
                .description("Item ID")
                .schema(new StringSchema());
    }

    private Schema<?> buildSchemaFromClass(Class<?> cls) {
        ObjectSchema schema = new ObjectSchema();
        for (Field field : cls.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            schema.addProperty(field.getName(), mapJavaTypeToSchema(field.getType()));
        }
        return schema;
    }

    private Schema<?> buildRequestSchemaFromMethod(SharedwallMethodCache.MethodCandidate candidate) {
        Method m = candidate.getMethod();
        int paramCount = m.getParameterCount();
        if (paramCount == 0) return null;
        if (paramCount == 1) {
            Class<?> type = m.getParameterTypes()[0];
            if (type == String.class) return new StringSchema();
            if (type == byte[].class) return new StringSchema().format("binary");
            if (Map.class.isAssignableFrom(type)) {
                return new ObjectSchema().description("JSON object payload");
            }
            return mapJavaTypeToSchema(type);
        }
        // multi-param: build object with parameter names
        ObjectSchema schema = new ObjectSchema();
        for (java.lang.reflect.Parameter param : m.getParameters()) {
            schema.addProperty(param.getName(), mapJavaTypeToSchema(param.getType()));
        }
        return schema;
    }

    @SuppressWarnings("rawtypes")
    private Schema<?> mapJavaTypeToSchema(Class<?> type) {
        if (type == String.class) return new StringSchema();
        if (type == int.class || type == Integer.class) return new IntegerSchema();
        if (type == long.class || type == Long.class) return new IntegerSchema().format("int64");
        if (type == double.class || type == Double.class) return new NumberSchema().format("double");
        if (type == float.class || type == Float.class) return new NumberSchema().format("float");
        if (type == boolean.class || type == Boolean.class) return new BooleanSchema();
        if (type == byte[].class) return new StringSchema().format("binary");
        if (List.class.isAssignableFrom(type) || Collection.class.isAssignableFrom(type)) {
            return new ArraySchema().items(new Schema().type("object"));
        }
        if (Map.class.isAssignableFrom(type)) {
            return new ObjectSchema();
        }
        // Fallback: object
        return new ObjectSchema().description(type.getSimpleName());
    }
}
