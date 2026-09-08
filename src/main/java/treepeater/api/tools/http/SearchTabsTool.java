package treepeater.api.tools.http;

import com.fasterxml.jackson.databind.JsonNode;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.AbstractEditorTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;
import treepeater.api.tools.http.support.HttpEditorService;

public final class SearchTabsTool extends AbstractEditorTool {

    public static final String NAME = "search_tabs";

    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;

    private static final String SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"offset\":{\"type\":\"integer\",\"minimum\":0,\"default\":0},\"page_size\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":"
                    + MAX_PAGE_SIZE
                    + ",\"default\":"
                    + DEFAULT_PAGE_SIZE
                    + "},\"query\":{\"type\":\"string\",\"description\":\"Filter method/URL or title; CI\"}},\"additionalProperties\":false}";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Paged open tabs. Empty query = all. Query matches live method+URL or title (CI). "
                + "Returns request_node_id. offset default 0; page_size default "
                + DEFAULT_PAGE_SIZE
                + " max "
                + MAX_PAGE_SIZE
                + ".";
    }

    @Override
    public String inputSchemaJson() {
        return SCHEMA;
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.READ_ONLY;
    }

    @Override
    public String invoke(ToolInvocation inv) {
        return capResult(HttpEditorService.searchTabs(requireBridge(inv), inv.args()));
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        JsonNode args = inv.args();
        int off = 0;
        JsonNode offN = HttpToolLabels.firstArg(args, "offset");
        if (offN != null && offN.isNumber()) {
            off = Math.max(0, offN.intValue());
        }
        int ps = DEFAULT_PAGE_SIZE;
        JsonNode psN = HttpToolLabels.firstArg(args, "page_size", "pageSize");
        if (psN != null && psN.isNumber()) {
            ps = Math.min(MAX_PAGE_SIZE, Math.max(1, psN.intValue()));
        }
        String q = HttpToolLabels.textArg(args, "query", "q", "search");
        String det = q.isEmpty() ? "all tabs" : HttpToolLabels.quotedSnippet(q, 80);
        return new HumanToolUsage("Search repeater tabs · offset " + off + ", page " + ps, det);
    }
}
