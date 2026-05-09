package treepeater.ai;

/**
 * Runs a child tool with the same approval and executor pipeline as a top-level {@link ChatToolCall}. Used by
 * {@link HttpTargetTools#BATCH_HTTP_TARGET_TOOLS}.
 */
@FunctionalInterface
public interface NestedToolInvoker {
    String invoke(String toolName, String argumentsJson) throws Exception;
}
