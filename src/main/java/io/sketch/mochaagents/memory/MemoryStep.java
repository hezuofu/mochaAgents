package io.sketch.mochaagents.memory;

import io.sketch.mochaagents.models.ChatMessage;

import java.util.List;

public interface MemoryStep {
    List<ChatMessage> toMessages();
}