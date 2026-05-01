package io.sketch.mochaagents.models;

import io.sketch.mochaagents.tools.Tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Azure OpenAI deployments share the same Chat Completions schema as OpenAI.
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
        return generate(messages, List.of(), ResponseFormat.text());
    }

    @Override
    public ChatMessage generate(List<ChatMessage> messages, List<Tool> tools) {
        return generate(messages, tools, ResponseFormat.text());
    }

    @Override
    public ChatMessage generate(List<ChatMessage> messages, List<Tool> tools, ResponseFormat format) {
        return generate(messages, tools, List.of(), format, Map.of());
    }

    @Override
    public ChatMessage generate(
        List<ChatMessage> messages,
        List<Tool> tools,
        List<String> stopSequences,
        ResponseFormat format,
        Map<String, Object> extraParameters
    ) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("messages",
                messages.stream()
                    .map(m -> {
                        try {
                            return OpenAiChatPayload.toRequestMessage(m, mapper);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList()));

            if (tools != null && !tools.isEmpty()) {
                requestBody.put("tools", tools.stream().map(OpenAiChatPayload::toToolSchema).toList());
                requestBody.put("tool_choice", "auto");
            }

            if (stopSequences != null && !stopSequences.isEmpty()) {
                requestBody.put("stop", stopSequences);
            }

            if (extraParameters != null) {
                mergeOpenAiExtras(requestBody, extraParameters);
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
                return extractChoice(responseMap);
            }
        } catch (Exception e) {
            throw new RuntimeException("Model generation failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeOpenAiExtras(Map<String, Object> body, Map<String, Object> extraParameters) {
        for (Map.Entry<String, Object> e : extraParameters.entrySet()) {
            String k = e.getKey();
            if ("response_format".equals(k)) {
                Object v = e.getValue();
                if ("json".equals(v)) {
                    body.put("response_format", Map.of("type", "json_object"));
                } else if (v instanceof Map<?, ?> m) {
                    body.put("response_format", new LinkedHashMap<>((Map<String, Object>) m));
                } else if (v != null) {
                    body.put(k, v);
                }
                continue;
            }
            body.put(k, e.getValue());
        }
    }

    @SuppressWarnings("unchecked")
    private ChatMessage extractChoice(Map<String, Object> response) throws Exception {
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("No choices in response");
        }
        Map<String, Object> choice = choices.get(0);
        Map<String, Object> message = (Map<String, Object>) choice.get("message");
        return OpenAiChatPayload.parseAssistantMessage(message, mapper);
    }

    @Override
    public String getModelId() {
        return deploymentId;
    }

    @Override
    public ChatMessage generateWithStop(List<ChatMessage> messages, List<String> stopSequences) {
        return generate(messages, List.of(), stopSequences, ResponseFormat.text(), Map.of());
    }
}
