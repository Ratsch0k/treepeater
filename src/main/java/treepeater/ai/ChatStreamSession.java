package treepeater.ai;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Bidirectional channel between a {@link StreamingChatClient} (worker thread) and its host UI. The client emits
 * outbound messages via {@link #emit} and, when it needs to wait for a user-originated reply (e.g. tool approval),
 * calls {@link #awaitReply}. The UI posts replies with {@link #postReply}, and calls {@link #close} to unblock any
 * waiting client when the request is aborted.
 */
public final class ChatStreamSession {
    private static final ChatStreamMessage CLOSE_SENTINEL =
            new ChatStreamMessage.ToolApprovalResponse("__session_closed__", false);

    private final Consumer<ChatStreamMessage> outbound;
    private final BlockingQueue<ChatStreamMessage> inbound = new LinkedBlockingQueue<>();
    private volatile boolean closed = false;

    public ChatStreamSession(Consumer<ChatStreamMessage> outbound) {
        this.outbound = outbound != null ? outbound : m -> {};
    }

    /** Sends {@code m} to the UI. Safe to call from any thread; the consumer decides thread marshalling. */
    public void emit(ChatStreamMessage m) {
        if (m == null) {
            return;
        }
        ChatStreamLogging.logEmitTowardUi(m);
        this.outbound.accept(m);
    }

    /** Queues a user-originated reply for the client to pick up via {@link #awaitReply}. */
    public void postReply(ChatStreamMessage m) {
        if (m == null) {
            return;
        }
        ChatStreamLogging.logSessionPostReply(m);
        this.inbound.offer(m);
    }

    /**
     * Blocks the calling thread until a reply arrives, the session is closed, or the thread is interrupted.
     *
     * @return the next reply, or {@code null} if the session was closed while waiting.
     * @throws InterruptedException if the calling thread is interrupted.
     */
    public ChatStreamMessage awaitReply() throws InterruptedException {
        while (true) {
            if (this.closed) {
                return null;
            }
            ChatStreamMessage m = this.inbound.poll(200, TimeUnit.MILLISECONDS);
            if (m == null) {
                continue;
            }
            if (m == CLOSE_SENTINEL) {
                return null;
            }
            return m;
        }
    }

    /** Marks the session closed and unblocks any pending {@link #awaitReply}. */
    public void close() {
        this.closed = true;
        this.inbound.offer(CLOSE_SENTINEL);
    }

    public boolean isClosed() {
        return this.closed;
    }
}
