package io.sketch.mochaagents.tools;

import java.util.List;
import java.util.Map;

/**
 * 工具定义（用于序列化）
 */
public record ToolDefinition(
    String name,
    String description,
    Map<String, ToolInput> inputs,
    String outputType,
    String code,
    List<String> requirements
) {
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String name;
        private String description;
        private Map<String, ToolInput> inputs;
        private String outputType;
        private String code = "";
        private List<String> requirements = List.of();
        
        public Builder name(String name) {
            this.name = name;
            return this;
        }
        
        public Builder description(String description) {
            this.description = description;
            return this;
        }
        
        public Builder inputs(Map<String, ToolInput> inputs) {
            this.inputs = inputs;
            return this;
        }
        
        public Builder outputType(String outputType) {
            this.outputType = outputType;
            return this;
        }
        
        public Builder code(String code) {
            this.code = code;
            return this;
        }
        
        public Builder requirements(List<String> requirements) {
            this.requirements = requirements;
            return this;
        }
        
        public ToolDefinition build() {
            return new ToolDefinition(name, description, inputs, outputType, code, requirements);
        }
    }
}