package treepeater.ai;

/**
 * Streamed interaction while a chat request is in progress: assistant tokens, tool invocations, etc.
 * Implementations emit these through {@link StreamingChatClient#streamChat(java.util.List, ChatTooling,
 * java.util.function.Consumer)} so the UI can handle all output in one place.
 */
public sealed interface ChatStreamMessage permits ChatStreamMessage.AssistantDelta, ChatStreamMessage.ToolUsage {

    /** Incremental assistant reply text (same meaning as the former per-token string callback). */
    record AssistantDelta(String text) implements ChatStreamMessage {
        public AssistantDelta {
            text = text == null ? "" : text;
        }
    }

    /**
     * Emitted immediately before the tool {@link ChatToolExecutor} runs for this name/arguments pair.
     * {@code humanDescription} is a short one-line status for the UI (e.g. {@code Getting target}).
     */
    record ToolUsage(String toolName, String argumentsJson, String humanDescription) implements ChatStreamMessage {
        public ToolUsage {
            toolName = toolName == null ? "" : toolName;
            argumentsJson = argumentsJson == null ? "" : argumentsJson;
            humanDescription = humanDescription == null ? "" : humanDescription;
        }
    }
}
