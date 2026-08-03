package treepeater.api.tools.http;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.http.support.HttpTargetSupport;

public final class SendCurrentHttpRequestTool extends AbstractHttpTargetTool {

    public static final String NAME = "send_current_http_request";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Send current repeater request; wait for response. Updates UI/history. Result: status_code only. "
                + "Optional request_node_id.";
    }

    @Override
    public String inputSchemaJson() {
        return HttpTargetSupport.optionalTabParamsSchema();
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.EXECUTE;
    }
}
