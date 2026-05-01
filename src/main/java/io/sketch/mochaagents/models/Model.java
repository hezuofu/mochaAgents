package io.sketch.mochaagents.models;

import io.sketch.mochaagents.tools.Tool;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public interface Model {

    ChatMessage generate(List<ChatMessage> messages);

    ChatMessage generate(List<ChatMessage> messages, List<Tool> tools);

    ChatMessage generate(List<ChatMessage> messages, List<Tool> tools, ResponseFormat format);

    /**
     * smolagents-style completion: stops + optional extra JSON keys (e.g. OpenAI {@code response_format}).
     * Backends without stop/tool support throw {@link UnsupportedOperationException}.
     */
    default ChatMessage generate(
        List<ChatMessage> messages,
        List<Tool> tools,
        List<String> stopSequences,
        ResponseFormat format,
        Map<String, Object> extraParameters
    ) {
        if (isAbsent(stopSequences) && isAbsent(extraParameters)) {
            return generate(messages, tools, format);
        }
        throw new UnsupportedOperationException(
            modelLabel() + " does not implement stop sequences or extra completion parameters");
    }

    private static boolean isAbsent(List<String> stopSequences) {
        return stopSequences == null || stopSequences.isEmpty();
    }

    private static boolean isAbsent(Map<String, Object> extraParameters) {
        return extraParameters == null || extraParameters.isEmpty();
    }

    default String modelLabel() {
        return getClass().getSimpleName();
    }

    default ChatMessage generate(ChatCompletionRequest request) {
        return generate(
            request.messages(),
            request.tools(),
            request.stopSequences(),
            request.responseFormat(),
            request.extraParameters()
        );
    }

    ChatMessage generateWithStop(List<ChatMessage> messages, List<String> stopSequences);

    /**
     * @deprecated Prefer {@link #generate(List, List, List, ResponseFormat, Map)}.
     */
    default ChatMessage generateWithStop(List<ChatMessage> messages,
                                         List<String> stopSequences,
                                         ResponseFormat format,
                                         Map<String, Object> additionalArgs) {
        return generate(messages, List.of(), stopSequences, format, additionalArgs);
    }

    /**
     * When the backend omits structured tool_calls, parse from assistant text (smolagents {@code parse_tool_calls}).
     */
    default ChatMessage parseToolCalls(ChatMessage message) {
        return message;
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

    record ChatCompletionRequest(
        List<ChatMessage> messages,
        List<Tool> tools,
        List<String> stopSequences,
        ResponseFormat responseFormat,
        Map<String, Object> extraParameters
    ) {
        public static ChatCompletionRequest of(List<ChatMessage> messages, List<Tool> tools) {
            return new ChatCompletionRequest(messages, tools, List.of(), ResponseFormat.text(), Map.of());
        }
    }
}
