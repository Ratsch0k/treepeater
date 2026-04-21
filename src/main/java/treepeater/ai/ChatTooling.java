package treepeater.ai;

import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * Optional tool declarations plus an executor. When inactive, chat clients behave like plain text chat.
 * Approval is handled purely via messages on the active {@link ChatStreamSession}; there is no callback
 * interface to implement.
 *
 * @param currentHistoryIndexSupplier invoked when formatting tool status lines; returns the tab's current send-history
 *     index, or {@link Integer#MIN_VALUE} if unknown.
 * @param requiresUserApproval if {@code null}, every tool run goes through approval. If non-null, when
 *     {@link Predicate#test(Object)} returns {@code false} for a tool name, the executor runs without waiting for the
 *     user, but a {@link ChatStreamMessage.ToolApprovalRequest} with {@code requiresApproval == false} is still
 *     emitted so the UI can show tool usage. Replace with a policy that reads user settings for future per-session or
 *     in-chat configuration.
 */
public record ChatTooling(
        List<ChatToolDefinition> tools,
        ChatToolExecutor executor,
        IntSupplier currentHistoryIndexSupplier,
        Predicate<String> requiresUserApproval) {
    public static ChatTooling none() {
        return new ChatTooling(List.of(), null, () -> Integer.MIN_VALUE, s -> true);
    }

    public boolean isActive() {
        return this.tools != null && !this.tools.isEmpty() && this.executor != null;
    }

    /**
     * Snapshot of the UI's current history entry index for one-line tool labels; {@link Integer#MIN_VALUE} if unknown.
     */
    public int currentHistoryIndexForToolStatus() {
        try {
            return this.currentHistoryIndexSupplier.getAsInt();
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    /**
     * Runs the executor either immediately (when the policy says no approval) or after a
     * {@link ChatStreamMessage.ToolApprovalRequest} / {@link ChatStreamMessage.ToolApprovalResponse} exchange.
     * Session-close and interruption both result in permission denied when approval was required.
     */
    public String executeWithApproval(ChatToolCall tc, ChatStreamSession session) throws Exception {
        if (this.executor == null) {
            throw new IllegalStateException("No executor");
        }
        String argsJson = tc.argumentsJson();
        String name = tc.name();
        String human = HttpTargetTools.humanReadableUsage(name, argsJson, currentHistoryIndexForToolStatus());
        if (this.requiresUserApproval != null && !this.requiresUserApproval.test(name)) {
            session.emit(new ChatStreamMessage.ToolApprovalRequest(tc.id(), name, argsJson, human, false));
            return this.executor.invoke(name, argsJson);
        }
        session.emit(new ChatStreamMessage.ToolApprovalRequest(tc.id(), name, argsJson, human, true));
        try {
            while (true) {
                ChatStreamMessage reply = session.awaitReply();
                if (reply == null) {
                    return HttpTargetTools.permissionDeniedResult();
                }
                if (reply instanceof ChatStreamMessage.ToolApprovalResponse r && matchesId(tc.id(), r.toolCallId())) {
                    return r.approved() ? this.executor.invoke(name, argsJson) : HttpTargetTools.permissionDeniedResult();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return HttpTargetTools.permissionDeniedResult();
        }
    }

    private static boolean matchesId(String requestId, String responseId) {
        if (requestId == null || requestId.isEmpty() || responseId == null || responseId.isEmpty()) {
            return true;
        }
        return requestId.equals(responseId);
    }
}
