package io.sketch.mochaagents.agents;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Runtime controls aligned with smolagents optional {@code MultiStepAgent.run} parameters.
 */
public record AgentRunControls(
    boolean reset,
    Integer maxStepsOverride,
    Map<String, Object> additionalArgs,
    List<byte[]> taskImages
) {
    public AgentRunControls(boolean reset, Integer maxStepsOverride, Map<String, Object> additionalArgs) {
        this(reset, maxStepsOverride, additionalArgs, List.of());
    }

    public static final AgentRunControls DEFAULT =
        new AgentRunControls(true, null, Collections.emptyMap(), List.of());

    public AgentRunControls {
        additionalArgs = additionalArgs != null ? Map.copyOf(additionalArgs) : Map.of();
        taskImages = taskImages != null ? List.copyOf(taskImages) : List.of();
    }

    public int effectiveMaxSteps(int agentDefaultMaxSteps) {
        return maxStepsOverride != null ? maxStepsOverride : agentDefaultMaxSteps;
    }

    public Map<String, Object> safeAdditionalArgs() {
        return additionalArgs;
    }
}
