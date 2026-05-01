package io.sketch.mochaagents.memory;

import io.sketch.mochaagents.models.ChatMessage;
import io.sketch.mochaagents.models.MessageRole;
import io.sketch.mochaagents.models.TokenUsage;

import java.util.List;

public record PlanningStep(
    List<ChatMessage> modelInputMessages,
    ChatMessage modelOutputMessage,
    String plan,
    Timing timing,
    TokenUsage tokenUsage
) implements MemoryStep {
    
    @Override
    public List<ChatMessage> toMessages(boolean summaryMode) {
        if (summaryMode) {
            return List.of();
        }
        return List.of(
            ChatMessage.text(MessageRole.ASSISTANT, plan),
            ChatMessage.text(MessageRole.USER, "Now proceed and carry out this plan.")
        );
    }
}