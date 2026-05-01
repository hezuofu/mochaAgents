package io.sketch.mochaagents.models;

import io.sketch.mochaagents.tools.Tool;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 模型接口
 */
public interface Model {
    
    ChatMessage generate(List<ChatMessage> messages);
    
    ChatMessage generate(List<ChatMessage> messages, List<Tool> tools);
    
    ChatMessage generate(List<ChatMessage> messages, 
                         List<Tool> tools, 
                         ResponseFormat format);
    
    ChatMessage generateWithStop(List<ChatMessage> messages, List<String> stopSequences);
    
    default ChatMessage generateWithStop(List<ChatMessage> messages, List<String> stopSequences, Map<String, Object> additionalArgs) {
        return generateWithStop(messages, stopSequences);
    }
    
    default Stream<ChatMessageStreamDelta> generateStream(List<ChatMessage> messages) {
        throw new UnsupportedOperationException("Streaming not supported");
    }
    
    default Stream<ChatMessageStreamDelta> generateStream(List<ChatMessage> messages, List<Tool> tools) {
        throw new UnsupportedOperationException("Streaming not supported");
    }
    
    String getModelId();
    
    default boolean supportsStopParameter() {
        return true;
    }
    
    /**
     * 流式事件增量
     */
    record ChatMessageStreamDelta(
        String content,
        List<ToolCall> toolCalls,
        TokenUsage tokenUsage
    ) {}
}