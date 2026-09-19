package treepeater.api.tools.tree;

import java.util.OptionalInt;

import treepeater.ai.ToolActionLevel;
import treepeater.api.Json;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.AbstractServiceTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;
import treepeater.api.tools.core.ToolSchemas;

public final class DeleteNodeTool extends AbstractServiceTool {

    public static final String NAME = "delete_node";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Deletes a node and its whole subtree, closing any open tabs it contains. "
                + "This cannot be undone.";
    }

    @Override
    public String inputSchemaJson() {
        return ToolSchemas.NODE_ID;
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.WRITE;
    }

    @Override
    public String invoke(ToolInvocation inv) {
        OptionalInt nodeId = Json.integer(inv.args(), "node_id", "nodeId", "id");
        if (nodeId.isEmpty()) {
            return Json.error("node_id required");
        }
        return requireService(inv).deleteNode(nodeId.getAsInt());
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        return new HumanToolUsage(
                "Delete node" + TreeToolLabels.nodeIdSuffix(inv.args()),
                "Deletes the node and its whole subtree; cannot be undone");
    }
}
