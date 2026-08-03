package treepeater.ai;

import treepeater.api.TreepeaterToolRegistry;
import treepeater.api.tools.core.ToolActionLevelHelper;

/**
 * {@link ToolRunPolicy} derived from {@link AgentMode} and tool action levels in the registry.
 */
public record AgentModeToolPolicy(AgentMode mode, TreepeaterToolRegistry registry) implements ToolRunPolicy {

    public AgentModeToolPolicy {
        if (mode == null) {
            mode = AgentMode.ASK;
        }
    }

    @Override
    public boolean requiresApproval(String toolName) {
        ToolActionLevel level =
                this.registry != null ? this.registry.actionLevelFor(toolName) : null;
        return ToolActionLevelHelper.requiresUserApprovalInAgentMode(toolName, level, this.mode);
    }
}
