package treepeater.ai;

/**
 * Runs a tool and returns a result string (typically JSON) for the model. Implementations receive a
 * {@link ChatToolInvokeContext} so nested tools can be executed with approval via {@link
 * ChatToolInvokeContext#invokeChildWithApproval()}.
 */
@FunctionalInterface
public interface ChatToolExecutor {
    String invoke(ChatToolInvokeContext context) throws Exception;
}
