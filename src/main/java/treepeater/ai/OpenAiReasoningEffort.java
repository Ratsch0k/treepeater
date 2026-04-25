package treepeater.ai;

import com.openai.models.ReasoningEffort;

/**
 * User-facing OpenAI / Azure chat reasoning levels; mapped to {@link ReasoningEffort} in the client.
 */
public enum OpenAiReasoningEffort {
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH;

    public ReasoningEffort toSdk() {
        return switch (this) {
            case MINIMAL -> ReasoningEffort.MINIMAL;
            case LOW -> ReasoningEffort.LOW;
            case MEDIUM -> ReasoningEffort.MEDIUM;
            case HIGH -> ReasoningEffort.HIGH;
        };
    }
}
