package treepeater.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class ParallelToolExecutionTest {

    /** Every non-approval call in a batch of three is observed to run before any of them completes. */
    @Test
    void executeRound_runsAutoApprovedToolsInParallel() throws Exception {
        List<ChatToolCall> calls =
                List.of(
                        new ChatToolCall("a", "noop", "{\"k\":\"a\"}"),
                        new ChatToolCall("b", "noop", "{\"k\":\"b\"}"),
                        new ChatToolCall("c", "noop", "{\"k\":\"c\"}"));

        CountDownLatch allStarted = new CountDownLatch(calls.size());
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();

        ChatToolExecutor exec =
                (name, argsJson) -> {
                    int now = inFlight.incrementAndGet();
                    maxInFlight.updateAndGet(m -> Math.max(m, now));
                    allStarted.countDown();
                    try {
                        if (!allStarted.await(2, TimeUnit.SECONDS)) {
                            throw new AssertionError("tools not dispatched concurrently");
                        }
                        return "{\"ok\":" + argsJson + "}";
                    } finally {
                        inFlight.decrementAndGet();
                    }
                };

        ChatTooling tooling =
                new ChatTooling(
                        List.of(new ChatToolDefinition("noop", "", "{}")),
                        exec,
                        () -> Integer.MIN_VALUE,
                        name -> false);

        ChatStreamSession session = new ChatStreamSession(m -> {});

        List<String> results = ParallelToolExecution.executeRound(calls, tooling, session);

        assertEquals(3, results.size(), "results preserve call count");
        assertTrue(results.get(0).contains("a"), "result 0 matches call 0 args");
        assertTrue(results.get(1).contains("b"), "result 1 matches call 1 args");
        assertTrue(results.get(2).contains("c"), "result 2 matches call 2 args");
        assertTrue(maxInFlight.get() >= 2, "at least two tools should overlap; saw max " + maxInFlight.get());
    }

    /** Approval-required calls keep sequential semantics so each request/response pair is observed in order. */
    @Test
    void executeRound_sequentialWhenAnyCallRequiresApproval() throws Exception {
        List<ChatToolCall> calls =
                List.of(
                        new ChatToolCall("a", "safe", "{}"),
                        new ChatToolCall("b", "danger", "{}"));

        List<String> invocationOrder = new ArrayList<>();
        AtomicReference<String> activeCall = new AtomicReference<>();

        ChatToolExecutor exec =
                (name, argsJson) -> {
                    synchronized (invocationOrder) {
                        if (activeCall.get() != null) {
                            throw new AssertionError("parallel execution occurred despite approval flag");
                        }
                        activeCall.set(name);
                        invocationOrder.add(name);
                    }
                    Thread.sleep(5);
                    synchronized (invocationOrder) {
                        activeCall.set(null);
                    }
                    return "{}";
                };

        ChatTooling tooling =
                new ChatTooling(
                        List.of(
                                new ChatToolDefinition("safe", "", "{}"),
                                new ChatToolDefinition("danger", "", "{}")),
                        exec,
                        () -> Integer.MIN_VALUE,
                        "danger"::equals);

        ChatStreamSession session = new ChatStreamSession(m -> {});
        Thread.ofVirtual()
                .start(
                        () -> {
                            try {
                                Thread.sleep(50);
                                session.postReply(new ChatStreamMessage.ToolApprovalResponse("b", true));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        });

        ParallelToolExecution.executeRound(calls, tooling, session);

        assertEquals(List.of("safe", "danger"), invocationOrder, "approval path runs sequentially in order");
    }
}
