package treepeater.ai;

/** One row in the {@link treepeater.api.tools.http.SearchTabsTool} tool result. */
public record SearchTabRow(
        int requestNodeId, String title, boolean selected, String method, String url, boolean urlTruncated) {}
