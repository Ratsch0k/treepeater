package treepeater.ai.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import treepeater.ai.anthropic.AnthropicProvider;
import treepeater.ai.burp.BurpProvider;
import treepeater.ai.ollama.OllamaProvider;
import treepeater.ai.openai.OpenAiProvider;

/**
 * Static registry of {@link LlmProvider}s. The provider order here drives the order in the model
 * combo: Burp first (default), then Anthropic, then OpenAI, then Ollama.
 */
public final class LlmRegistry {
    private static final List<LlmProvider> PROVIDERS = List.of(
            new BurpProvider(),
            new AnthropicProvider(),
            new OpenAiProvider(),
            new OllamaProvider());

    private LlmRegistry() {}

    public static List<LlmProvider> providers() {
        return PROVIDERS;
    }

    /** Flat list of every model from every provider, in registry order. */
    public static List<LlmModelDefinition> allModels() {
        List<LlmModelDefinition> all = new ArrayList<>();
        for (LlmProvider p : PROVIDERS) {
            all.addAll(p.models());
        }
        return all;
    }

    public static Optional<LlmProvider> providerById(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (LlmProvider p : PROVIDERS) {
            if (p.id().equals(id)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolve a persisted ref to a live model. Falls back to the provider's
     * {@link LlmProvider#synthesize} (currently used by {@code OllamaProvider} for user-typed model
     * names that aren't in the current settings list).
     */
    public static Optional<LlmModelDefinition> resolve(LlmModelRef ref) {
        if (ref == null) {
            return Optional.empty();
        }
        Optional<LlmProvider> provider = providerById(ref.providerId());
        if (provider.isEmpty()) {
            return Optional.empty();
        }
        for (LlmModelDefinition def : provider.get().models()) {
            if (def.modelId().equals(ref.modelId())) {
                return Optional.of(def);
            }
        }
        return provider.get().synthesize(ref);
    }

    /** First registered model — used as the default when nothing has been selected yet. */
    public static LlmModelDefinition defaultModel() {
        return PROVIDERS.get(0).models().get(0);
    }
}
