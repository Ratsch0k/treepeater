package treepeater.api.tools.tree;

import java.util.OptionalInt;

import com.fasterxml.jackson.databind.JsonNode;

import treepeater.ai.ToolActionLevel;
import treepeater.api.Json;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.AbstractServiceTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;

public final class SetNodeNotesTool extends AbstractServiceTool {

    public static final String NAME = "set_node_notes";

    private static final String SCHEMA =
            """
            {"type":"object","properties":{\
            "node_id":{"type":"integer","description":"Request node id; folders have no notes"},\
            "notes":{"type":"string","description":"Replacement notes text; empty string clears them"}},\
            "required":["node_id","notes"],"additionalProperties":false}""";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Replaces the notes on a request node.";
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
        JsonNode notes = Json.first(inv.args(), "notes");
        String text = notes != null && notes.isTextual() ? notes.asText() : "";
        return requireService(inv).setNotes(nodeId.getAsInt(), text);
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        return new HumanToolUsage("Set node notes" + TreeToolLabels.nodeIdSuffix(inv.args()), "");
    }
}
