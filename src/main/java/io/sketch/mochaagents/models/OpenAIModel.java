package io.sketch.mochaagents.models;

import io.sketch.mochaagents.tools.Tool;
import io.sketch.mochaagents.tools.ToolInput;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 模型实现
 */
public class OpenAIModel implements Model {
    
    private final String apiKey;
    private final String baseUrl;
    private final String modelId;
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    
    private OpenAIModel(Builder builder) {
        this.modelId = builder.modelId;
        this.apiKey = builder.apiKey;
        this.baseUrl = builder.baseUrl != null ? builder.baseUrl : "https://api.openai.com/v1";
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
        private String baseUrl;
        
        public Builder modelId(String modelId) {
            this.modelId = modelId;
            return this;
        }
        
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }
        
        public Builder apiBase(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }
        
        public OpenAIModel build() {
            return new OpenAIModel(this);
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
            "parameters", buildParameters(tool.getInputs())
        ));
        return schema;
    }
    
    private Map<String, Object> buildParameters(Map<String, ToolInput> inputs) {
        Map<String, Object> params = new HashMap<>();
        params.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();
        
        for (Map.Entry<String, ToolInput> entry : inputs.entrySet()) {
            properties.put(entry.getKey(), Map.of(
                "type", mapType(entry.getValue().type()),
                "description", entry.getValue().description()
            ));
            if (!entry.getValue().nullable()) {
                required.add(entry.getKey());
            }
        }
        
        params.put("properties", properties);
        params.put("required", required);
        
        return params;
    }
    
    private String mapType(String type) {
        return switch (type) {
            case "integer" -> "integer";
            case "number" -> "number";
            case "boolean" -> "boolean";
            case "array" -> "array";
            case "object" -> "object";
            default -> "string";
        };
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
        
        List<ToolCall> toolCalls = new ArrayList<>();
        if (message.containsKey("tool_calls")) {
            List<Map<String, Object>> tcList = (List<Map<String, Object>>) message.get("tool_calls");
            for (Map<String, Object> tc : tcList) {
                Map<String, Object> func = (Map<String, Object>) tc.get("function");
                toolCalls.add(new ToolCall(
                    (String) tc.get("id"),
                    ToolCallFunction.of(
                        (String) func.get("name"),
                        (Map<String, Object>) func.get("arguments")
                    )
                ));
            }
        }
        
        return new ChatMessage(
            MessageRole.valueOf(role),
            List.of(new ChatMessageContent.TextContent(content != null ? content : "")),
            toolCalls.isEmpty() ? null : toolCalls,
            null
        );
    }
}