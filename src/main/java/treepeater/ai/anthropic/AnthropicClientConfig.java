package treepeater.ai.anthropic;

/**
 * Connection settings for {@link AnthropicStreamingChatClient} (API key and model id from
 * {@link treepeater.settings.TreepeaterSettings}).
 */
public record AnthropicClientConfig(String apiKey, String model) {}
