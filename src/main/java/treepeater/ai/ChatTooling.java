package treepeater.ai;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

/**
 * Optional tool declarations plus an executor. When inactive, chat clients behave like plain text chat.
 * Approval is handled purely via messages on the active {@link ChatStreamSession}; there is no callback
 * interface to implement.
 *
 * @param currentHistoryIndexSupplier invoked when formatting tool status lines; returns the tab's current send-history
 *     index, or {@link Integer#MIN_VALUE} if unknown.
 * @param toolRunPolicy which tools need user approval before execution (never blocks outright).
 * @param agentBridge when non-null, tool cards use {@link HttpTargetTools#viewerHistoryIndexForToolCard} for the tab in
 *     {@code request_node_id}
 */
public record ChatTooling(
        List<ChatToolDefinition> tools,
        ChatToolExecutor executor,
        IntSupplier currentHistoryIndexSupplier,
        ToolRunPolicy toolRunPolicy,
        RepeaterTabAgentBridge agentBridge) {
    public ChatTooling {
        Objects.requireNonNull(toolRunPolicy, "toolRunPolicy");
    }

    public ChatTooling(
            List<ChatToolDefinition> tools,
            ChatToolExecutor executor,
            IntSupplier currentHistoryIndexSupplier,
            ToolRunPolicy toolRunPolicy) {
        this(tools, executor, currentHistoryIndexSupplier, toolRunPolicy, null);
    }

    public static ChatTooling none() {
        return new ChatTooling(List.of(), null, () -> Integer.MIN_VALUE, new AgentModeToolPolicy(AgentMode.ASK), null);
    }

    public boolean isActive() {
        return this.tools != null && !this.tools.isEmpty() && this.executor != null;
    }

    /** Whether the given tool name needs user approval under the current policy. */
    public boolean requiresApproval(String toolName) {
        return this.toolRunPolicy.requiresApproval(toolName);
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
        int histForCard =
                this.agentBridge != null
                        ? HttpTargetTools.viewerHistoryIndexForToolCard(name, argsJson, this.agentBridge)
                        : currentHistoryIndexForToolStatus();
        int uiNodeForCard = HttpTargetTools.uiSelectedRequestNodeIdForToolCard(this.agentBridge);
        HttpTargetTools.HumanToolUsage label =
                HttpTargetTools.humanToolUsage(name, argsJson, histForCard, uiNodeForCard);
        ToolRunPolicy policy = this.toolRunPolicy;
        AtomicInteger batchChildSlot = new AtomicInteger(0);
        NestedToolInvoker childInvoker =
                (childName, childArgs) -> {
                    String childId =
                            HttpTargetTools.syntheticBatchChildToolCallId(tc.id(), batchChildSlot.getAndIncrement());
                    return executeWithApproval(new ChatToolCall(childId, childName, childArgs), session);
                };
        ChatToolInvokeContext invokeCtx =
                new ChatToolInvokeContext(name, argsJson, childInvoker);
        if (!policy.requiresApproval(name)) {
            session.emit(
                    new ChatStreamMessage.ToolApprovalRequest(
                            tc.id(), name, argsJson, label.title(), label.detail(), false));
            return this.executor.invoke(invokeCtx);
        }
        session.emit(
                new ChatStreamMessage.ToolApprovalRequest(
                        tc.id(), name, argsJson, label.title(), label.detail(), true));
        try {
            while (true) {
                ChatStreamMessage reply = session.awaitReply();
                if (reply == null) {
                    return HttpTargetTools.permissionDeniedResult();
                }
                if (reply instanceof ChatStreamMessage.ToolApprovalResponse r && matchesId(tc.id(), r.toolCallId())) {
                    return r.approved()
                            ? this.executor.invoke(invokeCtx)
                            : HttpTargetTools.permissionDeniedResult();
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
