package treepeater.ai.anthropic;

import treepeater.ai.AnthropicOutputEffort;

/**
 * Settings for {@link AnthropicStreamingChatClient}: API key, model, extended thinking, and
 * {@code output_config.effort}.
 */
public record AnthropicClientConfig(
        String apiKey, String model, boolean extendedThinking, AnthropicOutputEffort outputEffort) {
    public AnthropicClientConfig {
        if (apiKey == null) {
            apiKey = "";
        }
        if (model == null) {
            model = "";
        }
        if (outputEffort == null) {
            outputEffort = AnthropicOutputEffort.MEDIUM;
        }
    }
}
