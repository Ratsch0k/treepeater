package treepeater.api.tools.tree;

import java.util.OptionalInt;

import treepeater.ai.ToolActionLevel;
import treepeater.api.Json;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.ToolHumanUsage;
import treepeater.api.tools.core.AbstractServiceTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;

public final class RenameNodeTool extends AbstractServiceTool {

    public static final String NAME = "rename_node";

    private static final String SCHEMA =
            """
            {"type":"object","properties":{\
            "node_id":{"type":"integer"},\
            "name":{"type":"string","description":"New display name"}},\
            "required":["node_id","name"],"additionalProperties":false}""";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Renames a folder or request node.";
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
        return requireService(inv).renameNode(nodeId.getAsInt(), Json.text(inv.args(), "name"));
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        String name = Json.text(inv.args(), "name");
        String det = name != null && !name.isBlank() ? ToolHumanUsage.quotedSnippet(name, 80) : "";
        return new HumanToolUsage("Rename node" + TreeToolLabels.nodeIdSuffix(inv.args()), det);
    }
}
