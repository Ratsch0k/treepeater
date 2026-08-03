package treepeater.api.tools.status;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.AbstractServiceTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;
import treepeater.api.tools.core.ToolSchemas;

/** Exposes the project's status registry so callers can resolve valid ids before setting a status. */
public final class ListStatusesTool extends AbstractServiceTool {

    public static final String NAME = "list_statuses";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Statuses defined in this project, each with id and display name, plus the default "
                + "status id. Use these ids for set_node_status and the import tools.";
    }

    @Override
    public String inputSchemaJson() {
        return ToolSchemas.EMPTY_OBJECT;
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.READ_ONLY;
    }

    @Override
    public String invoke(ToolInvocation inv) {
        return requireService(inv).statusesJson();
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        return new HumanToolUsage("List statuses", "");
    }
}
