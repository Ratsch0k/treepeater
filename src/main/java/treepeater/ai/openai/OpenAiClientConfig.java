package treepeater.ai.openai;

import com.openai.models.ReasoningEffort;

/**
 * Azure OpenAI / Microsoft Foundry (or compatible) endpoint: API key, resource base URL, deployment,
 * and the resolved SDK {@link com.openai.models.ReasoningEffort} to use for reasoning models.
 *
 * <p>The mapping from the generic {@link treepeater.ai.model.EffortLevel} to {@link ReasoningEffort}
 * lives in {@link OpenAiProvider}.
 */
public record OpenAiClientConfig(
        String endpoint, String apiKey, String deploymentName, ReasoningEffort reasoningEffort) {
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
            reasoningEffort = ReasoningEffort.MEDIUM;
        }
    }
}
