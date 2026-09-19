package treepeater.api.tools.http;

import java.util.Locale;
import java.util.OptionalInt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import treepeater.ai.AgentToolContext;
import treepeater.ai.TreepeaterTabAgentBridge;

/** Formatting helpers shared by HTTP/editor tool {@code humanLabel} implementations and chat transcript cards. */
public final class HttpToolLabels {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_SEMANTIC_HUMAN_DETAIL_CHARS = 12_000;

    private HttpToolLabels() {}

    /**
     * History index for tool transcript labels when the tool targets a specific tab via {@code request_node_id}.
     */
    public static int viewerHistoryIndexForToolCard(String toolName, String argumentsJson, TreepeaterTabAgentBridge bridge) {
        if (bridge == null
                || SearchTabsTool.NAME.equals(toolName)
                || BatchHttpTargetToolsTool.NAME.equals(toolName)
                || CopyTreepeaterNodeTool.NAME.equals(toolName)) {
            return Integer.MIN_VALUE;
        }
        try {
            JsonNode args = parseArgs(argumentsJson);
            AgentToolContext ctx = bridge.contextForAgent(parseRequestNodeId(args));
            return ctx != null ? ctx.currentHistoryIndex() : Integer.MIN_VALUE;
        } catch (Exception e) {
            return Integer.MIN_VALUE;
        }
    }

    /** UI-selected tab id for chat transcript labels; {@link Integer#MIN_VALUE} if unknown. */
    public static int uiSelectedRequestNodeIdForToolCard(TreepeaterTabAgentBridge bridge) {
        return bridge != null ? bridge.uiSelectedRequestNodeIdForToolCard() : Integer.MIN_VALUE;
    }

    /**
     * When {@code request_node_id} is in args, appends {@code · node id n} unless it matches {@code uiSelectedId}.
     * When args omit it, appends the UI-selected id if known.
     */
    public static String formatRequestNodeIdSuffix(JsonNode args, int uiSelectedId) {
        if (args == null) {
            return "";
        }
        JsonNode n = argFirst(args, "request_node_id", "requestNodeId");
        if (n != null && !n.isNull()) {
            int id;
            if (n.isNumber()) {
                id = n.intValue();
            } else if (n.isTextual()) {
                try {
                    id = Integer.parseInt(n.asText().trim());
                } catch (NumberFormatException e) {
                    return "";
                }
            } else {
                return "";
            }
            if (id < 1) {
                return "";
            }
            if (uiSelectedId != Integer.MIN_VALUE && id == uiSelectedId) {
                return "";
            }
            return " · node id " + id;
        }
        if (uiSelectedId != Integer.MIN_VALUE) {
            return " · node id " + uiSelectedId;
        }
        return "";
    }

    /**
     * Omits the history suffix when the tool targets the same entry the user is viewing, or when the viewer index is
     * unknown ({@link Integer#MIN_VALUE} — suffix is shown so the label stays explicit).
     */
    public static String formatHistoryIndexArg(JsonNode args, int viewerHistoryIndex) {
        JsonNode v = argFirst(args, "history_index", "historyIndex", "index", "entry_index", "entryIndex");
        if (v == null) {
            return "";
        }
        int idx = jsonToInt(v);
        if (viewerHistoryIndex != Integer.MIN_VALUE && idx == viewerHistoryIndex) {
            return "";
        }
        return " · history #" + idx;
    }

    /** First present, non-null value among {@code keys} (root object only). */
    public static JsonNode firstArg(JsonNode args, String... keys) {
        return argFirst(args, keys);
    }

    /** Trimmed text for the first matching key, or {@code ""} when absent. */
    public static String textArg(JsonNode args, String... keys) {
        JsonNode v = argFirst(args, keys);
        if (v == null) {
            return "";
        }
        String s = v.isTextual() ? v.asText().trim() : v.asText();
        return s != null ? s.trim() : "";
    }

    /** Lenient int coercion (numbers, numeric strings), or {@code 0} when absent/unparseable. */
    public static int intArg(JsonNode args, String... keys) {
        return jsonToInt(argFirst(args, keys));
    }

    /** {@code offset}-style arg: floored at 0, default 0. */
    public static int offsetArg(JsonNode args) {
        JsonNode v = argFirst(args, "offset", "byte_offset", "byteOffset", "start");
        return v == null ? 0 : Math.max(0, jsonToInt(v));
    }

