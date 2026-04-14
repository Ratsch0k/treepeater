package treepeater.ai;

import java.util.List;
import java.util.function.IntFunction;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

/**
 * Snapshot for AI tools: HTTP target summary, repeater history metadata, and accessors that resolve the
 * request/response for a given history index (live editors for the current index, frozen {@link
 * treepeater.requestResponse.HistoryEntry} snapshots for others).
 */
public record AgentToolContext(
        HttpTargetSnapshot target,
        int currentHistoryIndex,
        List<HistoryEntryInfo> historyEntries,
        IntFunction<HttpRequest> requestForHistoryIndex,
        IntFunction<HttpResponse> responseForHistoryIndex) {

    public int historySize() {
        return this.historyEntries.size();
    }

    /** One row per {@link treepeater.requestResponse.HistoryEntry} for tool summaries and navigation. */
    public record HistoryEntryInfo(int index, String time, String targetLabel) {}
}
