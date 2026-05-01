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
 * HuggingFace Inference API 模型实现
 */
public class HuggingFaceModel implements Model {
    
    private final String apiKey;
    private final String modelId;
    private final String provider;
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    
    private HuggingFaceModel(Builder builder) {
        this.modelId = builder.modelId;
        this.apiKey = builder.apiKey;
        this.provider = builder.provider;
        this.client = new OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(60))
            .readTimeout(java.time.Duration.ofSeconds(120))
            .build();
        this.mapper = new ObjectMapper();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String modelId;
        private String apiKey;
        private String provider;
        
        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }
        
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }
        
        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }
        
        public HuggingFaceModel build() {
            return new HuggingFaceModel(this);
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
            
            String prompt = messages.stream()
                .map(this::formatMessage)
                .reduce("", String::concat);
            
            requestBody.put("inputs", prompt);
            
            RequestBody body = RequestBody.create(
                mapper.writeValueAsString(requestBody),
                MediaType.parse("application/json")
            );
            
            String url = "https://api-inference.huggingface.co/models/" + modelId;
            
            Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(body)
                .build();
            
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new RuntimeException("API call failed: " + response);
                }
                
                String json = response.body().string();
                List<Map<String, Object>> responseList = mapper.readValue(json, List.class);
                
                if (responseList.isEmpty()) {
                    throw new RuntimeException("Empty response");
                }
                
                String content = (String) responseList.get(0).get("generated_text");
                content = content != null ? content.replace(prompt, "").trim() : "";
                
                return new ChatMessage(
                    MessageRole.ASSISTANT,
                    List.of(new ChatMessageContent.TextContent(content)),
                    null,
                    null
                );
            }
        } catch (Exception e) {
            throw new RuntimeException("Model generation failed", e);
        }
    }
    
    @Override
    public ChatMessage generate(
        List<ChatMessage> messages,
        List<Tool> tools,
        List<String> stopSequences,
        ResponseFormat format,
        Map<String, Object> extraParameters
    ) {
        return generate(messages, tools, format);
    }

    @Override
    public String getModelId() {
        return modelId;
    }
    
    @Override
    public ChatMessage generateWithStop(List<ChatMessage> messages, List<String> stopSequences) {
        return generate(messages, List.of());
    }
    
    private String formatMessage(ChatMessage message) {
        return switch (message.role()) {
            case SYSTEM -> "<s>[INST] " + message.getTextContent() + " [/INST]";
            case USER -> "<s>[INST] " + message.getTextContent() + " [/INST]";
            case ASSISTANT -> " " + message.getTextContent() + " ";
            default -> message.getTextContent();
        };
    }
}