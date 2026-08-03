package treepeater.api.tools.http;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.http.support.HttpTargetSupport;

public final class CopyTreepeaterNodeTool extends AbstractHttpTargetTool {

    public static final String NAME = "copy_treepeater_node";

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
        return HttpTargetSupport.copyTreepeaterNodeSchema();
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.WRITE;
    }
}
