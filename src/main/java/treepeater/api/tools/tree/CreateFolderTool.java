package treepeater.api.tools.tree;

import treepeater.ai.ToolActionLevel;
import treepeater.api.Json;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.ToolHumanUsage;
import treepeater.api.tools.core.AbstractServiceTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;

public final class CreateFolderTool extends AbstractServiceTool {

    public static final String NAME = "create_folder";

    private static final String SCHEMA =
            """
            {"type":"object","properties":{\
            "parent_id":{"type":"integer","description":"Parent folder id; 0 or omitted for the tree root"},\
            "name":{"type":"string","description":"Folder name (default \\"New Folder\\")"}},\
            "additionalProperties":false}""";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Creates a folder under parent_id (0 for the tree root) and returns its node id, "
                + "which can then be used as the import destination.";
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
        var a = inv.args();
        int parentId = Json.integer(a, "parent_id", "parentId").orElse(0);
        return requireService(inv).createFolder(parentId, Json.text(a, "name"));
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        var args = inv.args();
        int parentId = Json.integer(args, "parent_id", "parentId").orElse(0);
        String name = Json.text(args, "name");
        StringBuilder det = new StringBuilder();
        if (name != null && !name.isBlank()) {
            det.append(ToolHumanUsage.quotedSnippet(name, 80));
        }
        if (parentId > 0) {
            if (!det.isEmpty()) {
                det.append(" · ");
            }
            det.append("parent id ").append(parentId);
        }
        return new HumanToolUsage("Create folder", det.toString());
    }
}
