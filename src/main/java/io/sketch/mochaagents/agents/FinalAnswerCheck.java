package io.sketch.mochaagents.agents;

import io.sketch.mochaagents.memory.AgentMemory;

/**
 * Optional validation hook after a final answer is produced, mirroring smolagents {@code final_answer_checks}.
 */
@FunctionalInterface
public interface FinalAnswerCheck {

    void validate(Object finalAnswer, AgentMemory memory, MultiStepAgent agent);
}
