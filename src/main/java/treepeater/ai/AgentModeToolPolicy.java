package treepeater.ai;

/**
 * {@link ToolRunPolicy} derived from {@link AgentMode} and {@link HttpTargetTools} action levels.
 */
public record AgentModeToolPolicy(AgentMode mode) implements ToolRunPolicy {
    public AgentModeToolPolicy {
        if (mode == null) {
            mode = AgentMode.ASK;
        }
    }

    @Override
    public boolean requiresApproval(String toolName) {
        return HttpTargetTools.requiresUserApprovalInAgentMode(toolName, this.mode);
    }
}
