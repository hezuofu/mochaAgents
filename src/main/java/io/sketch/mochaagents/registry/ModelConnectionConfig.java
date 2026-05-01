package io.sketch.mochaagents.registry;

/**
 * Credential / endpoint wiring for LLM backends, independent of concrete {@link io.sketch.mochaagents.models.Model}
 * implementations.
 */
public record ModelConnectionConfig(
    String modelId,
    String apiBase,
    String apiKey,
    String provider
) {
    /** Merge explicit non-blank overrides with fallback values from environment/properties. */
    public ModelConnectionConfig withResolvedSecrets(
        String fallbackApiKey,
        String fallbackProvider
    ) {
        String ak = apiKey != null && !apiKey.isBlank() ? apiKey : fallbackApiKey;
        String pv = provider != null && !provider.isBlank() ? provider : fallbackProvider;
        return new ModelConnectionConfig(modelId, apiBase, ak, pv);
    }
}
