package io.sketch.mochaagents.agents;

import java.util.Map;

/**
 * Pluggable minimal template renderer (subset of smolagents Jinja usage: <code>{{ var }}</code> and <code>{var}</code>).
 */
@FunctionalInterface
public interface PromptRendering {
    String render(String template, Map<String, ?> variables);

    static PromptRendering defaultRenderer() {
        return (template, variables) -> {
            if (template == null || template.isEmpty()) {
                return "";
            }
            String out = template;
            if (variables != null) {
                for (Map.Entry<String, ?> e : variables.entrySet()) {
                    String val = String.valueOf(e.getValue());
                    out = out.replace("{{ " + e.getKey() + " }}", val);
                    out = out.replace("{{" + e.getKey() + "}}", val);
                    out = out.replace("{" + e.getKey() + "}", val);
                }
            }
            return out;
        };
    }
}
