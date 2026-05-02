package treepeater.ai.openai;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.openai.models.ChatModel;
import com.openai.models.ReasoningEffort;

import treepeater.ai.StreamingChatClient;
import treepeater.ai.model.EffortLevel;
import treepeater.ai.model.LlmModelDefinition;
import treepeater.ai.model.LlmModelOptionValues;
import treepeater.ai.model.LlmProvider;
import treepeater.ai.model.ModelOptions;
import treepeater.settings.TreepeaterSettings;

/**
 * Azure OpenAI / Foundry provider. Declares GPT-5.4 / GPT-5.4 mini / GPT-5.3 with {@code MINIMAL..HIGH}
 * effort range; this is the only place that touches the OpenAI SDK enums.
 */
public final class OpenAiProvider implements LlmProvider {
    public static final String ID = "openai";

    private static final List<EffortLevel> OPENAI_EFFORT_RANGE =
            List.of(EffortLevel.MINIMAL, EffortLevel.LOW, EffortLevel.MEDIUM, EffortLevel.HIGH);

    private final List<LlmModelDefinition> models;

    public OpenAiProvider() {
        this.models = List.of(
                build(ChatModel.GPT_5_4.asString(), "GPT-5.4"),
                build(ChatModel.GPT_5_4_MINI.asString(), "GPT-5.4 mini"),
                build(ChatModel.GPT_5_3_CHAT_LATEST.asString(), "GPT-5.3"));
    }

    private LlmModelDefinition build(String modelId, String displayName) {
        return new LlmModelDefinition(
                this,
                modelId,
                displayName,
                List.of(ModelOptions.EFFORT),
                Map.of(ModelOptions.EFFORT, OPENAI_EFFORT_RANGE),
                LlmModelOptionValues.of(ModelOptions.EFFORT, EffortLevel.MEDIUM));
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Azure OpenAI";
    }

    @Override
    public List<LlmModelDefinition> models() {
        return this.models;
    }

    @Override
    public Optional<UnavailableReason> unavailableReason(LlmModelDefinition model) {
        TreepeaterSettings s = TreepeaterSettings.getInstance();
        String endpoint = s.getLlmAzureOpenAiEndpoint();
        String key = s.getLlmAzureOpenAiApiKey();
        if (endpoint == null || endpoint.isBlank() || key == null || key.isBlank()) {
            return Optional.of(new UnavailableReason(
                    "Azure OpenAI configuration required",
                    "Add your Azure OpenAI / Foundry endpoint and API key under Extension settings "
                            + "for Treepeater (LLMs \u2192 Azure OpenAI / Foundry)."));
        }
        return Optional.empty();
    }

    @Override
    public StreamingChatClient createClient(LlmModelDefinition model, LlmModelOptionValues values) {
        TreepeaterSettings s = TreepeaterSettings.getInstance();
        String endpoint = s.getLlmAzureOpenAiEndpoint();
        String apiKey = s.getLlmAzureOpenAiApiKey();
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("Azure OpenAI endpoint not configured");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Azure OpenAI API key not configured");
        }
        if (model == null || model.modelId().isBlank()) {
            throw new IllegalStateException("No deployment name for Azure OpenAI");
        }
        EffortLevel effort = values != null
                ? values.getOrDefault(ModelOptions.EFFORT, EffortLevel.MEDIUM)
                : EffortLevel.MEDIUM;
        return new OpenAiStreamingChatClient(
                new OpenAiClientConfig(endpoint, apiKey, model.modelId(), mapEffort(effort)));
    }

    /** OpenAI supports {@code MINIMAL..HIGH}; {@link EffortLevel#MAX} is clipped to {@code HIGH}. */
    static ReasoningEffort mapEffort(EffortLevel level) {
        return switch (level) {
            case MINIMAL -> ReasoningEffort.MINIMAL;
            case LOW -> ReasoningEffort.LOW;
            case MEDIUM -> ReasoningEffort.MEDIUM;
            case HIGH, MAX -> ReasoningEffort.HIGH;
        };
    }
}
