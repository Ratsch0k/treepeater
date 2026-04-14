package treepeater.ai;

import java.util.List;
import java.util.function.IntSupplier;

/**
 * Optional tool declarations plus an executor. When inactive, chat clients behave like plain text chat.
 *
 * @param currentHistoryIndexSupplier invoked when formatting tool status lines; returns the tab's current send-history
 *     index, or {@link Integer#MIN_VALUE} if unknown (history index is always shown in the label when present in args).
 */
public record ChatTooling(
        List<ChatToolDefinition> tools, ChatToolExecutor executor, IntSupplier currentHistoryIndexSupplier) {
    public static ChatTooling none() {
        return new ChatTooling(List.of(), null, () -> Integer.MIN_VALUE);
    }

    public boolean isActive() {
        return this.tools != null && !this.tools.isEmpty() && this.executor != null;
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
}
