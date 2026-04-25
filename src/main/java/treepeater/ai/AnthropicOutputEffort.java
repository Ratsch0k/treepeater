package treepeater.ai;

import com.anthropic.models.messages.OutputConfig;

/**
 * Maps to {@link OutputConfig.Effort} (Messages API {@code output_config.effort}).
 */
public enum AnthropicOutputEffort {
    LOW,
    MEDIUM,
    HIGH,
    MAX;

    public OutputConfig.Effort toSdk() {
        return switch (this) {
            case LOW -> OutputConfig.Effort.LOW;
            case MEDIUM -> OutputConfig.Effort.MEDIUM;
            case HIGH -> OutputConfig.Effort.HIGH;
            case MAX -> OutputConfig.Effort.MAX;
        };
    }
}
