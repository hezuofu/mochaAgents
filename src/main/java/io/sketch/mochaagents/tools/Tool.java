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
    
    Object call(Map<String, Object> arguments);
    
    String toCodePrompt();
    
    String toToolCallingPrompt();
    
    ToolDefinition toDefinition();
}