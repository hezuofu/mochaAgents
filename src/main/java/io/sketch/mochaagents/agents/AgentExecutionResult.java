package io.sketch.mochaagents.agents;

import io.sketch.mochaagents.memory.MemoryStep;

import java.util.List;
import java.util.Map;

/**
 * Structured outcome of {@link MultiStepAgent#runReturningDetails}, aligned with smolagents {@code return_full_result}.
 */
public record AgentExecutionResult(
    Object output,
    boolean reachedFinalAnswer,
    boolean interrupted,
    boolean endedWithNonRecoverableError,
    List<MemoryStep> memorySteps,
    Map<String, Object> runStateSnapshot
) {

    public static AgentExecutionResult of(
        Object output,
        boolean reachedFinalAnswer,
        boolean interrupted,
        boolean endedWithNonRecoverableError,
        List<MemoryStep> memorySteps,
        Map<String, Object> runStateSnapshot
    ) {
        return new AgentExecutionResult(
            output,
            reachedFinalAnswer,
            interrupted,
            endedWithNonRecoverableError,
            List.copyOf(memorySteps),
            Map.copyOf(runStateSnapshot)
        );
    }

    public static AgentExecutionResult interrupted(
        List<MemoryStep> memorySteps,
        Map<String, Object> runStateSnapshot
    ) {
        return of(null, false, true, false, memorySteps, runStateSnapshot);
    }
}
