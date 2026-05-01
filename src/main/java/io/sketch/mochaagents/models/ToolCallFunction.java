package io.sketch.mochaagents.models;

import java.util.Map;

public record ToolCallFunction(
    String name,
    Map<String, Object> arguments
) {
    public static ToolCallFunction of(String name, Map<String, Object> arguments) {
        return new ToolCallFunction(name, arguments);
    }
}