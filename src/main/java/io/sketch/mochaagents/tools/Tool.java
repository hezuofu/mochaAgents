package io.sketch.mochaagents.tools;

import java.util.Map;

/**
 * 工具接口
 */
public interface Tool {
    
    String getName();
    
    String getDescription();
    
    Map<String, ToolInput> getInputs();
    
    String getOutputType();
    
    default void setup() {}

    /**
     * When true, {@link io.sketch.mochaagents.types.AgentTypeHandlers} unwraps {@link io.sketch.mochaagents.types.AgentType} arguments
     * and wraps return values (see smolagents {@code Tool.__call__(..., sanitize_inputs_outputs=True)}).
     */
    default boolean sanitizeAgentTypes() {
        return false;
    }

    Object call(Map<String, Object> arguments);
    
    String toCodePrompt();
    
    String toToolCallingPrompt();
    
    ToolDefinition toDefinition();
}