    /** Clamped {@code [1, maxValue]} int arg with a default when absent. */
    public static int clampedIntArg(JsonNode args, int defaultValue, int maxValue, String... keys) {
        JsonNode v = argFirst(args, keys);
        if (v == null) {
            return defaultValue;
        }
        return Math.min(maxValue, Math.max(1, jsonToInt(v)));
    }

    /** {@code scope} arg normalized to {@code headers}/{@code body}/{@code all} (default). */
    public static String searchScope(JsonNode args) {
        JsonNode v = argFirst(args, "scope", "where");
        if (v == null || !v.isTextual()) {
            return "all";
        }
        String t = v.asText().trim().toLowerCase(Locale.ROOT);
        if (t.equals("header") || t.equals("headers")) {
            return "headers";
        }
        if (t.equals("body")) {
            return "body";
        }
        return "all";
    }

    /** {@code "Request"}/{@code "Response"} for the {@code side} arg, or {@code ""} when absent/unrecognized. */
    public static String sideLabel(JsonNode args) {
        JsonNode sideNode = argFirst(args, "side", "Side", "http_side", "httpSide");
        String sideNorm = sideNode != null && sideNode.isTextual() ? normalizeSideLiteral(sideNode.asText()) : "";
        return "response".equals(sideNorm) ? "Response" : "request".equals(sideNorm) ? "Request" : "";
    }

    /** Accepts camelCase {@code side} and short aliases ({@code req}/{@code res}). */
    public static String normalizeSideLiteral(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if ("req".equals(t) || "r".equals(t)) {
            return "request";
        }
        if ("res".equals(t) || "resp".equals(t)) {
            return "response";
        }
        return t;
    }

    public static String quotedSnippet(String s, int maxTotal) {
        if (s == null) {
            s = "";
        }
        return "\"" + truncateForStatus(s, Math.max(8, maxTotal - 2)) + "\"";
    }

