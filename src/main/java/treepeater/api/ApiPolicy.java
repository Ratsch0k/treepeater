package treepeater.api;

import treepeater.ai.HttpTargetTools;
import treepeater.ai.ToolActionLevel;
import treepeater.settings.TreepeaterSettings;

/**
 * What external callers (REST and MCP) may do. The in-Burp AI chat is gated interactively by
 * {@link treepeater.ai.ChatTooling#executeWithApproval} instead; API callers have no way to prompt the
 * user, so the decision is made once in settings.
 */
public record ApiPolicy(boolean allowWrite, boolean allowExecute) {

    public static ApiPolicy readOnly() {
        return new ApiPolicy(false, false);
    }

    public static ApiPolicy full() {
        return new ApiPolicy(true, true);
    }

    public static ApiPolicy fromSettings() {
        TreepeaterSettings settings = TreepeaterSettings.getInstance();
        return new ApiPolicy(settings.isApiAllowWrite(), settings.isApiAllowExecute());
    }

    /**
     * Whether {@code toolName} may run. {@link HttpTargetTools#BATCH_HTTP_TARGET_TOOLS} is classified
     * READ_ONLY because the chat gates each nested child individually, but an API caller's children are
     * not re-prompted, so batch here requires every permission.
     */
    public boolean allows(String toolName, ToolActionLevel level) {
        if (HttpTargetTools.BATCH_HTTP_TARGET_TOOLS.equals(toolName)) {
            return this.allowWrite && this.allowExecute;
        }
        if (level == null) {
            return this.allowWrite && this.allowExecute;
        }
        return switch (level) {
            case READ_ONLY -> true;
            case WRITE -> this.allowWrite;
            case EXECUTE -> this.allowExecute;
        };
    }

    /** Error result for a tool the current policy forbids. */
    public static String deniedResult(String toolName) {
        return Json.error(
                "tool '" + toolName + "' is not permitted by the current API policy; enable it in the "
                        + "Treepeater settings under API & MCP server");
    }
}
