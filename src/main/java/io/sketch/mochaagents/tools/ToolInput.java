package io.sketch.mochaagents.tools;

/**
 * 工具输入参数定义
 */
public record ToolInput(
    String type,           // string, boolean, integer, number, image, audio, array, object, any, null
    String description,
    boolean nullable
) {
    public static ToolInput string(String description) {
        return new ToolInput("string", description, false);
    }
    
    public static ToolInput stringNullable(String description) {
        return new ToolInput("string", description, true);
    }
    
    public static ToolInput integer(String description) {
        return new ToolInput("integer", description, false);
    }
    
    public static ToolInput number(String description) {
        return new ToolInput("number", description, false);
    }
    
    public static ToolInput bool(String description) {
        return new ToolInput("boolean", description, false);
    }
    
    public static ToolInput any(String description) {
        return new ToolInput("any", description, false);
    }
}