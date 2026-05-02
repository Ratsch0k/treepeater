package treepeater.ai;

import java.util.List;
import java.util.function.Consumer;

/**
 * Strategy for streaming chat completions. Implementations may target Ollama, cloud APIs, etc.
 */
public interface StreamingChatClient {
    /**
     * Safety cap on assistant↔tool round-trips when tools are active. The user can stop earlier via
     * {@link ChatStreamSession#close()}; clients should also exit when the session is closed or the worker thread is
     * interrupted.
     */
    int MAX_AGENT_TOOL_ROUNDS = 256;
    /**
     * Runs a streaming chat request with no tools; output is delivered as {@link ChatStreamMessage.AssistantDelta}
     * and optionally {@link ChatStreamMessage.ThinkingDelta} when the provider streams reasoning text.
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
     * When {@link ChatTooling} is inactive, implementations behave like plain text chat (assistant and thinking deltas
     * only).
     * With active tooling, a {@link ChatStreamMessage.ToolApprovalRequest} is emitted for each tool (approval may be
     * required or informational only).
     */
    List<ChatMessage> streamChat(List<ChatMessage> messages, ChatTooling tooling, ChatStreamSession session)
            throws Exception;
}
