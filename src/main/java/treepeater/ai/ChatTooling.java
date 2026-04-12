package treepeater.ai;

import java.util.List;

/**
 * Optional tool declarations plus an executor. When inactive, chat clients behave like plain text chat.
 */
public record ChatTooling(List<ChatToolDefinition> tools, ChatToolExecutor executor) {
    public static ChatTooling none() {
        return new ChatTooling(List.of(), null);
    }

    public boolean isActive() {
        return this.tools != null && !this.tools.isEmpty() && this.executor != null;
    }
}
