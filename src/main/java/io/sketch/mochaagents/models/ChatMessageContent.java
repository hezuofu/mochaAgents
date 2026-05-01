package io.sketch.mochaagents.models;

import java.util.List;

public sealed interface ChatMessageContent {
    record TextContent(String text) implements ChatMessageContent {}
    record ImageContent(byte[] image) implements ChatMessageContent {}
    record ToolCallContent(List<ToolCall> toolCalls) implements ChatMessageContent {}
}