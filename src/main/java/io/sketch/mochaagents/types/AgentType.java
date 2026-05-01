package io.sketch.mochaagents.types;

/**
 * Base contract for multimodal values moving through tools and memory, aligned with Hugging Face
 * smolagents {@code AgentType}.
 *
 * <p>Semantics mirror Python:</p>
 * <ul>
 *   <li>{@link #toRaw()} — value for native processing (decode / model input)</li>
 *   <li>{@link #toSerializedForm()} — string form suitable for prompts and logs (often a filesystem path)</li>
 * </ul>
 */
public sealed interface AgentType permits AgentText, AgentImage, AgentAudio {

    /** Raw JVM form (text as {@link String}, image as encoded {@code byte[]}, audio as waveform metadata + samples). */
    Object toRaw();

    /**
     * String representation for persistence and LLM context; for media this is usually a path to a temp file
     * (same idea as Python {@code AgentImage.to_string} / {@code AgentAudio.to_string}).
     */
    String toSerializedForm();
}
