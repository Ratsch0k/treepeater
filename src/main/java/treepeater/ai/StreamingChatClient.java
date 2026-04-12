package treepeater.ai;

import java.util.List;
import java.util.function.Consumer;

/**
 * Strategy for streaming chat completions. Implementations may target Ollama, cloud APIs, etc.
 */
public interface StreamingChatClient {
    /**
     * Runs a streaming chat request. May invoke {@code onTextDelta} on the calling thread
     * (typically a background thread). Each delta is an incremental text segment.
     * Returns the provider's conversation snapshot after completion.
     */
    List<ChatMessage> streamChat(List<ChatMessage> messages, Consumer<String> onTextDelta) throws Exception;
}
