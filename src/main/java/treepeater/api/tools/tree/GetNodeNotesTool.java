package treepeater.api.tools.tree;

import java.util.OptionalInt;

import treepeater.ai.ToolActionLevel;
import treepeater.api.Json;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.AbstractServiceTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;
import treepeater.api.tools.core.ToolSchemas;

public final class GetNodeNotesTool extends AbstractServiceTool {

    public static final String NAME = "get_node_notes";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Notes attached to a request node.";
    }

    @Override
    public String inputSchemaJson() {
        return ToolSchemas.NODE_ID;
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.READ_ONLY;
    }

    @Override
    public String invoke(ToolInvocation inv) {
        OptionalInt nodeId = Json.integer(inv.args(), "node_id", "nodeId", "id");
        if (nodeId.isEmpty()) {
            return Json.error("node_id required");
        }
        return requireService(inv).notesJson(nodeId.getAsInt());
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        return new HumanToolUsage("Get node notes" + TreeToolLabels.nodeIdSuffix(inv.args()), "");
    }
}
