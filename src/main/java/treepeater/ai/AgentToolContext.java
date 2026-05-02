package treepeater.ai;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

/**
 * Snapshot for AI tools: HTTP target summary, repeater history metadata, and accessors that resolve the
 * request/response for a given history index (live editors for the current index, frozen {@link
 * treepeater.requestResponse.HistoryEntry} snapshots for others).
 *
 * @param requestNodeId {@link treepeater.tree.RequestTreeNode#getId()} for the repeater tab this context belongs to
 * @param applyLiveRequest commits the given request to the live repeater editor (current history entry only). Invoked
 *     on the EDT by body-edit tools; null if mutation is unavailable.
 * @param sendCurrentHttpRequest sends the current editor request (after applying target), updates response and history
 *     like the Send button, blocks until complete, returns HTTP status code; null if unavailable.
 */
public record AgentToolContext(
        HttpTargetSnapshot target,
        int requestNodeId,
        int currentHistoryIndex,
        List<HistoryEntryInfo> historyEntries,
        IntFunction<HttpRequest> requestForHistoryIndex,
        IntFunction<HttpResponse> responseForHistoryIndex,
        Consumer<HttpRequest> applyLiveRequest,
        Callable<Integer> sendCurrentHttpRequest) {

    public int historySize() {
        return this.historyEntries.size();
    }

    /** One row per {@link treepeater.requestResponse.HistoryEntry} for tool summaries and navigation. */
    public record HistoryEntryInfo(int index, String time, String targetLabel) {}
}
