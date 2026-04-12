package treepeater.ai;

import java.util.List;

/**
 * Built-in tools that expose the current editor HTTP target (scheme, host, port, method, URL, path).
 */
public final class HttpTargetTools {
    /** Tool name: returns JSON from {@link HttpTargetSnapshot#toJson()}. */
    public static final String GET_HTTP_TARGET = "get_http_target";

    private static final String EMPTY_PARAMS_SCHEMA =
            """
            {"type":"object","properties":{},"additionalProperties":false}\
            """;

    private HttpTargetTools() {}

    public static List<ChatToolDefinition> definitions() {
        return List.of(
                new ChatToolDefinition(
                        GET_HTTP_TARGET,
                        "Returns the HTTP target and request line for the request currently open in Treepeater: "
                                + "scheme, host, port, SNI flag, method, full URL, and path (JSON object).",
                        EMPTY_PARAMS_SCHEMA));
    }

    /**
     * Dispatches built-in tools against a snapshot supplier (typically the live request editor).
     */
    public static String execute(String toolName, String argumentsJson, HttpTargetSnapshot snapshot) {
        if (snapshot == null) {
            return "{\"error\":\"no target context\"}";
        }
        if (GET_HTTP_TARGET.equals(toolName)) {
            return snapshot.toJson();
        }
        return "{\"error\":\"unknown tool: " + escapeJson(toolName) + "\"}";
    }

    /**
     * Single-line status for the UI when a tool runs (what the agent is doing, e.g. {@code Getting target}).
     */
    public static String humanReadableUsage(String toolName, String argumentsJson) {
        if (GET_HTTP_TARGET.equals(toolName)) {
            return "Getting target";
        }
        return "Working…";
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
