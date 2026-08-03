package treepeater.ai;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;

import treepeater.api.TreepeaterToolRegistry;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.ToolHumanUsage;
import treepeater.api.tools.core.ToolResults;
import treepeater.api.tools.http.BatchHttpTargetToolsTool;

/**
 * Optional tool declarations plus an executor. When inactive, chat clients behave like plain text chat.
 * Approval is handled purely via messages on the active {@link ChatStreamSession}; there is no callback
 * interface to implement.
 */
public record ChatTooling(
        List<ChatToolDefinition> tools,
        ChatToolExecutor executor,
        IntSupplier currentHistoryIndexSupplier,
        ToolRunPolicy toolRunPolicy,
        TreepeaterTabAgentBridge agentBridge,
        TreepeaterToolRegistry toolRegistry) {

    public ChatTooling {
        Objects.requireNonNull(toolRunPolicy, "toolRunPolicy");
    }

    public ChatTooling(
            List<ChatToolDefinition> tools,
            ChatToolExecutor executor,
            IntSupplier currentHistoryIndexSupplier,
            ToolRunPolicy toolRunPolicy,
            TreepeaterTabAgentBridge agentBridge) {
        this(tools, executor, currentHistoryIndexSupplier, toolRunPolicy, agentBridge, null);
    }

    public ChatTooling(
            List<ChatToolDefinition> tools,
            ChatToolExecutor executor,
            IntSupplier currentHistoryIndexSupplier,
            ToolRunPolicy toolRunPolicy) {
        this(tools, executor, currentHistoryIndexSupplier, toolRunPolicy, null, null);
    }

    public static ChatTooling none() {
        return new ChatTooling(
                List.of(),
                null,
                () -> Integer.MIN_VALUE,
                new AgentModeToolPolicy(AgentMode.ASK, null),
                null,
                null);
    }

    public boolean isActive() {
        return this.tools != null && !this.tools.isEmpty() && this.executor != null;
    }

    public boolean requiresApproval(String toolName) {
        return this.toolRunPolicy.requiresApproval(toolName);
    }

    public int currentHistoryIndexForToolStatus() {
        try {
            return this.currentHistoryIndexSupplier.getAsInt();
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    public String executeWithApproval(ChatToolCall tc, ChatStreamSession session) throws Exception {
        if (this.executor == null) {
            throw new IllegalStateException("No executor");
        }
        String argsJson = tc.argumentsJson();
        String name = tc.name();
        int histForCard =
                this.agentBridge != null
                        ? treepeater.api.tools.http.support.HttpTargetSupport.viewerHistoryIndexForToolCard(
                                name, argsJson, this.agentBridge)
                        : currentHistoryIndexForToolStatus();
        int uiNodeForCard =
                treepeater.api.tools.http.support.HttpTargetSupport.uiSelectedRequestNodeIdForToolCard(
                        this.agentBridge);
        HumanToolUsage label = ToolHumanUsage.forTool(name, argsJson, histForCard, uiNodeForCard, this.toolRegistry);
        ToolRunPolicy policy = this.toolRunPolicy;
        AtomicInteger batchChildSlot = new AtomicInteger(0);
        NestedToolInvoker childInvoker =
                (childName, childArgs) -> {
                    String childId =
                            BatchHttpTargetToolsTool.syntheticChildToolCallId(
                                    tc.id(), batchChildSlot.getAndIncrement());
                    return executeWithApproval(new ChatToolCall(childId, childName, childArgs), session);
                };
        ChatToolInvokeContext invokeCtx = new ChatToolInvokeContext(name, argsJson, childInvoker);
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
                    return ToolResults.permissionDenied();
                }
                if (reply instanceof ChatStreamMessage.ToolApprovalResponse r && matchesId(tc.id(), r.toolCallId())) {
                    return r.approved() ? this.executor.invoke(invokeCtx) : ToolResults.permissionDenied();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResults.permissionDenied();
        }
    }

    private static boolean matchesId(String requestId, String responseId) {
        if (requestId == null || requestId.isEmpty() || responseId == null || responseId.isEmpty()) {
            return true;
        }
        return requestId.equals(responseId);
    }
}
