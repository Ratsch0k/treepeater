package treepeater.ai;

/**
 * Bidirectional stream item exchanged with a {@link StreamingChatClient} via a {@link ChatStreamSession}:
 * clients emit outbound variants ({@link AssistantDelta}, {@link ThinkingDelta}, {@link ToolApprovalRequest}) and may
 * wait for inbound
 * variants ({@link ToolApprovalResponse}) posted by the UI in reply.
 */
public sealed interface ChatStreamMessage
        permits ChatStreamMessage.AssistantDelta,
                ChatStreamMessage.ThinkingDelta,
                ChatStreamMessage.ToolApprovalRequest,
                ChatStreamMessage.ToolApprovalResponse {

    /** Incremental assistant reply text. */
    record AssistantDelta(String text) implements ChatStreamMessage {
        public AssistantDelta {
            text = text != null ? text : "";
        }
    }

    /** Incremental model reasoning / thinking text (when the provider exposes it). */
    record ThinkingDelta(String text) implements ChatStreamMessage {
        public ThinkingDelta {
            text = text != null ? text : "";
        }
    }

    /**
     * The client is running a tool: always show usage in the UI. When {@code requiresApproval} is {@code true}, the UI
     * must reply with a matching {@link ToolApprovalResponse} (same {@code toolCallId}) before the run continues.
     * When {@code false}, the card is informational only (no buttons; no reply).
     */
    record ToolApprovalRequest(
            String toolCallId,
            String toolName,
            String argumentsJson,
            String humanTitle,
            String humanDetail,
            boolean requiresApproval)
            implements ChatStreamMessage {
        public ToolApprovalRequest {
            toolCallId = toolCallId != null ? toolCallId : "";
            toolName = toolName != null ? toolName : "";
            argumentsJson = argumentsJson != null ? argumentsJson : "";
            humanTitle = humanTitle != null ? humanTitle : "";
            humanDetail = humanDetail != null ? humanDetail : "";
        }
    }

    /** Reply to a {@link ToolApprovalRequest}; {@code approved == false} yields a permission-denied tool result. */
    record ToolApprovalResponse(String toolCallId, boolean approved) implements ChatStreamMessage {
        public ToolApprovalResponse {
            toolCallId = toolCallId != null ? toolCallId : "";
        }
    }
}
