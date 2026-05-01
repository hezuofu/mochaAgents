package io.sketch.mochaagents.memory;

import io.sketch.mochaagents.models.ChatMessage;

import java.util.List;

/**
 * One agent memory slice, convertible to chat messages for the LLM.
 *
 * @param summaryMode when {@code true}, matches smolagents summary memory (omit system prompt chunks,
 *                    planning history, assistant raw blobs where applicable).
 */
public interface MemoryStep {
    List<ChatMessage> toMessages(boolean summaryMode);

    default List<ChatMessage> toMessages() {
        return toMessages(false);
    }
}