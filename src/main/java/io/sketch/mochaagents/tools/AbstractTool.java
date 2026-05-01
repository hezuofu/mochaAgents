package io.sketch.mochaagents.tools;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工具抽象基类
 */
public abstract class AbstractTool implements Tool {
    
    protected final String name;
    protected final String description;
    protected final Map<String, ToolInput> inputs;
    protected final String outputType;
    protected volatile boolean initialized = false;
    
    protected AbstractTool(String name, String description, 
                          Map<String, ToolInput> inputs, String outputType) {
        this.name = name;
        this.description = description;
        this.inputs = inputs;
        this.outputType = outputType;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    @Override
    public Map<String, ToolInput> getInputs() {
        return inputs;
    }
    
    @Override
    public String getOutputType() {
        return outputType;
    }
    
    @Override
    public Object call(Map<String, Object> arguments) {
        if (!initialized) {
            setup();
            initialized = true;
        }
        return forward(arguments);
    }
    
    /**
     * 工具核心逻辑
     */
    protected abstract Object forward(Map<String, Object> arguments);
    
    @Override
    public String toCodePrompt() {
        String params = inputs.entrySet().stream()
            .map(e -> mapType(e.getValue().type()) + " " + e.getKey())
            .collect(Collectors.joining(", "));
        
        return String.format("public Object %s(%s) {\n    /** %s */\n}",
            name, params, description);
    }
    
    @Override
    public String toToolCallingPrompt() {
        return String.format("%s: %s\n    Takes inputs: %s\n    Returns: %s",
            name, description, inputs.keySet(), outputType);
    }
    
    @Override
    public ToolDefinition toDefinition() {
        return ToolDefinition.builder()
            .name(name)
            .description(description)
            .inputs(inputs)
            .outputType(outputType)
            .code("")
            .requirements(List.of())
            .build();
    }
    
    /**
     * 类型映射
     */
    private String mapType(String type) {
        return switch (type) {
            case "string" -> "String";
            case "integer" -> "int";
            case "number" -> "double";
            case "boolean" -> "boolean";
            case "array" -> "List<?>";
            case "object" -> "Map<String, Object>";
            default -> "Object";
        };
    }
}