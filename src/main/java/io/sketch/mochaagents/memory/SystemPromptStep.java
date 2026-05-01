package io.sketch.mochaagents.memory;

import io.sketch.mochaagents.models.ChatMessage;
import io.sketch.mochaagents.models.MessageRole;

import java.util.List;

public record SystemPromptStep(String systemPrompt) implements MemoryStep {
    
    @Override
    public List<ChatMessage> toMessages(boolean summaryMode) {
        if (summaryMode) {
            return List.of();
        }
        return List.of(ChatMessage.text(MessageRole.SYSTEM, systemPrompt));
    }
}