package io.sketch.mochaagents.models;

import io.sketch.mochaagents.tools.Tool;

import java.util.List;

public class InferenceClientModel implements Model {
    private final String modelId;
    private final String provider;

    public InferenceClientModel() {
        this("meta-llama/Llama-3.3-70B-Instruct", null);
    }

    public InferenceClientModel(String modelId) {
        this(modelId, null);
    }

    public InferenceClientModel(String modelId, String provider) {
        this.modelId = modelId;
        this.provider = provider;
    }

    @Override
    public ChatMessage generate(List<ChatMessage> messages) {
        return new ChatMessage(
            MessageRole.ASSISTANT,
            List.of(new ChatMessageContent.TextContent("Simulated response from InferenceClientModel: " + modelId)),
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
