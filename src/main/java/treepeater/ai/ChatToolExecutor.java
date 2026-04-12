package treepeater.ai;

/**
 * Runs a tool by name with JSON arguments and returns a result string (typically JSON) for the model.
 */
@FunctionalInterface
public interface ChatToolExecutor {
    String invoke(String toolName, String argumentsJson) throws Exception;
}
