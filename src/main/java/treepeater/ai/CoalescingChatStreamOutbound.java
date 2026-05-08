package treepeater.ai;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Merges adjacent {@link ChatStreamMessage.AssistantDelta} and {@link ChatStreamMessage.ThinkingDelta} on the calling
 * thread (typically the chat worker) before they reach a delegate that may marshal to the EDT, reducing
 * {@link javax.swing.SwingUtilities#invokeLater} queue depth when a subsequent operation uses
 * {@link javax.swing.SwingUtilities#invokeAndWait} (e.g. live request apply).
 * <p>
 * Switching between assistant and thinking text flushes the other buffer first so chunk order matches the provider.
 * Non-delta messages flush both buffers (thinking, then assistant) before being passed through. Call {@link #shutdown}
 * when the stream ends so trailing debounced flushes are not lost and the scheduler is released.
 */
public final class CoalescingChatStreamOutbound implements Consumer<ChatStreamMessage> {
    private static final int DEBOUNCE_MS = 10;
    private static final int MAX_BUFFER_CHARS = 2048;

    private final Consumer<ChatStreamMessage> delegate;
    private final StringBuilder assistantBuffer = new StringBuilder();
    private final StringBuilder thinkingBuffer = new StringBuilder();
    private final Object lock = new Object();
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pendingAssistantFlush;
    private ScheduledFuture<?> pendingThinkingFlush;
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
                flushThinkingBufferUnsafe();
                this.assistantBuffer.append(ad.text());
                if (this.assistantBuffer.length() >= MAX_BUFFER_CHARS) {
                    cancelAssistantFlushUnsafe();
                    emitAssistantBufferUnsafe();
                } else {
                    rescheduleAssistantFlushUnsafe();
                }
            }
        } else if (m instanceof ChatStreamMessage.ThinkingDelta td) {
            if (td.text().isEmpty()) {
                return;
            }
            synchronized (this.lock) {
                if (this.shutdown) {
                    return;
                }
                flushAssistantBufferUnsafe();
                this.thinkingBuffer.append(td.text());
                if (this.thinkingBuffer.length() >= MAX_BUFFER_CHARS) {
                    cancelThinkingFlushUnsafe();
                    emitThinkingBufferUnsafe();
                } else {
                    rescheduleThinkingFlushUnsafe();
                }
            }
        } else {
            flush();
            this.delegate.accept(m);
        }
    }

    /**
     * Emits any buffered assistant and thinking text, then stops debounce timers and shuts down the scheduler.
     * Idempotent; safe to call more than once.
     */
    public void shutdown() {
        synchronized (this.lock) {
            this.shutdown = true;
            cancelAssistantFlushUnsafe();
            cancelThinkingFlushUnsafe();
            emitThinkingBufferUnsafe();
            emitAssistantBufferUnsafe();
        }
        this.scheduler.shutdown();
    }

    /** Flushes both buffers to the delegate (thinking first, then assistant). */
    public void flush() {
        if (this.shutdown) {
            return;
        }
        synchronized (this.lock) {
            cancelAssistantFlushUnsafe();
            cancelThinkingFlushUnsafe();
            emitThinkingBufferUnsafe();
            emitAssistantBufferUnsafe();
        }
    }

    private void cancelAssistantFlushUnsafe() {
        ScheduledFuture<?> f = this.pendingAssistantFlush;
        this.pendingAssistantFlush = null;
        if (f != null) {
            f.cancel(false);
        }
    }

    private void cancelThinkingFlushUnsafe() {
        ScheduledFuture<?> f = this.pendingThinkingFlush;
        this.pendingThinkingFlush = null;
        if (f != null) {
            f.cancel(false);
        }
    }

    /** Requires {@link #lock} held. */
    private void rescheduleAssistantFlushUnsafe() {
        cancelAssistantFlushUnsafe();
        final ScheduledFuture<?>[] box = new ScheduledFuture<?>[1];
        box[0] =
                this.scheduler.schedule(
                        () -> deferredAssistantFlush(box[0]), DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        this.pendingAssistantFlush = box[0];
    }

    /** Requires {@link #lock} held. */
    private void rescheduleThinkingFlushUnsafe() {
        cancelThinkingFlushUnsafe();
        final ScheduledFuture<?>[] box = new ScheduledFuture<?>[1];
        box[0] =
                this.scheduler.schedule(
                        () -> deferredThinkingFlush(box[0]), DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        this.pendingThinkingFlush = box[0];
    }

    private void deferredAssistantFlush(ScheduledFuture<?> expected) {
        if (this.shutdown) {
            return;
        }
        synchronized (this.lock) {
            if (this.pendingAssistantFlush != expected) {
                return;
            }
            this.pendingAssistantFlush = null;
            emitAssistantBufferUnsafe();
        }
    }

    private void deferredThinkingFlush(ScheduledFuture<?> expected) {
        if (this.shutdown) {
            return;
        }
        synchronized (this.lock) {
            if (this.pendingThinkingFlush != expected) {
                return;
            }
            this.pendingThinkingFlush = null;
            emitThinkingBufferUnsafe();
        }
    }

    /** Requires {@link #lock} held. */
    private void emitAssistantBufferUnsafe() {
        if (this.assistantBuffer.isEmpty()) {
            return;
        }
        String chunk = this.assistantBuffer.toString();
        this.assistantBuffer.setLength(0);
        this.delegate.accept(new ChatStreamMessage.AssistantDelta(chunk));
    }

    /** Requires {@link #lock} held. */
    private void emitThinkingBufferUnsafe() {
        if (this.thinkingBuffer.isEmpty()) {
            return;
        }
        String chunk = this.thinkingBuffer.toString();
        this.thinkingBuffer.setLength(0);
        this.delegate.accept(new ChatStreamMessage.ThinkingDelta(chunk));
    }

    /** Requires {@link #lock} held. */
    private void flushAssistantBufferUnsafe() {
        cancelAssistantFlushUnsafe();
        emitAssistantBufferUnsafe();
    }

    /** Requires {@link #lock} held. */
    private void flushThinkingBufferUnsafe() {
        cancelThinkingFlushUnsafe();
        emitThinkingBufferUnsafe();
    }
}
