package treepeater.ai;

/**
 * Bidirectional stream item exchanged with a {@link StreamingChatClient} via a {@link ChatStreamSession}:
 * clients emit outbound variants ({@link AssistantDelta}, {@link ToolApprovalRequest}) and may wait for inbound
 * variants ({@link ToolApprovalResponse}) posted by the UI in reply.
 */
public sealed interface ChatStreamMessage
        permits ChatStreamMessage.AssistantDelta,
                ChatStreamMessage.ToolApprovalRequest,
                ChatStreamMessage.ToolApprovalResponse {

    /** Incremental assistant reply text. */
    record AssistantDelta(String text) implements ChatStreamMessage {
        public AssistantDelta {
            text = text != null ? text : "";
        }
    }

    /**
     * The client wants to execute a tool and needs the user's approval. The UI must reply with a matching
     * {@link ToolApprovalResponse} (same {@code toolCallId}).
     */
    record ToolApprovalRequest(String toolCallId, String toolName, String argumentsJson, String humanDescription)
            implements ChatStreamMessage {
        public ToolApprovalRequest {
            toolCallId = toolCallId != null ? toolCallId : "";
            toolName = toolName != null ? toolName : "";
            argumentsJson = argumentsJson != null ? argumentsJson : "";
            humanDescription = humanDescription != null ? humanDescription : "";
        }
    }

    /** Reply to a {@link ToolApprovalRequest}; {@code approved == false} yields a permission-denied tool result. */
    record ToolApprovalResponse(String toolCallId, boolean approved) implements ChatStreamMessage {
        public ToolApprovalResponse {
            toolCallId = toolCallId != null ? toolCallId : "";
        }
    }
}
