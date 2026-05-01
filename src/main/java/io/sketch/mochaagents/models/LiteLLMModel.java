package io.sketch.mochaagents.models;

import io.sketch.mochaagents.tools.Tool;

import java.util.List;
import java.util.Map;

/**
 * Thin adapter over {@link OpenAIModel} for LiteLLM-compatible gateways (same wire format).
 */
public class LiteLLMModel implements Model {

    private final OpenAIModel delegate;

    public LiteLLMModel(String modelId) {
        this(modelId, System.getenv("OPENAI_API_KEY"), "https://api.litellm.ai");
    }

    public LiteLLMModel(String modelId, String apiKey) {
        this(modelId, apiKey, "https://api.litellm.ai");
    }

    public LiteLLMModel(String modelId, String apiKey, String baseUrl) {
        this.delegate = OpenAIModel.builder()
            .modelId(modelId)
            .apiKey(apiKey)
            .apiBase(baseUrl)
            .build();
    }

    @Override
    public ChatMessage generate(List<ChatMessage> messages) {
        return delegate.generate(messages);
    }

    @Override
    public ChatMessage generate(List<ChatMessage> messages, List<Tool> tools) {
        return delegate.generate(messages, tools);
    }

    @Override
    public ChatMessage generate(List<ChatMessage> messages, List<Tool> tools, ResponseFormat format) {
        return delegate.generate(messages, tools, format);
    }

    @Override
    public ChatMessage generate(
        List<ChatMessage> messages,
        List<Tool> tools,
        List<String> stopSequences,
        ResponseFormat format,
        Map<String, Object> extraParameters
    ) {
        return delegate.generate(messages, tools, stopSequences, format, extraParameters);
    }

    @Override
    public String getModelId() {
        return delegate.getModelId();
    }

    @Override
    public ChatMessage generateWithStop(List<ChatMessage> messages, List<String> stopSequences) {
        return delegate.generateWithStop(messages, stopSequences);
    }
}
