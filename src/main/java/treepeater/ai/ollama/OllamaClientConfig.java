package treepeater.ai.ollama;

/**
 * Connection settings for {@link OllamaStreamingChatClient}.
 */
public record OllamaClientConfig(String baseUrl, String model) {
}
