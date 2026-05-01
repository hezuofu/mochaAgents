package io.sketch.mochaagents.agents;

import io.sketch.mochaagents.models.ChatMessageStreamDelta;
import io.sketch.mochaagents.models.TokenUsage;

import java.util.List;

public interface StreamEvent {
    
    static ChatMessageStreamDelta of(String content) {
        return new ChatMessageStreamDelta(content, List.of(), null);
    }
    
    static ChatMessageStreamDelta of(String content, TokenUsage tokenUsage) {
        return new ChatMessageStreamDelta(content, List.of(), tokenUsage);
    }
    
    static ActionOutput actionOutput(Object output) {
        return new ActionOutput(output, false);
    }
    
    static ActionOutput finalAnswer(Object output) {
        return new ActionOutput(output, true);
    }
}