package treepeater.api.tools.tree;

import treepeater.ai.ToolActionLevel;
import treepeater.api.Json;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.AbstractServiceTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;

public final class ListTreeTool extends AbstractServiceTool {

    public static final String NAME = "list_tree";

    private static final String SCHEMA =
            """
            {"type":"object","properties":{\
            "node_id":{"type":"integer","description":"Subtree root; 0 or omitted for the whole tree"},\
            "max_depth":{"type":"integer","description":"Levels to include, 1-32 (default 32)"},\
            "include_requests":{"type":"boolean","description":"Include request leaves (default true)"}},\
            "additionalProperties":false}""";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Treepeater tree as nested JSON. Each node has id, type folder|request, name, "
                + "status_id, parent_id and path; requests also carry method and url. Use node_id "
                + "to scope to a subtree and max_depth to limit size.";
    }

    @Override
    public String inputSchemaJson() {
        return SCHEMA;
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.READ_ONLY;
    }

    @Override
    public String invoke(ToolInvocation inv) {
        var a = inv.args();
        int nodeId = Json.integer(a, "node_id", "nodeId").orElse(0);
        int maxDepth = Json.integer(a, "max_depth", "maxDepth").orElse(0);
        boolean includeRequests = Json.bool(a, "include_requests", "includeRequests").orElse(Boolean.TRUE);
        return requireService(inv).treeJson(nodeId, maxDepth, includeRequests);
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        var args = inv.args();
        int nodeId = Json.integer(args, "node_id", "nodeId").orElse(0);
        int maxDepth = Json.integer(args, "max_depth", "maxDepth").orElse(0);
        StringBuilder det = new StringBuilder();
        if (nodeId > 0) {
            det.append("subtree from node ").append(nodeId);
        }
        if (maxDepth > 0) {
            if (!det.isEmpty()) {
                det.append(" · ");
            }
            det.append("max depth ").append(maxDepth);
        }
        return new HumanToolUsage("List tree", det.toString());
    }
}
