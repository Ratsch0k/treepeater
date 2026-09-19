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
 * Azure OpenAI / Foundry provider. Declares GPT-6 Astra, the GPT-5.6 family, and the previous
 * GPT-5.5 / GPT-5.4 generation with per-model effort ranges; this is the only place that touches
 * the OpenAI SDK enums.
 */
public final class OpenAiProvider implements LlmProvider {
    public static final String ID = "openai";

    private static final List<EffortLevel> ASTRA_EFFORT_RANGE =
            List.of(EffortLevel.LOW, EffortLevel.MEDIUM, EffortLevel.HIGH, EffortLevel.XHIGH, EffortLevel.MAX);

    private static final List<EffortLevel> GPT56_EFFORT_RANGE =
            List.of(
                    EffortLevel.MINIMAL,
                    EffortLevel.LOW,
                    EffortLevel.MEDIUM,
                    EffortLevel.HIGH,
                    EffortLevel.XHIGH,
                    EffortLevel.MAX);

    private static final List<EffortLevel> GPT55_EFFORT_RANGE =
            List.of(
                    EffortLevel.MINIMAL,
                    EffortLevel.LOW,
                    EffortLevel.MEDIUM,
                    EffortLevel.HIGH,
                    EffortLevel.XHIGH);

    private final List<LlmModelDefinition> models;

    public OpenAiProvider() {
        this.models = List.of(
                build(ChatModel.of("gpt-6-astra").asString(), "GPT-6 Astra", ASTRA_EFFORT_RANGE),
                build(ChatModel.GPT_5_6_SOL.asString(), "GPT-5.6 Sol", GPT56_EFFORT_RANGE),
                build(ChatModel.GPT_5_6_TERRA.asString(), "GPT-5.6 Terra", GPT56_EFFORT_RANGE),
                build(ChatModel.GPT_5_6_LUNA.asString(), "GPT-5.6 Luna", GPT56_EFFORT_RANGE),
                build(ChatModel.GPT_5_5.asString(), "GPT-5.5", GPT55_EFFORT_RANGE),
                build(ChatModel.GPT_5_4.asString(), "GPT-5.4", GPT55_EFFORT_RANGE),
                build(ChatModel.GPT_5_4_MINI.asString(), "GPT-5.4 mini", GPT55_EFFORT_RANGE));
    }

    private LlmModelDefinition build(String modelId, String displayName, List<EffortLevel> effortRange) {
        return new LlmModelDefinition(
                this,
                modelId,
                displayName,
                List.of(ModelOptions.EFFORT),
                Map.of(ModelOptions.EFFORT, effortRange),
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

    /**
     * Maps generic effort to the Responses-API {@link ReasoningEffort}. {@link EffortLevel#MINIMAL}
     * is sent as {@code none} (GPT-5.5+ document {@code none}, not {@code minimal}).
     */
    static ReasoningEffort mapEffort(EffortLevel level) {
        return switch (level) {
            case MINIMAL -> ReasoningEffort.NONE;
            case LOW -> ReasoningEffort.LOW;
            case MEDIUM -> ReasoningEffort.MEDIUM;
            case HIGH -> ReasoningEffort.HIGH;
            case XHIGH -> ReasoningEffort.XHIGH;
            case MAX -> ReasoningEffort.MAX;
        };
    }

}
