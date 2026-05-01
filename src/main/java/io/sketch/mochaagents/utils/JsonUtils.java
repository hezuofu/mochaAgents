package io.sketch.mochaagents.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.util.Map;

/**
 * JSON 工具类
 */
public final class JsonUtils {
    
    private static final ObjectMapper mapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);
    
    private JsonUtils() {
        // 工具类禁止实例化
    }
    
    public static String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize object to JSON", e);
        }
    }
    
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize JSON to object", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    public static Map<String, Object> toMap(String json) {
        try {
            return mapper.readValue(json, Map.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse JSON to map", e);
        }
    }
    
    public static ObjectMapper getMapper() {
        return mapper;
    }
}