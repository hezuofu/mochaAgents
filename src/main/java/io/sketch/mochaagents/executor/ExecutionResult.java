package io.sketch.mochaagents.executor;

import io.sketch.mochaagents.memory.AgentError;

import java.util.List;

/**
 * 执行结果
 */
public record ExecutionResult(
    Object output,
    List<String> logs,
    boolean isFinalAnswer,
    AgentError error
) {
    public static ExecutionResult success(Object output, List<String> logs) {
        return new ExecutionResult(output, logs, false, null);
    }
    
    public static ExecutionResult success(Object output) {
        return new ExecutionResult(output, List.of(), false, null);
    }
    
    public static ExecutionResult finalAnswer(Object output) {
        return new ExecutionResult(output, List.of(), true, null);
    }
    
    public static ExecutionResult error(AgentError error) {
        return new ExecutionResult(null, List.of(), false, error);
    }
    
    public static ExecutionResult error(String message) {
        return new ExecutionResult(null, List.of(), false, AgentError.executionError(message));
    }
    
    public boolean isSuccess() {
        return error == null;
    }
}