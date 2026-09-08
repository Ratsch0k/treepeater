package treepeater.api.tools.http;

import com.fasterxml.jackson.databind.JsonNode;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.AbstractEditorTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;
import treepeater.api.tools.http.support.HttpEditorService;

public final class CopyTreepeaterNodeTool extends AbstractEditorTool {

    public static final String NAME = "copy_treepeater_node";

    private static final String SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"request_node_id\":{\"type\":\"integer\",\"minimum\":1,"
                    + "\"description\":\"Request tree node id to copy (from search_tabs or a prior copy).\"},"
                    + "\"name\":{\"type\":\"string\",\"description\":\"Name for the new node/tab.\"},"
                    + "\"placement\":{\"type\":\"string\",\"enum\":[\"after\",\"top\",\"bottom\"],\"default\":\"after\","
                    + "\"description\":\"Sibling position under the source parent: after=immediately after source, "
                    + "top=first child, bottom=last child.\"}},"
                    + "\"required\":[\"request_node_id\",\"name\"],\"additionalProperties\":false}";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Duplicate a request tree node as a new sibling tab with the given name. Returns "
                + "request_node_id for the copy. Use when the user wants a copy, or in multi-step "
                + "processes to create separate steps from a baseline node. request_node_id is any "
                + "request node id (from search_tabs or a prior copy).";
    }

    @Override
    public String inputSchemaJson() {
        return SCHEMA;
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.WRITE;
    }

    @Override
    public String invoke(ToolInvocation inv) {
        return capResult(HttpEditorService.copyTreepeaterNode(requireBridge(inv), inv.args()));
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        JsonNode args = inv.args();
        JsonNode idN = HttpToolLabels.firstArg(args, "request_node_id", "requestNodeId");
        int srcId = idN != null && idN.isNumber() ? idN.intValue() : 0;
        String newName = HttpToolLabels.textArg(args, "name");
        String placement = HttpToolLabels.textArg(args, "placement");
        StringBuilder det = new StringBuilder();
        if (!newName.isEmpty()) {
            det.append(HttpToolLabels.quotedSnippet(newName, 80));
        }
        if (!placement.isEmpty()) {
            if (!det.isEmpty()) {
                det.append(" · ");
            }
            det.append("placement ").append(placement);
        }
        return new HumanToolUsage("Copy treepeater node · node id " + srcId, det.toString());
    }
}
