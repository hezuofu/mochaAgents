package io.sketch.mochaagents.memory;

import io.sketch.mochaagents.models.ChatMessage;
import io.sketch.mochaagents.models.MessageRole;

import java.util.List;

public record TaskStep(String task, List<byte[]> taskImages) implements MemoryStep {
    
    public TaskStep(String task) {
        this(task, List.of());
    }
    
    @Override
    public List<ChatMessage> toMessages() {
        return List.of(ChatMessage.text(MessageRole.USER, "New task:\n" + task));
    }
}