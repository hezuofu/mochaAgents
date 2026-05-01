package io.sketch.mochaagents.models;

import io.sketch.mochaagents.tools.Tool;
import io.sketch.mochaagents.tools.ToolInput;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serialization helpers for OpenAI-compatible Chat Completions (used by OpenAI / Azure OpenAI / many LiteLLM backends).
 */
public final class OpenAiChatPayload {

    private OpenAiChatPayload() {}

    public static Map<String, Object> toRequestMessage(ChatMessage message, ObjectMapper mapper) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        MessageRole r = message.role();
        boolean hasAssistantToolCalls =
            message.toolCalls() != null && !message.toolCalls().isEmpty()
                && (r == MessageRole.ASSISTANT || r == MessageRole.TOOL_CALL);

        String openAiRole = switch (r) {
            case TOOL_RESPONSE -> "user";
            default -> r.name().toLowerCase();
        };
        map.put("role", openAiRole);

        if (hasAssistantToolCalls) {
            String text = message.getTextContent();
            map.put("content", text.isBlank() ? null : text);
            map.put("tool_calls", serializeToolCalls(message.toolCalls(), mapper));
            map.put("role", "assistant");
            return map;
        }

        map.put("content", message.getTextContent());
        return map;
    }

    private static List<Map<String, Object>> serializeToolCalls(List<ToolCall> calls, ObjectMapper mapper) throws Exception {
        List<Map<String, Object>> tc = new ArrayList<>();
        for (ToolCall toolCall : calls) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", toolCall.id());
            entry.put("type", "function");
            Map<String, Object> fn = new LinkedHashMap<>();
            fn.put("name", toolCall.function().name());
            Map<String, Object> args = toolCall.function().arguments();
            if (args == null) {
                args = Map.of();
            }
            fn.put("arguments", mapper.writeValueAsString(args));
            entry.put("function", fn);
            tc.add(entry);
        }
        return tc;
    }

    public static Map<String, Object> toToolSchema(Tool tool) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "function");
        schema.put("function", Map.of(
            "name", tool.getName(),
            "description", tool.getDescription(),
            "parameters", buildParameters(tool.getInputs())
        ));
        return schema;
    }

    private static Map<String, Object> buildParameters(Map<String, ToolInput> inputs) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
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

    private static String mapType(String type) {
        return switch (type) {
            case "integer" -> "integer";
            case "number" -> "number";
            case "boolean" -> "boolean";
            case "array" -> "array";
            case "object" -> "object";
            default -> "string";
        };
    }

    @SuppressWarnings("unchecked")
    public static ChatMessage parseAssistantMessage(Map<String, Object> message, ObjectMapper mapper) throws Exception {
        String role = ((String) message.get("role")).toUpperCase();
        Object contentRaw = message.get("content");

        List<ToolCall> toolCalls = new ArrayList<>();
        if (message.containsKey("tool_calls")) {
            List<Map<String, Object>> tcList = (List<Map<String, Object>>) message.get("tool_calls");
            for (Map<String, Object> tc : tcList) {
                Map<String, Object> func = (Map<String, Object>) tc.get("function");
                String fname = (String) func.get("name");
                Object argsObj = func.get("arguments");
                Map<String, Object> argsMap = coerceArgumentsMap(argsObj, mapper);
                toolCalls.add(new ToolCall(
                    (String) tc.get("id"),
                    ToolCallFunction.of(fname, argsMap == null ? Map.of() : argsMap)
                ));
            }
        }

        String text = "";
        if (contentRaw instanceof String s) {
            text = s != null ? s : "";
        } else if (contentRaw instanceof List<?> parts) {
            StringBuilder sb = new StringBuilder();
            for (Object p : parts) {
                if (p instanceof Map<?, ?> chunk) {
                    Object t = chunk.get("text");
                    if (t instanceof String ts) {
                        sb.append(ts);
                    }
                }
            }
            text = sb.toString();
        }

        return new ChatMessage(
            MessageRole.valueOf(role),
            List.of(new ChatMessageContent.TextContent(text)),
            toolCalls.isEmpty() ? null : toolCalls,
            null
        );
    }

    /**
     * OpenAI sends <code>function.arguments</code> as JSON string; some mocks use a parsed map directly.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> coerceArgumentsMap(Object raw, ObjectMapper mapper) throws Exception {
        if (raw == null || raw instanceof Map) {
            return raw instanceof Map<?, ?> mm ? new LinkedHashMap<>((Map<String, Object>) mm) : Map.of();
        }
        if (raw instanceof String s) {
            s = s.trim();
            if (s.isEmpty()) {
                return Map.of();
            }
            return mapper.readValue(s, new TypeReference<Map<String, Object>>() {});
        }
        return Map.of("value", raw);
    }
}
