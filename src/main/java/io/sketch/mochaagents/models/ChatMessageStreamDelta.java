package io.sketch.mochaagents.models;

import java.util.List;

public record ChatMessageStreamDelta(
    String content,
    List<ToolCall> toolCalls,
    TokenUsage tokenUsage
) {}
