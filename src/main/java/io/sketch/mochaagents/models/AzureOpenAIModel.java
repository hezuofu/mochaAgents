package io.sketch.mochaagents.models;

import io.sketch.mochaagents.tools.Tool;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Azure OpenAI 模型实现
 */
public class AzureOpenAIModel implements Model {
    
    private final String apiKey;
    private final String endpoint;
    private final String deploymentId;
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    
    private AzureOpenAIModel(Builder builder) {
        this.deploymentId = builder.modelId;
        this.apiKey = builder.apiKey;
        this.endpoint = builder.endpoint;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(30))
            .readTimeout(java.time.Duration.ofSeconds(60))
            .build();
        this.mapper = new ObjectMapper();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String modelId;
        private String apiKey;
        private String endpoint;
        
        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }
        
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }
        
        public Builder apiBase(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }
        
        public AzureOpenAIModel build() {
            return new AzureOpenAIModel(this);
        }
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
            requestBody.put("messages", messages.stream().map(this::toMessageMap).toList());
            
            if (!tools.isEmpty()) {
                requestBody.put("tools", tools.stream().map(this::toToolSchema).toList());
            }
            
            RequestBody body = RequestBody.create(
                mapper.writeValueAsString(requestBody),
                MediaType.parse("application/json")
            );
            
            String url = String.format("%s/openai/deployments/%s/chat/completions?api-version=2024-02-15-preview",
                endpoint, deploymentId);
            
            Request request = new Request.Builder()
                .url(url)
                .header("api-key", apiKey)
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
        return deploymentId;
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
            "description", tool.getDescription()
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