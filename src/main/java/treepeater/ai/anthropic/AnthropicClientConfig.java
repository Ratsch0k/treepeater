package treepeater.ai.anthropic;

import com.anthropic.models.messages.OutputConfig;

/**
 * Settings for {@link AnthropicStreamingChatClient}: API key, model, the thinking strategy chosen
 * by {@link AnthropicProvider} per model, and the resolved SDK
 * {@link com.anthropic.models.messages.OutputConfig.Effort}.
 *
 * <p>The provider is responsible for selecting {@link ThinkingMode} appropriately for each model
 * id; the streaming client just switches on it.
 */
public record AnthropicClientConfig(
        String apiKey, String model, ThinkingMode thinkingMode, OutputConfig.Effort outputEffort) {

    /** How {@link AnthropicStreamingChatClient} should configure the {@code thinking} request field. */
    public enum ThinkingMode {
        /** Do not enable extended thinking. */
        OFF,
        /** Use {@link com.anthropic.models.messages.ThinkingConfigAdaptive} (Sonnet/Opus 4.6+). */
        ADAPTIVE,
        /** Use {@code enabled_thinking} with a fixed budget (older 4.x Sonnet/Opus). */
        FIXED_BUDGET
    }

    public AnthropicClientConfig {
        if (apiKey == null) {
            apiKey = "";
        }
        if (model == null) {
            model = "";
        }
        if (thinkingMode == null) {
            thinkingMode = ThinkingMode.OFF;
        }
        if (outputEffort == null) {
            outputEffort = OutputConfig.Effort.MEDIUM;
        }
    }
}
