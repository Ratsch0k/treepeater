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
        ChatStreamSession session = new ChatStreamSession(onMessage);
        try {
            return streamChat(messages, ChatTooling.none(), session);
        } finally {
            session.close();
        }
    }

    /**
     * Runs a streaming chat request. The implementation may call {@link ChatStreamSession#emit} from any thread
     * (listeners should marshal UI work to the EDT as needed) and may call {@link ChatStreamSession#awaitReply} to
     * wait for user-originated messages (e.g. {@link ChatStreamMessage.ToolApprovalResponse}).
     * <p>
     * When {@link ChatTooling} is inactive, implementations behave like plain text chat (assistant deltas only).
     * With active tooling, a {@link ChatStreamMessage.ToolApprovalRequest} is emitted before each tool execution.
     */
    List<ChatMessage> streamChat(List<ChatMessage> messages, ChatTooling tooling, ChatStreamSession session)
            throws Exception;
}
