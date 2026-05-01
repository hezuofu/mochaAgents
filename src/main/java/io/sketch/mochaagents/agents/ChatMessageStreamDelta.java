package io.sketch.mochaagents.agents;

import io.sketch.mochaagents.models.ToolCall;
import io.sketch.mochaagents.models.TokenUsage;

import java.util.List;

public record ChatMessageStreamDelta(
    String content,
    List<ToolCall> toolCalls,
    TokenUsage tokenUsage
) implements StreamEvent {}