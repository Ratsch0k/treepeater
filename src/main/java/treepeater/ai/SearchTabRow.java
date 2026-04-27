package treepeater.ai;

/** One row in the {@link HttpTargetTools#SEARCH_TABS} tool result. */
public record SearchTabRow(
        int requestNodeId, String title, boolean selected, String method, String url, boolean urlTruncated) {}
