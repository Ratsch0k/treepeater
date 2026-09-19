package treepeater.api.tools.http.support;

import java.util.List;

import burp.api.montoya.http.message.requests.HttpRequest;

import treepeater.ai.SearchTabRow;

/** Tab listing and copy response formatting shared with the UI. */
public final class TabListingFormatter {

    /** Max URL characters per row in {@code search_tabs} results before truncation. */
    public static final int MAX_TAB_LIST_URL_CHARS = 512;

    private TabListingFormatter() {}

    public static String formatSearchTabsResponse(
            int total, int offset, int pageSize, boolean hasMore, List<SearchTabRow> page) {
        return HttpEditorService.formatSearchTabsResponse(total, offset, pageSize, hasMore, page);
    }

    public static String formatCopyTreepeaterNodeResponse(int requestNodeId, String name) {
        return HttpEditorService.formatCopyTreepeaterNodeResponse(requestNodeId, name);
    }
}