    public static String singleLinePreview(String s, int max) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String one = s.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        while (one.contains("  ")) {
            one = one.replace("  ", " ");
        }
        return truncateForStatus(one.trim(), max);
    }

    public static String truncateForStatus(String s, int maxChars) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars - 1) + "…";
    }

    /** One line per operation for {@link ApplyHttpRequestSemanticChangesTool#humanLabel}. */
    public static String formatSemanticOperationsHumanDetail(JsonNode args) {
        JsonNode arr = args != null ? args.get("operations") : null;
        if (arr == null || !arr.isArray()) {
            return "(no operations array)";
        }
        if (arr.isEmpty()) {
            return "(empty operations)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(i + 1).append(". ");
            JsonNode raw = arr.get(i);
            if (raw == null || !raw.isObject()) {
                sb.append("(not an object)");
            } else {
                sb.append(formatOneSemanticOperationHumanLine((ObjectNode) raw));
            }
            if (sb.length() >= MAX_SEMANTIC_HUMAN_DETAIL_CHARS) {
                sb.append("\n… (truncated)");
                break;
            }
        }
        return sb.toString();
    }

    private static String formatOneSemanticOperationHumanLine(ObjectNode op) {
        String type = opStringLower(op, "type");
        String action = opStringLower(op, "action");
        if (type.isEmpty()) {
            type = "?";
        }
        if (action.isEmpty()) {
            action = "?";
        }
        String key = opTextTrimmed(op, "key");
        String path = opTextTrimmed(op, "path");
        boolean hasValue = op.has("value");
        JsonNode val = op.get("value");

        return switch (type) {
            case "header" ->
                    "remove".equals(action)
                            ? ("Remove header " + (key.isEmpty() ? "(unnamed)" : truncateForStatus(key, 120)))
                            : ("Set header "
                                    + (key.isEmpty() ? "(unnamed)" : truncateForStatus(key, 80))
                                    + " = "
                                    + semanticValueHumanSnippet(val, hasValue));
            case "cookie" ->
                    "remove".equals(action)
                            ? ("Remove cookie " + (key.isEmpty() ? "(unnamed)" : truncateForStatus(key, 120)))
                            : ("Set cookie "
                                    + (key.isEmpty() ? "(unnamed)" : truncateForStatus(key, 80))
                                    + " = "
                                    + semanticValueHumanSnippet(val, hasValue));
            case "json" ->
                    "remove".equals(action)
                            ? ("JSON remove " + (path.isEmpty() ? "(no path)" : truncateForStatus(path, 200)))
                            : ("JSON set "
                                    + (path.isEmpty() ? "(no path)" : truncateForStatus(path, 200))
                                    + " → "
                                    + semanticValueHumanSnippet(val, hasValue));
            case "xml" ->
                    "remove".equals(action)
                            ? ("XML remove " + (path.isEmpty() ? "(no path)" : truncateForStatus(path, 200)))
                            : ("XML set "
                                    + (path.isEmpty() ? "(no path)" : truncateForStatus(path, 200))
                                    + " → "
                                    + semanticValueHumanSnippet(val, hasValue));
            case "method" ->
                    "Set method "
                            + (hasValue && val != null && val.isTextual()
                                    ? truncateForStatus(val.asText().trim(), 64)
                                    : semanticValueHumanSnippet(val, hasValue));
            case "url" ->
                    "Set URL "
                            + (hasValue && val != null && val.isTextual()
                                    ? truncateForStatus(val.asText().trim(), 220)
                                    : semanticValueHumanSnippet(val, hasValue));
            default -> {
                StringBuilder b = new StringBuilder();
                b.append(type).append(' ').append(action);
                if (!key.isEmpty()) {
                    b.append(" · key ").append(truncateForStatus(key, 80));
                }
                if (!path.isEmpty()) {
                    b.append(" · path ").append(truncateForStatus(path, 120));
                }
                if (hasValue) {
                    b.append(" → ").append(semanticValueHumanSnippet(val, true));
                }
                yield truncateForStatus(b.toString(), 300);
            }
        };
    }

    private static String semanticValueHumanSnippet(JsonNode val, boolean hasValue) {
        if (!hasValue) {
            return "(value omitted)";
        }
        if (val == null || val.isNull()) {
            return "null";
        }
        if (val.isTextual()) {
            return quotedSnippet(val.asText(), 100);
        }
        if (val.isNumber() || val.isBoolean()) {
            return val.asText();
        }
        try {
            return singleLinePreview(JSON.writeValueAsString(val), 160);
        } catch (JsonProcessingException e) {
            return singleLinePreview(val.toString(), 160);
        }
    }

    private static String opStringLower(ObjectNode op, String field) {
        JsonNode v = op.get(field);
        if (v == null || v.isNull() || !v.isTextual()) {
            return "";
        }
        return v.asText().trim().toLowerCase(Locale.ROOT);
    }

    private static String opTextTrimmed(ObjectNode op, String field) {
        JsonNode v = op.get(field);
        if (v == null || v.isNull()) {
            return "";
        }
        if (v.isTextual()) {
            return v.asText().trim();
        }
        if (v.isNumber() || v.isBoolean()) {
            return v.asText();
        }
        return v.toString();
    }

    private static JsonNode parseArgs(String argumentsJson) throws JsonProcessingException {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return JSON.createObjectNode();
        }
        return JSON.readTree(argumentsJson);
    }

    private static OptionalInt parseRequestNodeId(JsonNode args) {
        if (args == null) {
            return OptionalInt.empty();
        }
        JsonNode n = argFirst(args, "request_node_id", "requestNodeId");
        if (n == null || n.isNull() || !n.isNumber()) {
            return OptionalInt.empty();
        }
        int v = n.intValue();
        if (v < 1) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(v);
    }

    private static int jsonToInt(JsonNode n) {
        if (n == null || n.isNull()) {
            return 0;
        }
        if (n.isIntegralNumber()) {
            long v = n.longValue();
            if (v > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            if (v < Integer.MIN_VALUE) {
                return Integer.MIN_VALUE;
            }
            return (int) v;
        }
        if (n.isFloatingPointNumber()) {
            return (int) n.doubleValue();
        }
        if (n.isTextual()) {
            try {
                String s = n.asText().trim();
                if (s.isEmpty()) {
                    return 0;
                }
                return new java.math.BigDecimal(s).intValue();
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }

    /** First defined non-null property among {@code keys} (root object only). */
    private static JsonNode argFirst(JsonNode args, String... keys) {
        if (args == null || !args.isObject()) {
            return null;
        }
        for (String k : keys) {
            if (args.has(k) && !args.get(k).isNull()) {
                return args.get(k);
            }
        }
        return null;
    }
}
