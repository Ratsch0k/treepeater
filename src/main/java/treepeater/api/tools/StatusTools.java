package treepeater.api.tools;

import treepeater.ai.ToolActionLevel;
import treepeater.api.TreepeaterService;
import treepeater.api.TreepeaterTool;
import treepeater.api.TreepeaterToolRegistry;

/** Exposes the project's status registry so callers can resolve valid ids before setting a status. */
public final class StatusTools {

    public static final String LIST_STATUSES = "list_statuses";

    private static final String EMPTY_SCHEMA =
            "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}";

    private StatusTools() {}

    public static void register(TreepeaterToolRegistry registry, TreepeaterService service) {
        registry.add(
                new TreepeaterTool(
                        LIST_STATUSES,
                        "Statuses defined in this project, each with id and display name, plus the default "
                                + "status id. Use these ids for set_node_status and the import tools.",
                        EMPTY_SCHEMA,
                        ToolActionLevel.READ_ONLY,
                        args -> service.statusesJson()));
    }

    /** @return label for a status tool, or {@code null} when {@code toolName} is not handled here */
    public static HumanToolUsage humanToolUsage(String toolName, String argumentsJson) {
        if (LIST_STATUSES.equals(toolName)) {
            return new HumanToolUsage("List statuses", "");
        }
        return null;
    }
}
