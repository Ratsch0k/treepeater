package treepeater.api.tools.tree;

import java.util.OptionalInt;

import treepeater.ai.ToolActionLevel;
import treepeater.api.Json;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.AbstractServiceTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;

public final class MoveNodeTool extends AbstractServiceTool {

    public static final String NAME = "move_node";

    private static final String SCHEMA =
            """
            {"type":"object","properties":{\
            "node_id":{"type":"integer","description":"Node to move"},\
            "parent_id":{"type":"integer","description":"Destination folder id; 0 for the tree root"},\
            "index":{"type":"integer","description":"Position among the destination's children; omitted appends"}},\
            "required":["node_id","parent_id"],"additionalProperties":false}""";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Reparents a node under another folder. Moving a node into its own subtree is "
                + "rejected. Open tabs for the node stay open.";
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
        OptionalInt nodeId = Json.integer(inv.args(), "node_id", "nodeId");
        OptionalInt parentId = Json.integer(inv.args(), "parent_id", "parentId");
        if (nodeId.isEmpty() || parentId.isEmpty()) {
            return Json.error("node_id and parent_id required");
        }
        int index = Json.integer(inv.args(), "index").orElse(-1);
        return requireService(inv).moveNode(nodeId.getAsInt(), parentId.getAsInt(), index);
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        var args = inv.args();
        OptionalInt nodeId = Json.integer(args, "node_id", "nodeId", "id");
        OptionalInt parentId = Json.integer(args, "parent_id", "parentId");
        OptionalInt index = Json.integer(args, "index");
        StringBuilder det = new StringBuilder();
        if (parentId.isPresent()) {
            det.append("parent id ").append(parentId.getAsInt());
        }
        if (index.isPresent() && index.getAsInt() >= 0) {
            if (!det.isEmpty()) {
                det.append(" · ");
            }
            det.append("index ").append(index.getAsInt());
        }
        String title =
                nodeId.isPresent() ? "Move node · id " + nodeId.getAsInt() : "Move node";
        return new HumanToolUsage(title, det.toString());
    }
}
