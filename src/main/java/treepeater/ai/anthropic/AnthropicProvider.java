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
 * Anthropic Messages API provider. Declares Fable 5.1 / Opus 5 / Sonnet 5 / Haiku 4.5 plus the
 * previous-generation Opus 4.7 and Sonnet 4.6 fallbacks, and is the only place that touches
 * Anthropic SDK enums.
 */
public final class AnthropicProvider implements LlmProvider {
    public static final String ID = "anthropic";

    private static final List<EffortLevel> ANTHROPIC_EFFORT_RANGE =
            List.of(EffortLevel.LOW, EffortLevel.MEDIUM, EffortLevel.HIGH, EffortLevel.MAX);

    private final List<LlmModelDefinition> models;

    public AnthropicProvider() {
        this.models = List.of(
                buildAlwaysAdaptiveModel(Model.CLAUDE_FABLE_5_1.asString(), "Fable 5.1", EffortLevel.HIGH),
                buildAdaptiveThinkingModel(Model.CLAUDE_OPUS_5.asString(), "Opus 5", EffortLevel.HIGH),
                buildAdaptiveThinkingModel(Model.CLAUDE_SONNET_5.asString(), "Sonnet 5", EffortLevel.HIGH),
                buildFixedBudgetThinkingModel(Model.CLAUDE_HAIKU_4_5.asString(), "Haiku 4.5"),
                buildAdaptiveThinkingModel(Model.CLAUDE_OPUS_4_7.asString(), "Opus 4.7", EffortLevel.MEDIUM),
                buildAdaptiveThinkingModel(Model.CLAUDE_SONNET_4_6.asString(), "Sonnet 4.6", EffortLevel.MEDIUM));
    }

    /**
     * Effort only; adaptive thinking is always on (Fable 5.1). The {@code EXTENDED_THINKING} toggle
     * is omitted because the API does not accept turning thinking off.
     */
    private LlmModelDefinition buildAlwaysAdaptiveModel(
            String modelId, String displayName, EffortLevel defaultEffort) {
        return new LlmModelDefinition(
                this,
                modelId,
                displayName,
                List.of(ModelOptions.EFFORT),
                Map.of(ModelOptions.EFFORT, ANTHROPIC_EFFORT_RANGE),
                LlmModelOptionValues.of(ModelOptions.EFFORT, defaultEffort));
    }

    private LlmModelDefinition buildAdaptiveThinkingModel(
            String modelId, String displayName, EffortLevel defaultEffort) {
        return new LlmModelDefinition(
                this,
                modelId,
                displayName,
                List.of(ModelOptions.EFFORT, ModelOptions.EXTENDED_THINKING),
                Map.of(ModelOptions.EFFORT, ANTHROPIC_EFFORT_RANGE),
                LlmModelOptionValues.of(
                        ModelOptions.EFFORT, defaultEffort,
                        ModelOptions.EXTENDED_THINKING, Boolean.TRUE));
    }

    private LlmModelDefinition buildFixedBudgetThinkingModel(String modelId, String displayName) {
        return new LlmModelDefinition(
                this,
                modelId,
                displayName,
                List.of(ModelOptions.EXTENDED_THINKING),
                Map.of(),
                LlmModelOptionValues.of(ModelOptions.EXTENDED_THINKING, Boolean.FALSE));
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
     * Fable (effort, no thinking option): adaptive always on. Adaptive thinking models (effort +
     * thinking option): {@code ADAPTIVE} when the toggle is on. Haiku (thinking option, no effort):
     * {@code FIXED_BUDGET} when the toggle is on.
     */
    static AnthropicClientConfig.ThinkingMode thinkingModeFor(LlmModelDefinition model, boolean wantsThinking) {
        boolean supportsThinking = model.supportedOptions().contains(ModelOptions.EXTENDED_THINKING);
        boolean supportsEffort = model.supportedOptions().contains(ModelOptions.EFFORT);
        if (supportsEffort && !supportsThinking) {
            return AnthropicClientConfig.ThinkingMode.ADAPTIVE;
        }
        if (!wantsThinking) {
            return AnthropicClientConfig.ThinkingMode.OFF;
        }
        if (supportsThinking && !supportsEffort) {
            return AnthropicClientConfig.ThinkingMode.FIXED_BUDGET;
        }
        if (supportsThinking) {
            return AnthropicClientConfig.ThinkingMode.ADAPTIVE;
        }
        return AnthropicClientConfig.ThinkingMode.OFF;
    }

    /**
     * Anthropic supports {@code LOW..MAX}; {@link EffortLevel#MINIMAL} is clipped to {@code LOW}
     * and {@link EffortLevel#XHIGH} is clipped to {@code HIGH}.
     */
    static OutputConfig.Effort mapEffort(EffortLevel level) {
        return switch (level) {
            case MINIMAL, LOW -> OutputConfig.Effort.LOW;
            case MEDIUM -> OutputConfig.Effort.MEDIUM;
            case HIGH, XHIGH -> OutputConfig.Effort.HIGH;
            case MAX -> OutputConfig.Effort.MAX;
        };
    }

}
