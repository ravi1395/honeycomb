package com.honeycomb.example.service;

import com.honeycomb.core.annotations.MethodOp;
import com.honeycomb.core.annotations.MethodType;

import java.util.List;
import java.util.Map;

/**
 * Typed interface for the Catalog service cell’s operations.
 *
 * <p>Each method is annotated with {@link MethodType} to map it
 * to a CRUD operation dispatched by
 * {@link com.honeycomb.core.web.ServiceCellController}.</p>
 */
public interface CatalogServiceApi {
    @MethodType(MethodOp.READ)
    List<Map<String, Object>> listItems();

    @MethodType(MethodOp.READ)
    Map<String, Object> getItem(String id);

    @MethodType(MethodOp.CREATE)
    Map<String, Object> createItem(Map<String, Object> body);

    @MethodType(MethodOp.UPDATE)
    Map<String, Object> updateItem(Map<String, Object> body);

    @MethodType(MethodOp.DELETE)
    Map<String, Object> deleteItem(String id);
}
