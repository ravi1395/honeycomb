package com.example.honeycomb.example.service;

import com.example.honeycomb.annotations.MethodOp;
import com.example.honeycomb.annotations.MethodType;

import java.util.List;
import java.util.Map;

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
