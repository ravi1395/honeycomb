package com.honeycomb.grpc.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bidirectional converter between Protobuf {@link Struct} and Java
 * {@link Map}/{@link Object} types.
 *
 * <p>Handles the translation layer needed to bridge JSON-based Honeycomb
 * shared-method payloads with Protobuf wire format. Also provides raw JSON
 * string conversion for complex payloads that don't map cleanly to Struct.</p>
 *
 * @since 1.4.0
 */
public final class ProtoJsonConverter {

    private static final Logger log = LoggerFactory.getLogger(ProtoJsonConverter.class);
    private static final ObjectMapper DEFAULT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private ProtoJsonConverter() {}

    /**
     * Convert a Java Map to a Protobuf Struct.
     */
    public static Struct mapToStruct(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return Struct.getDefaultInstance();
        }
        Struct.Builder builder = Struct.newBuilder();
        map.forEach((key, val) -> builder.putFields(key, objectToValue(val)));
        return builder.build();
    }

    /**
     * Convert a Protobuf Struct to a Java Map.
     */
    public static Map<String, Object> structToMap(Struct struct) {
        if (struct == null || struct.getFieldsCount() == 0) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        struct.getFieldsMap().forEach((key, val) -> map.put(key, valueToObject(val)));
        return map;
    }

    /**
     * Convert a raw JSON string to a Protobuf Struct.
     */
    public static Struct jsonToStruct(String json) {
        return jsonToStruct(json, DEFAULT_MAPPER);
    }

    /**
     * Convert a raw JSON string to a Protobuf Struct using a custom ObjectMapper.
     */
    public static Struct jsonToStruct(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) {
            return Struct.getDefaultInstance();
        }
        try {
            Map<String, Object> map = mapper.readValue(json, new TypeReference<>() {});
            return mapToStruct(map);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON to Struct: {}", e.getMessage());
            return Struct.getDefaultInstance();
        }
    }

    /**
     * Convert a Protobuf Struct to a raw JSON string.
     */
    public static String structToJson(Struct struct) {
        return structToJson(struct, DEFAULT_MAPPER);
    }

    /**
     * Convert a Protobuf Struct to a raw JSON string using a custom ObjectMapper.
     */
    public static String structToJson(Struct struct, ObjectMapper mapper) {
        if (struct == null || struct.getFieldsCount() == 0) {
            return "{}";
        }
        try {
            return mapper.writeValueAsString(structToMap(struct));
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize Struct to JSON: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Convert any Java object to a Protobuf {@link Value}.
     */
    @SuppressWarnings("unchecked")
    public static Value objectToValue(Object obj) {
        if (obj == null) {
            return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
        }
        if (obj instanceof String s) {
            return Value.newBuilder().setStringValue(s).build();
        }
        if (obj instanceof Number n) {
            return Value.newBuilder().setNumberValue(n.doubleValue()).build();
        }
        if (obj instanceof Boolean b) {
            return Value.newBuilder().setBoolValue(b).build();
        }
        if (obj instanceof Map<?, ?> m) {
            return Value.newBuilder().setStructValue(mapToStruct((Map<String, Object>) m)).build();
        }
        if (obj instanceof List<?> list) {
            ListValue.Builder lb = ListValue.newBuilder();
            for (Object item : list) {
                lb.addValues(objectToValue(item));
            }
            return Value.newBuilder().setListValue(lb.build()).build();
        }
        // Fallback: stringify
        return Value.newBuilder().setStringValue(obj.toString()).build();
    }

    /**
     * Convert a Protobuf {@link Value} back to a Java Object.
     */
    public static Object valueToObject(Value value) {
        if (value == null) return null;
        return switch (value.getKindCase()) {
            case NULL_VALUE -> null;
            case NUMBER_VALUE -> {
                double d = value.getNumberValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    if (d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                        yield (int) d;
                    }
                    yield (long) d;
                }
                yield d;
            }
            case STRING_VALUE -> value.getStringValue();
            case BOOL_VALUE -> value.getBoolValue();
            case STRUCT_VALUE -> structToMap(value.getStructValue());
            case LIST_VALUE -> {
                List<Object> list = new ArrayList<>();
                for (Value v : value.getListValue().getValuesList()) {
                    list.add(valueToObject(v));
                }
                yield list;
            }
            case KIND_NOT_SET -> null;
        };
    }
}
