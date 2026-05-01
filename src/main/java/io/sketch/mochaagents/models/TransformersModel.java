package io.sketch.mochaagents.models;

import io.sketch.mochaagents.tools.Tool;

import java.util.List;

public class TransformersModel implements Model {
    private final String modelId;
    private final String deviceMap;
    private final int maxNewTokens;

    public TransformersModel(String modelId, String deviceMap, int maxNewTokens) {
        this.modelId = modelId;
        this.deviceMap = deviceMap;
        this.maxNewTokens = maxNewTokens;
    }

    @Override
    public ChatMessage generate(List<ChatMessage> messages) {
        return new ChatMessage(
            MessageRole.ASSISTANT,
            List.of(new ChatMessageContent.TextContent("Simulated response from TransformersModel: " + modelId)),
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
