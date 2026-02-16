package com.honeycomb.grpc.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ProtoJsonConverter} — bidirectional Struct↔Map/JSON conversion.
 *
 * @since 1.4.0
 */
class ProtoJsonConverterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("mapToStruct converts flat map correctly")
    void mapToStruct_flatMap() {
        Map<String, Object> map = Map.of("name", "honeycomb", "version", 1.4, "active", true);

        Struct struct = ProtoJsonConverter.mapToStruct(map);

        assertNotNull(struct);
        assertEquals("honeycomb", struct.getFieldsOrThrow("name").getStringValue());
        assertEquals(1.4, struct.getFieldsOrThrow("version").getNumberValue());
        assertTrue(struct.getFieldsOrThrow("active").getBoolValue());
    }

    @Test
    @DisplayName("mapToStruct handles null values")
    void mapToStruct_nullValues() {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("key", null);

        Struct struct = ProtoJsonConverter.mapToStruct(map);

        assertNotNull(struct);
        assertEquals(Value.KindCase.NULL_VALUE, struct.getFieldsOrThrow("key").getKindCase());
    }

    @Test
    @DisplayName("structToMap round-trips with mapToStruct")
    void roundTrip_mapToStructToMap() {
        Map<String, Object> original = Map.of("cell", "TestCell", "count", 42.0, "enabled", false);

        Struct struct = ProtoJsonConverter.mapToStruct(original);
        Map<String, Object> restored = ProtoJsonConverter.structToMap(struct);

        assertEquals("TestCell", restored.get("cell"));
        // Protobuf Struct stores all numbers as double; structToMap may return
        // Number subtype (Integer or Double) depending on implementation.
        assertEquals(42.0, ((Number) restored.get("count")).doubleValue());
        assertEquals(false, restored.get("enabled"));
    }

    @Test
    @DisplayName("mapToStruct handles nested maps")
    void mapToStruct_nestedMap() {
        Map<String, Object> map = Map.of("outer", Map.of("inner", "deep"));

        Struct struct = ProtoJsonConverter.mapToStruct(map);

        Struct inner = struct.getFieldsOrThrow("outer").getStructValue();
        assertNotNull(inner);
        assertEquals("deep", inner.getFieldsOrThrow("inner").getStringValue());
    }

    @Test
    @DisplayName("mapToStruct handles lists")
    void mapToStruct_list() {
        Map<String, Object> map = Map.of("items", List.of("a", "b", "c"));

        Struct struct = ProtoJsonConverter.mapToStruct(map);

        var listValue = struct.getFieldsOrThrow("items").getListValue();
        assertEquals(3, listValue.getValuesCount());
        assertEquals("a", listValue.getValues(0).getStringValue());
        assertEquals("c", listValue.getValues(2).getStringValue());
    }

    @Test
    @DisplayName("jsonToStruct parses JSON string")
    void jsonToStruct_validJson() {
        String json = "{\"method\":\"discount\",\"amount\":100}";

        Struct struct = ProtoJsonConverter.jsonToStruct(json, mapper);

        assertNotNull(struct);
        assertEquals("discount", struct.getFieldsOrThrow("method").getStringValue());
        assertEquals(100.0, struct.getFieldsOrThrow("amount").getNumberValue());
    }

    @Test
    @DisplayName("structToJson produces valid JSON")
    void structToJson_validStruct() {
        Struct struct = ProtoJsonConverter.mapToStruct(Map.of("key", "value"));

        String json = ProtoJsonConverter.structToJson(struct, mapper);

        assertNotNull(json);
        assertTrue(json.contains("\"key\""));
        assertTrue(json.contains("\"value\""));
    }

    @Test
    @DisplayName("mapToStruct handles empty map")
    void mapToStruct_emptyMap() {
        Struct struct = ProtoJsonConverter.mapToStruct(Map.of());

        assertNotNull(struct);
        assertEquals(0, struct.getFieldsCount());
    }

    @Test
    @DisplayName("structToMap handles empty struct")
    void structToMap_emptyStruct() {
        Map<String, Object> map = ProtoJsonConverter.structToMap(Struct.getDefaultInstance());

        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    @Test
    @DisplayName("mapToStruct handles integer values as numbers")
    void mapToStruct_integerValues() {
        Map<String, Object> map = Map.of("port", 9090, "timeout", 5000L);

        Struct struct = ProtoJsonConverter.mapToStruct(map);

        assertEquals(9090.0, struct.getFieldsOrThrow("port").getNumberValue());
        assertEquals(5000.0, struct.getFieldsOrThrow("timeout").getNumberValue());
    }
}
