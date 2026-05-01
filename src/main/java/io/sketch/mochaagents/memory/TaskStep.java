package io.sketch.mochaagents.memory;

import io.sketch.mochaagents.models.ChatMessage;
import io.sketch.mochaagents.models.ChatMessageContent;
import io.sketch.mochaagents.models.MessageRole;

import java.util.ArrayList;
import java.util.List;

public record TaskStep(String task, List<byte[]> taskImages) implements MemoryStep {
    
    public TaskStep(String task) {
        this(task, List.of());
    }
    
    @Override
    public List<ChatMessage> toMessages(boolean summaryMode) {
        String body = "New task:\n" + task;
        if (taskImages == null || taskImages.isEmpty()) {
            return List.of(ChatMessage.text(MessageRole.USER, body));
        }
        List<ChatMessageContent> parts = new ArrayList<>();
        parts.add(new ChatMessageContent.TextContent(body));
        for (byte[] img : taskImages) {
            if (img != null && img.length > 0) {
                parts.add(new ChatMessageContent.ImageContent(img));
            }
        }
        return List.of(ChatMessage.userMultipart(parts));
    }
}