package treepeater.ai.openai;

import treepeater.ai.OpenAiReasoningEffort;

/**
 * Azure OpenAI / Microsoft Foundry (or compatible) endpoint: API key, resource base URL, deployment, and
 * reasoning effort for reasoning models.
 */
public record OpenAiClientConfig(
        String endpoint, String apiKey, String deploymentName, OpenAiReasoningEffort reasoningEffort) {
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
        if (reasoningEffort == null) {
            reasoningEffort = OpenAiReasoningEffort.MEDIUM;
        }
    }
}
