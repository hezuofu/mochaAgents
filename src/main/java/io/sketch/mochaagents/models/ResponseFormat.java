package io.sketch.mochaagents.models;

import java.util.Map;

/**
 * 响应格式
 */
public record ResponseFormat(
    String type,           // "json_schema", "text"
    Map<String, Object> jsonSchema
) {
    public static ResponseFormat text() {
        return new ResponseFormat("text", null);
    }
    
    public static ResponseFormat jsonSchema(Map<String, Object> schema) {
        return new ResponseFormat("json_schema", schema);
    }
}