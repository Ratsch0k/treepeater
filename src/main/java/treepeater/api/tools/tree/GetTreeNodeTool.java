package treepeater.api.tools.tree;

import java.util.OptionalInt;

import treepeater.ai.ToolActionLevel;
import treepeater.api.Json;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.AbstractServiceTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;
import treepeater.api.tools.core.ToolSchemas;

public final class GetTreeNodeTool extends AbstractServiceTool {

    public static final String NAME = "get_tree_node";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "One tree node in detail: name, status, path, and for requests the method, url, "
                + "notes and send-history size. Immediate children are included for folders.";
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
        return requireService(inv).nodeJson(nodeId.getAsInt());
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        return new HumanToolUsage("Get tree node" + TreeToolLabels.nodeIdSuffix(inv.args()), "");
    }
}
