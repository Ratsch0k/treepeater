package treepeater.ai;

/**
 * A single chat turn with a role and text content.
 */
public record ChatMessage(ChatRole role, String content) {
    public ChatMessage {
        if (content == null) {
            content = "";
        }
    }
}
