package treepeater.api.tools.core;

import treepeater.ai.AgentMode;
import treepeater.ai.ToolActionLevel;

/** Shared approval rules for agent mode and tool sensitivity. */
public final class ToolActionLevelHelper {

    private ToolActionLevelHelper() {}

    /**
     * Whether the chat must collect approval before running this tool. Unknown tool names require approval except in
     * {@link AgentMode#AUTONOMOUS}.
     */
    public static boolean requiresUserApprovalInAgentMode(
            String toolName, ToolActionLevel level, AgentMode mode) {
        if (mode == null) {
            mode = AgentMode.ASK;
        }
        if (level == null) {
            return mode != AgentMode.AUTONOMOUS;
        }
        return switch (mode) {
            case ASK -> level != ToolActionLevel.READ_ONLY;
            case HELPER -> level == ToolActionLevel.EXECUTE;
            case AUTONOMOUS -> false;
        };
    }
}
