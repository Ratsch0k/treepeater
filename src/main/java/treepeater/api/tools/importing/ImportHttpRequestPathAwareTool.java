package treepeater.api.tools.importing;

import treepeater.ai.ToolActionLevel;
import treepeater.api.Json;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.AbstractServiceTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;

public final class ImportHttpRequestPathAwareTool extends AbstractServiceTool {

    public static final String NAME = "import_http_request_path_aware";

    private static final String SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
                    + ImportSchemas.REQUEST_PROPERTIES
                    + ",\"folder_id\":{\"type\":\"integer\",\"description\":\"Folder to build the path under; 0 or omitted for the tree root\"},"
                    + ImportSchemas.PATH_AWARE_PROPERTIES
                    + "},\"additionalProperties\":false}";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Imports a request you supply using the path-aware importer, like \"Send to Treepeater "
                + "(path-aware)\": folders are built from the URL path, reusing existing ones. "
                + "All placement options default to the user's settings. Returns request_node_id "
                + "and the folder_path that was used.";
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
        var parsed = inv.args();
        int folderId = Json.integer(parsed, "folder_id", "folderId").orElse(0);
        return requireService(inv)
                .importRequest(
                        folderId,
                        ImportRequestBuilder.buildRequestResponse(parsed),
                        ImportRequestBuilder.pathAwareOptions(parsed));
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        return new HumanToolUsage(
                "Import HTTP request (path-aware)", ImportRequestBuilder.importTargetDetail(inv.args()));
    }
}
