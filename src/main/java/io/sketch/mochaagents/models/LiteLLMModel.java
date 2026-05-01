package io.sketch.mochaagents.models;

import io.sketch.mochaagents.tools.Tool;
import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LiteLLM 模型实现
 */
public class LiteLLMModel implements Model {
    
    private final String apiKey;
    private final String baseUrl;
    private final String modelId;
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    
    public LiteLLMModel(String modelId) {
        this(modelId, System.getenv("OPENAI_API_KEY"), "https://api.litellm.ai");
    }
    
    public LiteLLMModel(String modelId, String apiKey) {
        this(modelId, apiKey, "https://api.litellm.ai");
    }
    
    public LiteLLMModel(String modelId, String apiKey, String baseUrl) {
        this.modelId = modelId;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(30))
            .readTimeout(java.time.Duration.ofSeconds(60))
            .build();
        this.mapper = new ObjectMapper();
    }
    
    @Override
    public ChatMessage generate(List<ChatMessage> messages) {
        return generate(messages, List.of());
    }
    
    @Override
    public ChatMessage generate(List<ChatMessage> messages, List<Tool> tools) {
        return generate(messages, tools, ResponseFormat.text());
    }
    
    @Override
    public ChatMessage generate(List<ChatMessage> messages, List<Tool> tools, ResponseFormat format) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelId);
            requestBody.put("messages", messages.stream().map(this::toMessageMap).toList());
            
            if (!tools.isEmpty()) {
                requestBody.put("tools", tools.stream().map(this::toToolSchema).toList());
            }
            
            RequestBody body = RequestBody.create(
                mapper.writeValueAsString(requestBody),
                MediaType.parse("application/json")
            );
            
            Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("API call failed: " + response);
                }
                
                String json = response.body().string();
                Map<String, Object> responseMap = mapper.readValue(json, Map.class);
                
                return parseResponse(responseMap);
            }
        } catch (Exception e) {
            throw new RuntimeException("Model generation failed", e);
        }
    }
    
    @Override
    public String getModelId() {
        return modelId;
    }
    
    @Override
    public ChatMessage generateWithStop(List<ChatMessage> messages, List<String> stopSequences) {
        return generate(messages, List.of());
    }
    
    private Map<String, Object> toMessageMap(ChatMessage message) {
        Map<String, Object> map = new HashMap<>();
        map.put("role", message.role().name().toLowerCase());
        map.put("content", message.getTextContent());
        return map;
    }
    
    private Map<String, Object> toToolSchema(Tool tool) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "function");
        schema.put("function", Map.of(
            "name", tool.getName(),
            "description", tool.getDescription(),
            "parameters", Map.of("type", "object")
        ));
        return schema;
    }
    
    private ChatMessage parseResponse(Map<String, Object> response) {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("No choices in response");
        }
        
        Map<String, Object> choice = choices.get(0);
        Map<String, Object> message = (Map<String, Object>) choice.get("message");
        
        String role = ((String) message.get("role")).toUpperCase();
        String content = (String) message.get("content");
        
        return new ChatMessage(
            MessageRole.valueOf(role),
            List.of(new ChatMessageContent.TextContent(content != null ? content : "")),
            null,
            null
        );
    }
}