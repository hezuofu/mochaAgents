package io.sketch.mochaagents.types;

import java.util.Objects;

/**
 * Text wrapper returned by tools, aligned with smolagents {@code AgentText}.
 */
public final class AgentText implements AgentType {

    private final String value;

    public AgentText(String value) {
        this.value = Objects.requireNonNullElse(value, "");
    }

    @Override
    public String toRaw() {
        return value;
    }

    @Override
    public String toSerializedForm() {
        return value;
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return toSerializedForm();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AgentText agentText = (AgentText) o;
        return value.equals(agentText.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
