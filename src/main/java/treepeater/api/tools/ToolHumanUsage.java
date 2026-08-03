package treepeater.api.tools;

import treepeater.api.TreepeaterToolRegistry;
import treepeater.api.tools.core.ToolLabelContext;

/** Resolves human-readable tool labels for chat transcript cards via the tool registry. */
public final class ToolHumanUsage {

    private ToolHumanUsage() {}

    public static HumanToolUsage forTool(String toolName, String argumentsJson, int viewerHistoryIndex) {
        return forTool(toolName, argumentsJson, viewerHistoryIndex, Integer.MIN_VALUE, null);
    }

    public static HumanToolUsage forTool(
            String toolName, String argumentsJson, int viewerHistoryIndex, int uiSelectedRequestNodeId) {
        return forTool(toolName, argumentsJson, viewerHistoryIndex, uiSelectedRequestNodeId, null);
    }

    public static HumanToolUsage forTool(
            String toolName,
            String argumentsJson,
            int viewerHistoryIndex,
            int uiSelectedRequestNodeId,
            TreepeaterToolRegistry registry) {
        ToolLabelContext labelCtx = new ToolLabelContext(viewerHistoryIndex, uiSelectedRequestNodeId);
        if (registry != null) {
            return registry.humanLabelFor(toolName, argumentsJson, labelCtx);
        }
        return new HumanToolUsage("Working…", "");
    }

    public static String quotedSnippet(String s, int maxTotal) {
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
