package com.example.honeycomb.service;

import com.example.honeycomb.config.HoneycombProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springframework.stereotype.Service;
import com.example.honeycomb.util.HoneycombConstants;

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

        String base = HoneycombConstants.Paths.HONEYCOMB_MODELS
                + HoneycombConstants.Names.SEPARATOR_SLASH
                + cellName;
        String items = base
                + HoneycombConstants.Names.SEPARATOR_SLASH
                + HoneycombConstants.Paths.ITEMS;
        String itemById = base
                + HoneycombConstants.Names.SEPARATOR_SLASH
                + HoneycombConstants.Paths.ITEMS
                + HoneycombConstants.Names.SEPARATOR_SLASH
                + HoneycombConstants.Names.OPEN_BRACE
                + HoneycombConstants.JsonKeys.ID
                + HoneycombConstants.Names.CLOSE_BRACE;

        Paths paths = new Paths();

        // Describe model
        paths.addPathItem(base, new PathItem().get(new Operation()
                .summary(HoneycombConstants.Swagger.SUMMARY_DESCRIBE)
                .addTagsItem(HoneycombConstants.Swagger.TAG_CELL)
                .responses(new io.swagger.v3.oas.models.responses.ApiResponses()
                        .addApiResponse(HoneycombConstants.Swagger.RESP_200,
                                new ApiResponse().description(HoneycombConstants.Swagger.DESC_CELL))
                        .addApiResponse(HoneycombConstants.Swagger.RESP_404,
                                new ApiResponse().description(HoneycombConstants.Swagger.DESC_CELL_NOT_FOUND))
                )));

        // List items (read)
        if (properties.isOperationAllowed(cellName, HoneycombConstants.Ops.READ)) {
            paths.addPathItem(items, new PathItem().get(new Operation()
                    .summary(HoneycombConstants.Swagger.SUMMARY_LIST_ITEMS)
                    .addTagsItem(HoneycombConstants.Swagger.TAG_CELL)
                    .responses(new io.swagger.v3.oas.models.responses.ApiResponses()
                            .addApiResponse(HoneycombConstants.Swagger.RESP_200,
                                    new ApiResponse().description(HoneycombConstants.Swagger.DESC_LIST_ITEMS))
                    )));
            paths.addPathItem(itemById, new PathItem().get(new Operation()
                    .summary(HoneycombConstants.Swagger.SUMMARY_GET_ITEM)
                    .addTagsItem(HoneycombConstants.Swagger.TAG_CELL)
                    .responses(new io.swagger.v3.oas.models.responses.ApiResponses()
                            .addApiResponse(HoneycombConstants.Swagger.RESP_200,
                                    new ApiResponse().description(HoneycombConstants.Swagger.DESC_ITEM_FOUND))
                            .addApiResponse(HoneycombConstants.Swagger.RESP_404,
                                    new ApiResponse().description(HoneycombConstants.Swagger.DESC_ITEM_NOT_FOUND))
                    )));
        }

        // Create
        if (properties.isOperationAllowed(cellName, HoneycombConstants.Ops.CREATE)) {
            PathItem item = paths.containsKey(items) ? paths.get(items) : new PathItem();
            item.post(new Operation()
                    .summary(HoneycombConstants.Swagger.SUMMARY_CREATE_ITEM)
                    .addTagsItem(HoneycombConstants.Swagger.TAG_CELL)
                    .responses(new io.swagger.v3.oas.models.responses.ApiResponses()
                            .addApiResponse(HoneycombConstants.Swagger.RESP_201,
                                    new ApiResponse().description(HoneycombConstants.Swagger.DESC_ITEM_CREATED))
                    ));
            paths.addPathItem(items, item);
        }

        // Update
        if (properties.isOperationAllowed(cellName, HoneycombConstants.Ops.UPDATE)) {
            PathItem item = paths.containsKey(itemById) ? paths.get(itemById) : new PathItem();
            item.put(new Operation()
                    .summary(HoneycombConstants.Swagger.SUMMARY_UPDATE_ITEM)
                    .addTagsItem(HoneycombConstants.Swagger.TAG_CELL)
                    .responses(new io.swagger.v3.oas.models.responses.ApiResponses()
                            .addApiResponse(HoneycombConstants.Swagger.RESP_200,
                                    new ApiResponse().description(HoneycombConstants.Swagger.DESC_ITEM_UPDATED))
                            .addApiResponse(HoneycombConstants.Swagger.RESP_404,
                                    new ApiResponse().description(HoneycombConstants.Swagger.DESC_ITEM_NOT_FOUND))
                    ));
            paths.addPathItem(itemById, item);
        }

        // Delete
        if (properties.isOperationAllowed(cellName, HoneycombConstants.Ops.DELETE)) {
            PathItem item = paths.containsKey(itemById) ? paths.get(itemById) : new PathItem();
            item.delete(new Operation()
                    .summary(HoneycombConstants.Swagger.SUMMARY_DELETE_ITEM)
                    .addTagsItem(HoneycombConstants.Swagger.TAG_CELL)
                    .responses(new io.swagger.v3.oas.models.responses.ApiResponses()
                            .addApiResponse(HoneycombConstants.Swagger.RESP_204,
                                    new ApiResponse().description(HoneycombConstants.Swagger.DESC_ITEM_DELETED))
                            .addApiResponse(HoneycombConstants.Swagger.RESP_404,
                                    new ApiResponse().description(HoneycombConstants.Swagger.DESC_ITEM_NOT_FOUND))
                    ));
            paths.addPathItem(itemById, item);
        }

        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title(HoneycombConstants.Swagger.INFO_TITLE_PREFIX + cellName)
                        .version(HoneycombConstants.Swagger.INFO_VERSION))
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
                                                .title(HoneycombConstants.Swagger.INFO_TITLE_ALL)
                                                .version(HoneycombConstants.Swagger.INFO_VERSION))
                                .paths(paths);
        }
}