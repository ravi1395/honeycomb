package com.example.honeycomb.service;

import com.example.honeycomb.config.HoneycombProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.Optional;

@Service
public class CellSwaggerService {
    private final CellRegistry cellRegistry;
    private final HoneycombProperties properties;

    public CellSwaggerService(CellRegistry cellRegistry, HoneycombProperties properties) {
        this.cellRegistry = cellRegistry;
        this.properties = properties;
    }

    public Optional<OpenAPI> buildForCell(String cellName) {
        if (cellName == null || cellName.isBlank()) {
            return Optional.empty();
        }
        if (cellRegistry.getCellClass(cellName).isEmpty()) {
            return Optional.empty();
        }

        String base = "/honeycomb/models/" + cellName;
        String items = base + "/items";
        String itemById = base + "/items/{id}";

        Paths paths = new Paths();

        // Describe model
        paths.addPathItem(base, new PathItem().get(new Operation()
                .summary("Describe cell model")
                .addTagsItem("Cell")
                .responses(new io.swagger.v3.oas.models.responses.ApiResponses()
                        .addApiResponse("200", new ApiResponse().description("Cell description"))
                        .addApiResponse("404", new ApiResponse().description("Cell not found"))
                )));

        // List items (read)
        if (properties.isOperationAllowed(cellName, "read")) {
            paths.addPathItem(items, new PathItem().get(new Operation()
                    .summary("List all items in cell")
                    .addTagsItem("Cell")
                    .responses(new io.swagger.v3.oas.models.responses.ApiResponses()
                            .addApiResponse("200", new ApiResponse().description("List of items"))
                    )));
            paths.addPathItem(itemById, new PathItem().get(new Operation()
                    .summary("Get item by ID")
                    .addTagsItem("Cell")
                    .responses(new io.swagger.v3.oas.models.responses.ApiResponses()
                            .addApiResponse("200", new ApiResponse().description("Item found"))
                            .addApiResponse("404", new ApiResponse().description("Item not found"))
                    )));
        }

        // Create
        if (properties.isOperationAllowed(cellName, "create")) {
            PathItem item = paths.containsKey(items) ? paths.get(items) : new PathItem();
            item.post(new Operation()
                    .summary("Create item")
                    .addTagsItem("Cell")
                    .responses(new io.swagger.v3.oas.models.responses.ApiResponses()
                            .addApiResponse("201", new ApiResponse().description("Item created"))
                    ));
            paths.addPathItem(items, item);
        }

        // Update
        if (properties.isOperationAllowed(cellName, "update")) {
            PathItem item = paths.containsKey(itemById) ? paths.get(itemById) : new PathItem();
            item.put(new Operation()
                    .summary("Update item")
                    .addTagsItem("Cell")
                    .responses(new io.swagger.v3.oas.models.responses.ApiResponses()
                            .addApiResponse("200", new ApiResponse().description("Item updated"))
                            .addApiResponse("404", new ApiResponse().description("Item not found"))
                    ));
            paths.addPathItem(itemById, item);
        }

        // Delete
        if (properties.isOperationAllowed(cellName, "delete")) {
            PathItem item = paths.containsKey(itemById) ? paths.get(itemById) : new PathItem();
            item.delete(new Operation()
                    .summary("Delete item")
                    .addTagsItem("Cell")
                    .responses(new io.swagger.v3.oas.models.responses.ApiResponses()
                            .addApiResponse("204", new ApiResponse().description("Item deleted"))
                            .addApiResponse("404", new ApiResponse().description("Item not found"))
                    ));
            paths.addPathItem(itemById, item);
        }

        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title("Honeycomb Cell API - " + cellName)
                        .version("1.0"))
                .paths(paths);

        return Optional.of(openAPI);
    }

        public OpenAPI buildForAllCells() {
                Paths paths = new Paths();
                Set<String> cells = cellRegistry.getCellNames();
                for (String cell : cells) {
                        buildForCell(cell).map(OpenAPI::getPaths).ifPresent(p -> {
                                if (p != null) {
                                        p.forEach(paths::addPathItem);
                                }
                        });
                }

                return new OpenAPI()
                                .info(new Info()
                                                .title("Honeycomb Cell APIs")
                                                .version("1.0"))
                                .paths(paths);
        }
}