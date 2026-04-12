package treepeater.ai;

import java.util.List;
import java.util.function.Consumer;

/**
 * Strategy for streaming chat completions. Implementations may target Ollama, cloud APIs, etc.
 */
public interface StreamingChatClient {
    /**
     * Runs a streaming chat request with no tools; all output is delivered as {@link ChatStreamMessage.AssistantDelta}.
     */
    default List<ChatMessage> streamChat(List<ChatMessage> messages, Consumer<ChatStreamMessage> onMessage)
            throws Exception {
        return streamChat(messages, ChatTooling.none(), onMessage);
    }

    /**
     * Runs a streaming chat request. Implementations may invoke {@code onMessage} on the calling thread
     * (typically a background thread); the listener should marshal UI work to the EDT as needed.
     * <p>
     * When {@link ChatTooling} is inactive, implementations behave like plain text chat (assistant deltas only).
     */
    List<ChatMessage> streamChat(
            List<ChatMessage> messages, ChatTooling tooling, Consumer<ChatStreamMessage> onMessage) throws Exception;
}
