package treepeater.ai.openai;

/**
 * Azure OpenAI / Microsoft Foundry (or compatible) endpoint: API key, resource base URL, and deployment name.
 */
public record OpenAiClientConfig(String endpoint, String apiKey, String deploymentName) {
    public OpenAiClientConfig {
        if (endpoint == null) {
            endpoint = "";
        }
        if (apiKey == null) {
            apiKey = "";
        }
        if (deploymentName == null) {
            deploymentName = "";
        }
    }
}
