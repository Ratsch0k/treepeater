package treepeater.ai;

/**
 * A tool invocation produced by the assistant (model output), before results are applied.
 */
public record ChatToolCall(String id, String name, String argumentsJson) {
    public ChatToolCall {
        if (id == null) {
            id = "";
        }
        if (name == null) {
            name = "";
        }
        if (argumentsJson == null) {
            argumentsJson = "";
        }
    }
}
