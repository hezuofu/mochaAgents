package io.sketch.mochaagents.memory;

/**
 * Agent 错误
 */
public record AgentError(
    String message,
    String type
) {
    public static AgentError maxStepsReached() {
        return new AgentError("Reached max steps.", "max_steps_error");
    }
    
    public static AgentError parsingError(String message) {
        return new AgentError(message, "parsing_error");
    }
    
    public static AgentError executionError(String message) {
        return new AgentError(message, "execution_error");
    }
    
    public static AgentError modelError(String message) {
        return new AgentError(message, "model_error");
    }
    
    public static AgentError toolError(String message) {
        return new AgentError(message, "tool_error");
    }
    
    public boolean isRecoverable() {
        return !type.equals("max_steps_error");
    }
}