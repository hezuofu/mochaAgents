package io.sketch.mochaagents.registry;

import io.sketch.mochaagents.models.AzureOpenAIModel;
import io.sketch.mochaagents.models.HuggingFaceModel;
import io.sketch.mochaagents.models.Model;
import io.sketch.mochaagents.models.OpenAIModel;

import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of LLM backends (strategy-per-key). Callers resolve a logical type string from YAML/CLI
 * into a wired {@link Model} without scattering {@code switch} expressions.
 */
public final class ModelRegistry {

    @FunctionalInterface
    public interface ModelFactory {
        Model create(ModelConnectionConfig config);
    }

    private static final Map<String, ModelFactory> REGISTRY = new ConcurrentHashMap<>();

    static {
        registerCanonical("OpenAIModel", cfg -> OpenAIModel.builder()
            .apiKey(firstNonBlank(cfg.apiKey(), System.getenv("FIREWORKS_API_KEY")))
            .apiBase(firstNonBlank(cfg.apiBase(), "https://api.fireworks.ai/inference/v1"))
            .modelId(cfg.modelId())
            .build());
        registerCanonical("AzureOpenAIModel", cfg -> AzureOpenAIModel.builder()
            .apiKey(firstNonBlank(cfg.apiKey(), System.getenv("AZURE_OPENAI_API_KEY")))
            .apiBase(cfg.apiBase())
            .modelId(cfg.modelId())
            .build());
        registerCanonical("HuggingFaceModel", ModelRegistry::hfModel);
        registerCanonical("InferenceClientModel", ModelRegistry::hfModel);
    }

    private ModelRegistry() {}

    public static void registerCanonical(String canonicalTypeKey, ModelFactory factory) {
        Objects.requireNonNull(canonicalTypeKey);
        Objects.requireNonNull(factory);
        REGISTRY.put(canonicalTypeKey, factory);
    }

    /** Case-insensitive key (smolagents style CLI flags). Unknown keys → {@code InferenceClientModel}. */
    public static Model create(String declaredType, ModelConnectionConfig cfg) {
        Objects.requireNonNull(cfg, "config");
        String key = canonicalize(declaredType);
        ModelFactory f = REGISTRY.get(key);
        if (f == null) {
            f = REGISTRY.get("InferenceClientModel");
        }
        return Objects.requireNonNull(f, "InferenceClient factory missing").create(cfg);
    }

    /** HuggingFace / HF Inference Providers channel (also used when CLI selects InferenceClientModel). */
    private static Model hfModel(ModelConnectionConfig raw) {
        ModelConnectionConfig cfg = raw.withResolvedSecrets(System.getenv("HF_API_KEY"), null);
        HuggingFaceModel.Builder b =
            HuggingFaceModel.builder()
                .modelId(cfg.modelId())
                .apiKey(cfg.apiKey());
        if (cfg.provider() != null && !cfg.provider().isBlank()) {
            b.provider(cfg.provider());
        }
        return b.build();
    }

    private static String canonicalize(String declaredType) {
        if (declaredType == null || declaredType.isBlank()) {
            return "InferenceClientModel";
        }
        String trimmed = declaredType.strip();
        for (String canon : REGISTRY.keySet()) {
            if (canon.equalsIgnoreCase(trimmed)) {
                return canon;
            }
        }
        return trimmed;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null ? b : "";
    }

    /** Keys currently registered (for diagnostics / `--help`). */
    public static Set<String> registeredTypes() {
        return Set.copyOf(REGISTRY.keySet());
    }
}
