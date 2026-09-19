package treepeater.api.tools.http;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.AbstractEditorTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;
import treepeater.api.tools.http.support.HttpEditorService;

public final class SendCurrentHttpRequestTool extends AbstractEditorTool {

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
        return HttpToolSchemas.OPTIONAL_TAB_PARAMS;
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.EXECUTE;
    }

    @Override
    public String invoke(ToolInvocation inv) throws Exception {
        return capResult(HttpEditorService.sendCurrentHttpRequest(requireContext(inv)));
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        String nodeSuf = HttpToolLabels.formatRequestNodeIdSuffix(inv.args(), labelCtx.uiSelectedRequestNodeId());
        return new HumanToolUsage(
                "Send current HTTP request" + nodeSuf,
                "Sends the in-editor request and waits for the response (status only)");
    }
}
