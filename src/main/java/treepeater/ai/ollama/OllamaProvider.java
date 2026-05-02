package treepeater.ai.ollama;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import treepeater.ai.StreamingChatClient;
import treepeater.ai.model.LlmModelDefinition;
import treepeater.ai.model.LlmModelOptionValues;
import treepeater.ai.model.LlmModelRef;
import treepeater.ai.model.LlmProvider;
import treepeater.settings.TreepeaterSettings;

/**
 * Ollama provider. Unlike the cloud providers, the model catalog is dynamic — it reflects whatever
 * the user configured under settings (or {@link #FALLBACK_MODELS} when nothing is configured).
 */
public final class OllamaProvider implements LlmProvider {
    public static final String ID = "ollama";

    /** Sensible defaults shown when the user hasn't configured an Ollama model list yet. */
    public static final String[] FALLBACK_MODELS = {"qwen3.5", "llama3.2", "mistral", "codellama"};

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Ollama";
    }

    @Override
    public List<LlmModelDefinition> models() {
        List<String> configured = configuredModels();
        List<LlmModelDefinition> result = new ArrayList<>(configured.size());
        for (String name : configured) {
            result.add(buildModel(name));
        }
        return result;
    }

    @Override
    public Optional<LlmModelDefinition> synthesize(LlmModelRef ref) {
        if (ref == null || !ID.equals(ref.providerId()) || ref.modelId().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(buildModel(ref.modelId()));
    }

    private LlmModelDefinition buildModel(String modelName) {
        return new LlmModelDefinition(
                this,
                modelName,
                modelName + " (Ollama)",
                List.of(),
                null,
                LlmModelOptionValues.EMPTY);
    }

    private static List<String> configuredModels() {
        try {
            List<String> configured = TreepeaterSettings.getInstance().getOllamaModels();
            if (configured != null) {
                return configured;
            }
        } catch (IllegalStateException ignored) {
        }
        return List.of(FALLBACK_MODELS);
    }

    @Override
    public Optional<UnavailableReason> unavailableReason(LlmModelDefinition model) {
        String base = TreepeaterSettings.getInstance().getLlmOllamaBaseUrl();
        if (base == null || base.isBlank()) {
            return Optional.of(new UnavailableReason(
                    "Ollama base URL required",
                    "Set the Ollama base URL under Extension settings for Treepeater "
                            + "(LLMs \u2192 Ollama)."));
        }
        return Optional.empty();
    }

    @Override
    public StreamingChatClient createClient(LlmModelDefinition model, LlmModelOptionValues values) {
        String baseUrl = TreepeaterSettings.getInstance().getLlmOllamaBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Ollama base URL not configured");
        }
        if (model == null || model.modelId().isBlank()) {
            throw new IllegalStateException("No Ollama model id");
        }
        return new OllamaStreamingChatClient(new OllamaClientConfig(baseUrl, model.modelId()));
    }
}
