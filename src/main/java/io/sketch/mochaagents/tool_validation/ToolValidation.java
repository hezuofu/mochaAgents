package io.sketch.mochaagents.tool_validation;

import io.sketch.mochaagents.tools.Tool;
import io.sketch.mochaagents.tools.ToolDefinition;
import io.sketch.mochaagents.tools.ToolInput;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Structural checks for {@link Tool} instances, mirroring part of smolagents {@code tool_validation}
 * (name hygiene, schema presence, input key validity).
 */
public final class ToolValidation {

    private static final Pattern LEGAL_NAME = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final Pattern LEGAL_INPUT_KEY = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private ToolValidation() {}

    public static void validateToolOrThrow(Tool tool) {
        Objects.requireNonNull(tool, "tool");
        String name = tool.getName();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool name must be non-blank.");
        }
        if (!LEGAL_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                "Tool name must be a valid identifier [a-zA-Z_][a-zA-Z0-9_]*: '" + name + "'");
        }
        String desc = tool.getDescription();
        if (desc == null || desc.isBlank()) {
            throw new IllegalArgumentException("Tool '" + name + "' requires a non-blank description.");
        }
        Map<String, ToolInput> inputs = tool.getInputs();
        if (inputs == null) {
            throw new IllegalArgumentException("Tool '" + name + "' inputs map must not be null.");
        }
        for (String key : inputs.keySet()) {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("Tool '" + name + "' has a blank input key.");
            }
            if (!LEGAL_INPUT_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException(
                    "Tool '" + name + "' input key must be a valid identifier: '" + key + "'");
            }
            ToolInput spec = inputs.get(key);
            if (spec == null) {
                throw new IllegalArgumentException("Tool '" + name + "' missing schema for input '" + key + "'.");
            }
            if (spec.type() == null || spec.type().isBlank()) {
                throw new IllegalArgumentException(
                    "Tool '" + name + "' input '" + key + "' must declare a non-blank JSON-schema type string.");
            }
        }
        String out = tool.getOutputType();
        if (out == null || out.isBlank()) {
            throw new IllegalArgumentException("Tool '" + name + "' must declare outputType.");
        }
        ToolDefinition def = tool.toDefinition();
        if (def == null) {
            throw new IllegalArgumentException("Tool '" + name + "' toDefinition() returned null.");
        }
        if (!name.equals(def.name())) {
            throw new IllegalArgumentException(
                "Tool '" + name + "' name does not match ToolDefinition.name() '" + def.name() + "'.");
        }
    }
}
