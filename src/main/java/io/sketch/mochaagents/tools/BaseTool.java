package io.sketch.mochaagents.tools;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础工具实现类，简化工具创建
 */
public abstract class BaseTool implements Tool {
    
    private final String name;
    private final String description;
    private final Map<String, ToolInput> inputs = new HashMap<>();
    private final String outputType;
    private String code = "";
    private List<String> requirements = List.of();
    
    public BaseTool(String name, String description) {
        this(name, description, "string");
    }
    
    public BaseTool(String name, String description, String outputType) {
        this.name = name;
        this.description = description;
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
    
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    public List<String> getRequirements() {
        return requirements;
    }
    
    public void setRequirements(List<String> requirements) {
        this.requirements = requirements;
    }
    
    @Override
    public void setup() {}
    
    @Override
    public Object call(Map<String, Object> arguments) {
        return null;
    }
    
    @Override
    public String toCodePrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("def ").append(name).append("(");
        boolean first = true;
        for (Map.Entry<String, ToolInput> entry : inputs.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(entry.getKey());
            first = false;
        }
        sb.append("):\n");
        sb.append("    \"\"\"").append(description).append("\"\"\"\n");
        sb.append("    pass");
        return sb.toString();
    }
    
    @Override
    public String toToolCallingPrompt() {
        return String.format("{\"name\": \"%s\", \"description\": \"%s\"}", name, description);
    }
    
    @Override
    public ToolDefinition toDefinition() {
        return ToolDefinition.builder()
            .name(name)
            .description(description)
            .inputs(inputs)
            .outputType(outputType)
            .code(code)
            .requirements(requirements)
            .build();
    }
    
    protected void addInput(String name, String description) {
        inputs.put(name, ToolInput.string(description));
    }
    
    protected void addInput(String name, String description, String type) {
        inputs.put(name, new ToolInput(type, description, false));
    }
    
    protected void addOptionalInput(String name, String description) {
        inputs.put(name, ToolInput.stringNullable(description));
    }
}
