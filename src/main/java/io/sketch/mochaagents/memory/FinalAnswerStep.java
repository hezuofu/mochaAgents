package io.sketch.mochaagents.memory;

import io.sketch.mochaagents.models.ChatMessage;
import io.sketch.mochaagents.models.MessageRole;

import java.util.List;

public record FinalAnswerStep(Object output) implements MemoryStep {
    
    @Override
    public List<ChatMessage> toMessages(boolean summaryMode) {
        return List.of(ChatMessage.text(MessageRole.ASSISTANT, output.toString()));
    }
}