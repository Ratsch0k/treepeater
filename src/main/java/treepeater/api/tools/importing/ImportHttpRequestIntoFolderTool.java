package treepeater.api.tools.importing;

import java.util.OptionalInt;

import treepeater.ai.ToolActionLevel;
import treepeater.api.Json;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.AbstractServiceTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;
import treepeater.importing.ImportOptions;

public final class ImportHttpRequestIntoFolderTool extends AbstractServiceTool {

    public static final String NAME = "import_http_request_into_folder";

    private static final String SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
                    + ImportSchemas.REQUEST_PROPERTIES
                    + ",\"folder_id\":{\"type\":\"integer\",\"description\":\"Destination folder id, from list_tree or create_folder\"},"
                    + "\"placement\":{\"type\":\"string\",\"enum\":[\"direct\",\"path_aware\"],\"description\":\"How to place the request under the folder (default direct)\"},"
                    + ImportSchemas.DIRECT_PROPERTIES
                    + ","
                    + ImportSchemas.PATH_AWARE_PROPERTIES
                    + "},\"required\":[\"folder_id\"],\"additionalProperties\":false}";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Imports a request you supply under an explicit folder_id, the headless equivalent of "
                + "\"Send to Treepeater (manual)\". Set placement to direct or path_aware.";
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
        OptionalInt folderId = Json.integer(parsed, "folder_id", "folderId");
        if (folderId.isEmpty()) {
            return Json.error("folder_id required");
        }
        String placement = Json.text(parsed, "placement");
        ImportOptions options =
                "path_aware".equalsIgnoreCase(placement) || "pathAware".equals(placement)
                        ? ImportRequestBuilder.pathAwareOptions(parsed)
                        : ImportRequestBuilder.directOptions(parsed);
        return requireService(inv)
                .importRequest(folderId.getAsInt(), ImportRequestBuilder.buildRequestResponse(parsed), options);
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        var args = inv.args();
        OptionalInt folderId = Json.integer(args, "folder_id", "folderId");
        String placement = Json.text(args, "placement");
        StringBuilder det = new StringBuilder(ImportRequestBuilder.importTargetDetail(args));
        if (folderId.isPresent()) {
            if (!det.isEmpty()) {
                det.append(" · ");
            }
            det.append("folder id ").append(folderId.getAsInt());
        }
        if (placement != null && !placement.isBlank()) {
            if (!det.isEmpty()) {
                det.append(" · ");
            }
            det.append("placement ").append(placement);
        }
        return new HumanToolUsage("Import HTTP request into folder", det.toString());
    }
}
