package io.sketch.mochaagents.types;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Input/output normalization for {@link AgentType}, mirroring smolagents
 * {@code handle_agent_input_types} and {@code handle_agent_output_types}.
 */
public final class AgentTypeHandlers {

    private AgentTypeHandlers() {}

    /**
     * Replaces top-level {@link AgentType} values with {@link AgentType#toRaw()} (Python
     * {@code handle_agent_input_types} for kwargs).
     */
    public static Map<String, Object> unwrapArguments(Map<String, Object> kwargs) {
        if (kwargs == null || kwargs.isEmpty()) {
            return kwargs;
        }
        LinkedHashMap<String, Object> out = new LinkedHashMap<>(kwargs.size());
        for (Map.Entry<String, Object> e : kwargs.entrySet()) {
            Object v = e.getValue();
            if (v instanceof AgentType at) {
                v = at.toRaw();
            }
            out.put(e.getKey(), v);
        }
        return out;
    }

    public static Object unwrapValue(Object value) {
        if (value instanceof AgentType at) {
            return at.toRaw();
        }
        return value;
    }

    /**
     * Wraps tool outputs according to declared {@code outputType} or heuristics, matching Python
     * {@code handle_agent_output_types}.
     */
    public static Object handleAgentOutputTypes(Object output, String outputType) {
        if (output == null) {
            return null;
        }
        if (output instanceof AgentType) {
            return output;
        }
        if (outputType != null) {
            return switch (outputType) {
                case "string" -> new AgentText(Objects.toString(output, ""));
                case "image" -> AgentImage.coerce(output);
                case "audio" -> AgentAudio.coerce(output);
                default -> output;
            };
        }
        if (output instanceof String s) {
            return new AgentText(s);
        }
        return output;
    }
}
