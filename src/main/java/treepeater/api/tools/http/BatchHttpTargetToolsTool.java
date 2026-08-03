package treepeater.api.tools.http;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.http.support.HttpTargetSupport;

public final class BatchHttpTargetToolsTool extends AbstractHttpTargetTool {

    public static final String NAME = "batch_http_target_tools";

    /** Stable id for nested tool approval cards when a step runs inside a batch. */
    public static String syntheticChildToolCallId(String parentToolCallId, int batchSlot) {
        String base =
                parentToolCallId != null && !parentToolCallId.isBlank() ? parentToolCallId.trim() : "tool";
        return base + ":batch:" + batchSlot;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Runs several built-in tools in fixed order in a single call; each tools[] entry is "
                + "tool_name plus an arguments object ({} if none); returns per-step results. "
                + "Strongly prefer this whenever your plan needs more than one tool on the same turn—"
                + "especially ordered flows such as changing the request, send_current_http_request, "
                + "then read_http_message with side \"response\". Steps run one after another; write/send "
                + "still need approval.";
    }

    @Override
    public String inputSchemaJson() {
        return HttpTargetSupport.batchHttpTargetToolsSchema();
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.READ_ONLY;
    }
}
