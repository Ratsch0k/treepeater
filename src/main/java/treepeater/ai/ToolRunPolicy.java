package treepeater.ai;

/**
 * Whether the user must approve a tool before it runs. Does not block tools; disapproval still yields a
 * permission-denied result after prompting when approval is required.
 */
@FunctionalInterface
public interface ToolRunPolicy {
    /**
     * {@code false} — run immediately after showing the informational tool card; {@code true} — wait for
     * {@link ChatStreamMessage.ToolApprovalResponse}.
     */
    boolean requiresApproval(String toolName);
}
