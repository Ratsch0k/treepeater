package treepeater.api.tools.importing;

import treepeater.ai.ToolActionLevel;
import treepeater.api.Json;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.AbstractServiceTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;

public final class ImportHttpRequestTool extends AbstractServiceTool {

    public static final String NAME = "import_http_request";

    private static final String SCHEMA =
            buildSchema(
                    "\"folder_id\":{\"type\":\"integer\",\"description\":\"Destination folder id; 0 or omitted for the tree root\"},"
                            + ImportSchemas.DIRECT_PROPERTIES);

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Imports a request you supply as a single leaf, like \"Send to Treepeater (direct)\". "
                + "Provide base_url plus request_utf8. Returns the new request_node_id.";
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
                .importRequest(folderId, ImportRequestBuilder.buildRequestResponse(parsed), ImportRequestBuilder.directOptions(parsed));
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        return new HumanToolUsage("Import HTTP request (direct)", ImportRequestBuilder.importTargetDetail(inv.args()));
    }

    private static String buildSchema(String extra) {
        return "{\"type\":\"object\",\"properties\":{" + ImportSchemas.REQUEST_PROPERTIES + "," + extra + "},\"additionalProperties\":false}";
    }
}
