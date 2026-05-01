package io.sketch.mochaagents.tools;

import java.util.Map;
import java.util.Objects;

/**
 * Minimal JSON-schema style validation of tool arguments against {@link Tool#getInputs()}.
 */
public final class ToolArgumentValidator {

    private ToolArgumentValidator() {}

    public static void validateOrThrow(Tool tool, Map<String, Object> arguments) {
        Objects.requireNonNull(tool, "tool");
        Map<String, ToolInput> schema = tool.getInputs();
        if (schema == null || schema.isEmpty()) {
            return;
        }
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        for (Map.Entry<String, ToolInput> e : schema.entrySet()) {
            String name = e.getKey();
            ToolInput spec = e.getValue();
            if (!spec.nullable() && (!args.containsKey(name) || args.get(name) == null)) {
                throw new IllegalArgumentException(
                    "Tool '%s' missing required argument '%s'".formatted(tool.getName(), name));
            }
            if (args.containsKey(name)) {
                validateValue(tool.getName(), name, spec, args.get(name));
            }
        }
    }

    private static void validateValue(String toolName, String argName, ToolInput spec, Object value) {
        if (value == null) {
            if (!spec.nullable()) {
                throw new IllegalArgumentException(
                    "Tool '%s' argument '%s' cannot be null".formatted(toolName, argName));
            }
            return;
        }
        String t = spec.type();
        if (t == null || "any".equalsIgnoreCase(t)) {
            return;
        }
        switch (t) {
            case "string" -> {
                if (!(value instanceof CharSequence)) {
                    throw new IllegalArgumentException(
                        "Tool '%s' argument '%s' expected string, got %s"
                            .formatted(toolName, argName, value.getClass().getSimpleName()));
                }
            }
            case "boolean" -> {
                if (!(value instanceof Boolean)) {
                    throw new IllegalArgumentException(
                        "Tool '%s' argument '%s' expected boolean, got %s"
                            .formatted(toolName, argName, value.getClass().getSimpleName()));
                }
            }
            case "integer" -> {
                if (!(value instanceof Number n) || n.doubleValue() != Math.floor(n.doubleValue())) {
                    throw new IllegalArgumentException(
                        "Tool '%s' argument '%s' expected integer, got %s"
                            .formatted(toolName, argName, value.getClass().getSimpleName()));
                }
            }
            case "number" -> {
                if (!(value instanceof Number)) {
                    throw new IllegalArgumentException(
                        "Tool '%s' argument '%s' expected number, got %s"
                            .formatted(toolName, argName, value.getClass().getSimpleName()));
                }
            }
            case "object", "array", "image", "audio", "null" -> {
                // Structural / binary types: presence and nullability only
            }
            default -> {
                // unknown type string — skip strict check
            }
        }
    }
}
