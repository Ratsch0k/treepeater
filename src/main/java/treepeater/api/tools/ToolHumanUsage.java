package treepeater.api.tools;

/**
 * Resolves human-readable tool labels for chat transcript cards by delegating to each tool group.
 */
public final class ToolHumanUsage {

    private ToolHumanUsage() {}

    public static HumanToolUsage forTool(String toolName, String argumentsJson, int viewerHistoryIndex) {
        return forTool(toolName, argumentsJson, viewerHistoryIndex, Integer.MIN_VALUE);
    }

    /**
     * @param uiSelectedRequestNodeId {@link treepeater.ai.RepeaterTabAgentBridge#uiSelectedRequestNodeIdForToolCard()};
     *     only used by editor tools. {@link Integer#MIN_VALUE} skips suffix unless {@code request_node_id} is set.
     */
    public static HumanToolUsage forTool(
            String toolName, String argumentsJson, int viewerHistoryIndex, int uiSelectedRequestNodeId) {
        HumanToolUsage usage =
                HttpTargetTools.humanToolUsage(toolName, argumentsJson, viewerHistoryIndex, uiSelectedRequestNodeId);
        if (usage != null) {
            return usage;
        }
        usage = TreeTools.humanToolUsage(toolName, argumentsJson);
        if (usage != null) {
            return usage;
        }
        usage = ImportTools.humanToolUsage(toolName, argumentsJson);
        if (usage != null) {
            return usage;
        }
        usage = StatusTools.humanToolUsage(toolName, argumentsJson);
        if (usage != null) {
            return usage;
        }
        return new HumanToolUsage("Working…", "");
    }

    static String quotedSnippet(String s, int maxTotal) {
        if (s == null) {
            s = "";
        }
        return "\"" + truncate(s, Math.max(8, maxTotal - 2)) + "\"";
    }

    static String truncate(String s, int maxChars) {
        if (s == null) {
            return "";
        }
        if (maxChars <= 0 || s.length() <= maxChars) {
            return s;
        }
        if (maxChars <= 3) {
            return s.substring(0, maxChars);
        }
        return s.substring(0, maxChars - 1) + "…";
    }
}
