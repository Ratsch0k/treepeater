package treepeater.api.tools.tree;

import java.util.OptionalInt;

import treepeater.ai.ToolActionLevel;
import treepeater.api.Json;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.ToolHumanUsage;
import treepeater.api.tools.core.AbstractServiceTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;

public final class SetNodeStatusTool extends AbstractServiceTool {

    public static final String NAME = "set_node_status";

    private static final String SCHEMA =
            """
            {"type":"object","properties":{\
            "node_id":{"type":"integer"},\
            "status_id":{"type":"string","description":"Status id from list_statuses"}},\
            "required":["node_id","status_id"],"additionalProperties":false}""";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Sets the status of a folder or request node. Valid ids come from list_statuses.";
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
        if (nodeId.isEmpty()) {
            return Json.error("node_id required");
        }
        return requireService(inv).setNodeStatus(nodeId.getAsInt(), Json.text(inv.args(), "status_id", "statusId"));
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        String statusId = Json.text(inv.args(), "status_id", "statusId");
        String det =
                statusId != null && !statusId.isBlank()
                        ? ToolHumanUsage.quotedSnippet(statusId, 80)
                        : "";
        return new HumanToolUsage("Set node status" + TreeToolLabels.nodeIdSuffix(inv.args()), det);
    }
}
