package treepeater.ai.anthropic;

import java.util.Optional;

import com.anthropic.models.messages.OutputConfig;

/**
 * Settings for {@link AnthropicStreamingChatClient}: API key, model, the thinking strategy chosen
 * by {@link AnthropicProvider} per model, and optionally the SDK
 * {@link com.anthropic.models.messages.OutputConfig.Effort} when the model supports it.
 *
 * <p>The provider is responsible for selecting {@link ThinkingMode} appropriately for each model
 * id; the streaming client just switches on it. Models that do not support output effort (for
 * example Haiku 4.5) use an empty {@link #outputEffort()} so the client omits {@code output_config}
 * from the request.
 */
public record AnthropicClientConfig(
        String apiKey,
        String model,
        ThinkingMode thinkingMode,
        Optional<OutputConfig.Effort> outputEffort) {

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
            outputEffort = Optional.empty();
        }
    }
}
