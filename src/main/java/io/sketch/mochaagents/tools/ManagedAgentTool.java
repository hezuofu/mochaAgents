package io.sketch.mochaagents.tools;

import io.sketch.mochaagents.agents.AgentRunControls;
import io.sketch.mochaagents.agents.MultiStepAgent;
import io.sketch.mochaagents.agents.PromptRendering;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes a {@link MultiStepAgent} as a {@link Tool} for {@link io.sketch.mochaagents.agents.ToolCallingAgent},
 * matching smolagents managed-agent semantics.
 */
public final class ManagedAgentTool implements Tool {

    private final MultiStepAgent delegate;
    private final PromptRendering rendering;

    public ManagedAgentTool(MultiStepAgent delegate, PromptRendering rendering) {
        this.delegate = delegate;
        this.rendering = rendering != null ? rendering : PromptRendering.defaultRenderer();
    }

    @Override
    public String getName() {
        return delegate.agentNameRequired();
    }

    @Override
    public String getDescription() {
        return delegate.agentDescriptionRequired();
    }

    @Override
    public Map<String, ToolInput> getInputs() {
        Map<String, ToolInput> m = new LinkedHashMap<>();
        m.put(
            "task",
            new ToolInput(
                "string",
                "Long detailed description of the task.",
                false
            )
        );
        m.put(
            "additional_args",
            new ToolInput(
                "object",
                "Dictionary of extra inputs to pass to the managed agent, e.g. images or contextual data.",
                true
            )
        );
        return m;
    }

    @Override
    public String getOutputType() {
        return "string";
    }

    @Override
    public Object call(Map<String, Object> arguments) {
        String task = (String) arguments.get("task");
        @SuppressWarnings("unchecked")
        Map<String, Object> extra =
            arguments.get("additional_args") instanceof Map<?, ?> mm
                ? (Map<String, Object>) mm
                : null;
        return delegate.runAsManagedMember(task, extra, rendering);
    }

    @Override
    public String toCodePrompt() {
        return ""; // delegated agent; code agent uses injected tools separately
    }

    @Override
    public String toToolCallingPrompt() {
        return getDescription();
    }

    @Override
    public ToolDefinition toDefinition() {
        return new ToolDefinition(getName(), getDescription(), getInputs(), getOutputType(), "", List.of());
    }

    MultiStepAgent delegate() {
        return delegate;
    }
}
