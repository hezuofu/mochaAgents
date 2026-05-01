package io.sketch.mochaagents.models;

public record ToolCall(
    String id,
    String type,           // "function"
    ToolCallFunction function
) {
    public ToolCall(String id, ToolCallFunction function) {
        this(id, "function", function);
    }
}