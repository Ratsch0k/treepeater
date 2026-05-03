package treepeater.ai.anthropic;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.OutputConfig;

import treepeater.ai.StreamingChatClient;
import treepeater.ai.model.EffortLevel;
import treepeater.ai.model.LlmModelDefinition;
import treepeater.ai.model.LlmModelOptionValues;
import treepeater.ai.model.LlmProvider;
import treepeater.ai.model.ModelOptions;
import treepeater.settings.TreepeaterSettings;

/**
 * Anthropic Messages API provider. Declares Opus 4.7 / Sonnet 4.6 / Haiku 4.5 with their per-model
 * thinking and effort capabilities, and is the only place that touches Anthropic SDK enums.
 */
public final class AnthropicProvider implements LlmProvider {
    public static final String ID = "anthropic";

    private static final List<EffortLevel> ANTHROPIC_EFFORT_RANGE =
            List.of(EffortLevel.LOW, EffortLevel.MEDIUM, EffortLevel.HIGH, EffortLevel.MAX);

    private final List<LlmModelDefinition> models;

    public AnthropicProvider() {
        this.models = List.of(
                buildAdaptiveThinkingModel(Model.CLAUDE_OPUS_4_7.asString(), "Opus 4.7"),
                buildAdaptiveThinkingModel(Model.CLAUDE_SONNET_4_6.asString(), "Sonnet 4.6"),
                buildNoFeatureModel(Model.CLAUDE_HAIKU_4_5.asString(), "Haiku 4.5"));
    }

    private LlmModelDefinition buildAdaptiveThinkingModel(String modelId, String displayName) {
        return new LlmModelDefinition(
                this,
                modelId,
                displayName,
                List.of(ModelOptions.EFFORT, ModelOptions.EXTENDED_THINKING),
                Map.of(ModelOptions.EFFORT, ANTHROPIC_EFFORT_RANGE),
                LlmModelOptionValues.of(
                        ModelOptions.EFFORT, EffortLevel.MEDIUM,
                        ModelOptions.EXTENDED_THINKING, Boolean.TRUE));
    }

    private LlmModelDefinition buildNoFeatureModel(String modelId, String displayName) {
        return new LlmModelDefinition(
                this,
                modelId,
                displayName,
                List.of(),
                Map.of(),
                LlmModelOptionValues.EMPTY);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Anthropic";
    }

    @Override
    public List<LlmModelDefinition> models() {
        return this.models;
    }

    @Override
    public Optional<UnavailableReason> unavailableReason(LlmModelDefinition model) {
        String key = TreepeaterSettings.getInstance().getLlmAnthropicApiKey();
        if (key == null || key.isBlank()) {
            return Optional.of(new UnavailableReason(
                    "Anthropic API key required",
                    "Add your Anthropic API key under Extension settings for Treepeater "
                            + "(LLMs \u2192 Anthropic)."));
        }
        return Optional.empty();
    }

    @Override
    public StreamingChatClient createClient(LlmModelDefinition model, LlmModelOptionValues values) {
        String apiKey = TreepeaterSettings.getInstance().getLlmAnthropicApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Anthropic API key not configured");
        }
        if (model == null || model.modelId().isBlank()) {
            throw new IllegalStateException("No Anthropic model id");
        }
        boolean supportsEffort = model.supportedOptions().contains(ModelOptions.EFFORT);
        Optional<OutputConfig.Effort> outputEffort = supportsEffort
                ? Optional.of(
                        mapEffort(values != null
                                ? values.getOrDefault(ModelOptions.EFFORT, EffortLevel.MEDIUM)
                                : EffortLevel.MEDIUM))
                : Optional.empty();
        boolean wantsThinking =
                values != null && Boolean.TRUE.equals(values.get(ModelOptions.EXTENDED_THINKING));
        AnthropicClientConfig.ThinkingMode mode =
                thinkingModeFor(model, wantsThinking);
        return new AnthropicStreamingChatClient(
                new AnthropicClientConfig(apiKey, model.modelId(), mode, outputEffort));
    }

    /**
     * Adaptive for the catalog models that support {@link ModelOptions#EXTENDED_THINKING}; OFF
     * otherwise. Older 4.x non-adaptive thinking models are not in the current catalog, so
     * {@link AnthropicClientConfig.ThinkingMode#FIXED_BUDGET} is unused here today but remains
     * available for future entries.
     */
    static AnthropicClientConfig.ThinkingMode thinkingModeFor(LlmModelDefinition model, boolean wantsThinking) {
        if (!wantsThinking) {
            return AnthropicClientConfig.ThinkingMode.OFF;
        }
        if (model.supportedOptions().contains(ModelOptions.EXTENDED_THINKING)) {
            return AnthropicClientConfig.ThinkingMode.ADAPTIVE;
        }
        return AnthropicClientConfig.ThinkingMode.OFF;
    }

    /**
     * Anthropic supports {@code LOW..MAX}; {@link EffortLevel#MINIMAL} is clipped to {@code LOW}.
     */
    static OutputConfig.Effort mapEffort(EffortLevel level) {
        return switch (level) {
            case MINIMAL, LOW -> OutputConfig.Effort.LOW;
            case MEDIUM -> OutputConfig.Effort.MEDIUM;
            case HIGH -> OutputConfig.Effort.HIGH;
            case MAX -> OutputConfig.Effort.MAX;
        };
    }

}
