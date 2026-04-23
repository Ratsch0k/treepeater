package treepeater.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs a round of model-requested {@link ChatToolCall tool calls}. When every call in the batch is
 * auto-approved (no {@link ChatStreamMessage.ToolApprovalRequest} needed) the calls are dispatched
 * concurrently on a short-lived pool and results are returned in the original order. If any call in
 * the batch requires approval, execution falls back to sequential invocation to preserve the
 * approval UX (each request/response pair is observed in order).
 */
public final class ParallelToolExecution {

    private static final int MAX_CONCURRENCY = 4;

    private ParallelToolExecution() {}

    /**
     * @return tool results, one per input call, in the same order as {@code calls}. On session
     *     close / thread interrupt the returned list is padded with
     *     {@link HttpTargetTools#permissionDeniedResult()} for any not-yet-started calls.
     */
    public static List<String> executeRound(
            List<ChatToolCall> calls, ChatTooling tooling, ChatStreamSession session) throws Exception {
        int n = calls.size();
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(null);
        }

        if (n == 0) {
            return out;
        }

        boolean anyApproval = false;
        for (ChatToolCall tc : calls) {
            if (tooling.requiresApproval(tc.name())) {
                anyApproval = true;
                break;
            }
        }

        if (n == 1 || anyApproval) {
            for (int i = 0; i < n; i++) {
                if (session.isClosed() || Thread.currentThread().isInterrupted()) {
                    out.set(i, HttpTargetTools.permissionDeniedResult());
                    continue;
                }
                out.set(i, tooling.executeWithApproval(calls.get(i), session));
            }
            return out;
        }

        int workers = Math.min(MAX_CONCURRENCY, n);
        ExecutorService pool =
                Executors.newFixedThreadPool(
                        workers,
                        r -> {
                            Thread t = new Thread(r, "treepeater-tool-parallel");
                            t.setDaemon(true);
                            return t;
                        });
        try {
            List<Future<String>> futures = new ArrayList<>(n);
            for (ChatToolCall tc : calls) {
                Callable<String> task = () -> tooling.executeWithApproval(tc, session);
                futures.add(pool.submit(task));
            }
            for (int i = 0; i < n; i++) {
                try {
                    out.set(i, futures.get(i).get());
                } catch (ExecutionException ee) {
                    Throwable cause = ee.getCause();
                    if (cause instanceof Exception ex) {
                        throw ex;
                    }
                    throw ee;
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    out.set(i, HttpTargetTools.permissionDeniedResult());
                    for (int j = i; j < n; j++) {
                        futures.get(j).cancel(true);
                    }
                    for (int j = i + 1; j < n; j++) {
                        out.set(j, HttpTargetTools.permissionDeniedResult());
                    }
                    return out;
                }
            }
            return out;
        } finally {
            pool.shutdown();
        }
    }
}
