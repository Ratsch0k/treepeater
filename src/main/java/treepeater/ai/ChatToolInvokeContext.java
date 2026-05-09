package treepeater.ai;

/**
 * Invocation payload for {@link ChatToolExecutor}. Carries optional {@link NestedToolInvoker} so compound tools (e.g.
 * batch) can execute nested built-ins with per-step approval.
 */
public record ChatToolInvokeContext(String toolName, String argumentsJson, NestedToolInvoker invokeChildWithApproval) {
    public ChatToolInvokeContext(String toolName, String argumentsJson) {
        this(toolName, argumentsJson, null);
    }

    public ChatToolInvokeContext {
        if (toolName == null) {
            toolName = "";
        }
        if (argumentsJson == null) {
            argumentsJson = "";
        }
    }
}
