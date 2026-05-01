package io.sketch.mochaagents.models;

import io.sketch.mochaagents.tools.Tool;

import java.util.List;
import java.util.Map;

public class LiteLLMRouterModel implements Model {
    private final String modelId;
    private final List<Map<String, Object>> modelList;
    private final Map<String, Object> clientKwargs;

    public LiteLLMRouterModel(String modelId, List<Map<String, Object>> modelList, Map<String, Object> clientKwargs) {
        this.modelId = modelId;
        this.modelList = modelList;
        this.clientKwargs = clientKwargs;
    }

    @Override
    public ChatMessage generate(List<ChatMessage> messages) {
        return new ChatMessage(
            MessageRole.ASSISTANT,
            List.of(new ChatMessageContent.TextContent("Simulated response from LiteLLMRouterModel: " + modelId)),
            null,
            null
        );
    }

    @Override
    public ChatMessage generate(List<ChatMessage> messages, List<Tool> tools) {
        return generate(messages);
    }

    @Override
    public ChatMessage generate(List<ChatMessage> messages, List<Tool> tools, ResponseFormat format) {
        return generate(messages);
    }

    @Override
    public ChatMessage generateWithStop(List<ChatMessage> messages, List<String> stopSequences) {
        return generate(messages);
    }

    @Override
    public String getModelId() {
        return modelId;
    }
}
