package io.sketch.mochaagents.agents;

import io.sketch.mochaagents.models.ToolCall;

public record ToolOutput(
    String id,
    Object output,
    boolean isFinalAnswer,
    String observation,
    ToolCall toolCall
) implements StreamEvent {
    public static ToolOutput from(ToolCall call, Object output) {
        return new ToolOutput(call.id(), output, false, output.toString(), call);
    }
}