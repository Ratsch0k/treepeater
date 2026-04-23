package treepeater.ai;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Merges adjacent {@link ChatStreamMessage.AssistantDelta} on the calling thread (typically the chat worker) before
 * they reach a delegate that may marshal to the EDT, reducing {@link javax.swing.SwingUtilities#invokeLater} queue
 * depth when a subsequent operation uses {@link javax.swing.SwingUtilities#invokeAndWait} (e.g. live request apply).
 * <p>
 * Non-delta messages flush any buffered text first, then are passed through immediately. Call {@link #shutdown} when
 * the stream ends so a trailing debounced flush is not lost and the scheduler is released.
 */
public final class CoalescingChatStreamOutbound implements Consumer<ChatStreamMessage> {
    private static final int DEBOUNCE_MS = 25;
    private static final int MAX_BUFFER_CHARS = 2048;

    private final Consumer<ChatStreamMessage> delegate;
    private final StringBuilder buffer = new StringBuilder();
    private final Object lock = new Object();
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pendingFlush;
    private volatile boolean shutdown;

    public CoalescingChatStreamOutbound(Consumer<ChatStreamMessage> delegate) {
        this.delegate = delegate;
        this.scheduler =
                Executors.newSingleThreadScheduledExecutor(
                        r -> {
                            Thread t = new Thread(r, "treepeater-coalesce-chat-out");
                            t.setDaemon(true);
                            return t;
                        });
    }

    @Override
    public void accept(ChatStreamMessage m) {
        if (this.shutdown) {
            return;
        }
        if (m instanceof ChatStreamMessage.AssistantDelta ad) {
            if (ad.text().isEmpty()) {
                return;
            }
            synchronized (this.lock) {
                if (this.shutdown) {
                    return;
                }
                this.buffer.append(ad.text());
                if (this.buffer.length() >= MAX_BUFFER_CHARS) {
                    cancelFlushUnsafe();
                    emitBufferUnsafe();
                } else {
                    rescheduleFlushUnsafe();
                }
            }
        } else {
            flush();
            this.delegate.accept(m);
        }
    }

    /**
     * Emits any buffered assistant text, then stops the debounce timer and shuts down the scheduler. Idempotent; safe
     * to call more than once.
     */
    public void shutdown() {
        synchronized (this.lock) {
            this.shutdown = true;
            cancelFlushUnsafe();
            emitBufferUnsafe();
        }
        this.scheduler.shutdown();
    }

    /** Flushes buffered assistant text to the delegate (used before session close in addition to {@link #shutdown}). */
    public void flush() {
        if (this.shutdown) {
            return;
        }
        synchronized (this.lock) {
            cancelFlushUnsafe();
            emitBufferUnsafe();
        }
    }

    private void cancelFlushUnsafe() {
        ScheduledFuture<?> f = this.pendingFlush;
        this.pendingFlush = null;
        if (f != null) {
            f.cancel(false);
        }
    }

    /** Requires {@link #lock} held. */
    private void rescheduleFlushUnsafe() {
        cancelFlushUnsafe();
        final ScheduledFuture<?>[] box = new ScheduledFuture<?>[1];
        box[0] =
                this.scheduler.schedule(
                        () -> deferredFlush(box[0]), DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        this.pendingFlush = box[0];
    }

    private void deferredFlush(ScheduledFuture<?> expected) {
        if (this.shutdown) {
            return;
        }
        synchronized (this.lock) {
            if (this.pendingFlush != expected) {
                return;
            }
            this.pendingFlush = null;
            emitBufferUnsafe();
        }
    }

    /** Requires {@link #lock} held. */
    private void emitBufferUnsafe() {
        if (this.buffer.isEmpty()) {
            return;
        }
        String chunk = this.buffer.toString();
        this.buffer.setLength(0);
        this.delegate.accept(new ChatStreamMessage.AssistantDelta(chunk));
    }
}
