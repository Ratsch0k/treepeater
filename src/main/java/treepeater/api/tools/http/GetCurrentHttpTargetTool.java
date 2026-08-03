package treepeater.api.tools.http;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.http.support.HttpTargetSupport;

public final class GetCurrentHttpTargetTool extends AbstractHttpTargetTool {

    public static final String NAME = "get_current_http_target";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Current tab: scheme, host, port, SNI, method, URL, path, send history. "
                + "Optional request_node_id (search_tabs). Raw wire: read_http_message / search_http_message.";
    }

    @Override
    public String inputSchemaJson() {
        return HttpTargetSupport.optionalTabParamsSchema();
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.READ_ONLY;
    }
}
