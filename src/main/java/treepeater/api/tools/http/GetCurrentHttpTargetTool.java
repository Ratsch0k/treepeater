package treepeater.api.tools.http;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.AbstractEditorTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;
import treepeater.api.tools.http.support.HttpEditorService;

public final class GetCurrentHttpTargetTool extends AbstractEditorTool {

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
        return HttpToolSchemas.OPTIONAL_TAB_PARAMS;
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.READ_ONLY;
    }

    @Override
    public String invoke(ToolInvocation inv) {
        return capResult(HttpEditorService.currentTarget(requireContext(inv)));
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        String nodeSuf = HttpToolLabels.formatRequestNodeIdSuffix(inv.args(), labelCtx.uiSelectedRequestNodeId());
        return new HumanToolUsage("Getting current repeater target and send history" + nodeSuf, "");
    }
}
