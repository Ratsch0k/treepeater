package treepeater.api.tools.core;

import com.fasterxml.jackson.databind.node.ObjectNode;

import treepeater.api.Json;

/** Shared result formatting for tool handlers. */
public final class ToolResults {

    /** Upper bound on JSON returned to the model for a single tool call. */
    public static final int MAX_TOOL_RESULT_CHARS = 96_000;

    private ToolResults() {}

    public static String errorJson(String message) {
        ObjectNode n = Json.obj();
        n.put("error", message != null ? message : "tool error");
        return Json.nodeToString(n);
    }

    public static String permissionDenied() {
        return errorJson("permission denied");
    }

    /** Truncates oversized payloads with a pointer to paginated alternatives. */
    public static String capResult(String result) {
        if (result == null) {
            return errorJson("tool returned null");
        }
        if (result.length() <= MAX_TOOL_RESULT_CHARS) {
            return result;
        }
        ObjectNode n = Json.obj();
        n.put("error", "tool_result_too_large");
        n.put("result_chars", result.length());
        n.put("max_result_chars", MAX_TOOL_RESULT_CHARS);
        n.put(
                "hint",
                "Call read_http_message with offset and max_bytes for smaller slices, or use search_http_message to "
                        + "match a small substring; narrow search scope (headers|body) when possible.");
        return Json.nodeToString(n);
    }
}
