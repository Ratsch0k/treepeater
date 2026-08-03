package treepeater.api.tools.http;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.http.support.HttpTargetSupport;

public final class SearchTabsTool extends AbstractHttpTargetTool {

    public static final String NAME = "search_tabs";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Paged open tabs. Empty query = all. Query matches live method+URL or title (CI). "
                + "Returns request_node_id. offset default 0; page_size default "
                + HttpTargetSupport.defaultTabPageSize()
                + " max "
                + HttpTargetSupport.maxTabPageSize()
                + ".";
    }

    @Override
    public String inputSchemaJson() {
        return HttpTargetSupport.searchTabsSchema();
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.READ_ONLY;
    }
}
