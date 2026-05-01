package io.sketch.mochaagents.models;

import java.util.List;

public record ChatMessage(
    MessageRole role,
    List<ChatMessageContent> content,
    List<ToolCall> toolCalls,
    TokenUsage tokenUsage
) {
    public static ChatMessage text(MessageRole role, String text) {
        return new ChatMessage(role, List.of(new ChatMessageContent.TextContent(text)), null, null);
    }

    /** Multimodal user message (text + optional images), for {@link io.sketch.mochaagents.memory.TaskStep}. */
    public static ChatMessage userMultipart(List<ChatMessageContent> parts) {
        return new ChatMessage(MessageRole.USER, List.copyOf(parts), null, null);
    }
    
    public static ChatMessage toolCall(List<ToolCall> toolCalls) {
        return new ChatMessage(MessageRole.TOOL_CALL, 
            List.of(new ChatMessageContent.ToolCallContent(toolCalls)), 
            toolCalls, null);
    }
    
    public String getTextContent() {
        return content.stream()
            .filter(c -> c instanceof ChatMessageContent.TextContent)
            .map(c -> ((ChatMessageContent.TextContent) c).text())
            .findFirst()
            .orElse("");
    }
}