package treepeater.ai;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.swing.SwingUtilities;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import treepeater.Utilities;

/**
 * Built-in tools: HTTP target summary, raw wire read ({@value #READ_HTTP_MESSAGE}), regex search
 * ({@value #SEARCH_HTTP_MESSAGE}), structured request edits ({@value #APPLY_HTTP_REQUEST_SEMANTIC_CHANGES}), other body
 * helpers, and send in Repeater.
 */
public final class HttpTargetTools {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger TIMING = Logger.getLogger("treepeater.ai.HttpTargetTools.commitLiveRequest");

    /** Tool name: current editor/target only; JSON from {@link HttpTargetSnapshot#toJson()} plus nested {@code history}. */
    public static final String GET_CURRENT_HTTP_TARGET = "get_current_http_target";

    /**
     * Byte slice of the raw request or response wire (start-line + headers + CRLFCRLF + body). Same side parameter as
     * {@value #SEARCH_HTTP_MESSAGE}.
     */
    public static final String READ_HTTP_MESSAGE = "read_http_message";

    /** Regex over raw wire bytes (Java {@link java.util.regex.Pattern}); byte offsets match {@link #READ_HTTP_MESSAGE}. */
    public static final String SEARCH_HTTP_MESSAGE = "search_http_message";

    /** Substring replace in the current (live editor) request body only. */
    public static final String REPLACE_IN_HTTP_REQUEST_BODY = "replace_in_http_request_body";

    /** Replace a 1-based inclusive line range in the current request body (UTF-8 text). */
    public static final String PATCH_HTTP_REQUEST_BODY_LINES = "patch_http_request_body_lines";

    /** Replace the entire current request body. */
    public static final String SET_HTTP_REQUEST_BODY = "set_http_request_body";

    /** Send the current repeater request and wait for the response; tool result contains only HTTP status_code. */
    public static final String SEND_CURRENT_HTTP_REQUEST = "send_current_http_request";

    /** Paginated list or search of open repeater tabs (live method/URL and title). */
    public static final String SEARCH_TABS = "search_tabs";

    /**
     * Batch semantic mutations on the current request (headers, cookies, JSON Pointer, XPath, method, URL). Use
     * {@code action} {@code set} vs {@code remove}; literal JSON null in the body uses {@code set} with {@code value}
     * null.
     */
    public static final String APPLY_HTTP_REQUEST_SEMANTIC_CHANGES = "apply_http_request_semantic_changes";

    /**
     * Transcript line for a tool: short {@code title} plus optional {@code detail} (what will change, key arguments).
     * {@code detail} is empty for read-only tools and for {@link #SET_HTTP_REQUEST_BODY} to avoid duplicating a large
     * body in the chat.
     */
    public static record HumanToolUsage(String title, String detail) {
        public HumanToolUsage {
            title = title != null ? title : "";
            detail = detail != null ? detail : "";
        }
    }

    private static final int DEFAULT_READ_CHUNK_BYTES = 4_096;
    private static final int MAX_BODY_CHUNK_BYTES = 65_536;

    private static final int DEFAULT_SEARCH_MAX_MATCHES = 10;
    private static final int MAX_SEARCH_MAX_MATCHES = 100;
    private static final int DEFAULT_SEARCH_CONTEXT_BYTES = 64;
    private static final int MAX_SEARCH_CONTEXT_BYTES = 512;
    private static final int MAX_SEARCH_PATTERN_CHARS = 1_024;
    private static final int MAX_SCAN_BYTES = 1_048_576;

    /**
     * Upper bound on the JSON string returned to the model for any single tool call. Sized to
     * comfortably accommodate one {@link #MAX_BODY_CHUNK_BYTES}-sized body chunk after base64
     * inflation (~88k chars) plus JSON overhead. When a tool returns more, the payload is replaced
     * with a small error object pointing the model at the paginated alternatives.
     */
    private static final int MAX_TOOL_RESULT_CHARS = 96_000;

    private static final int DEFAULT_TAB_PAGE_SIZE = 10;
    private static final int MAX_TAB_PAGE_SIZE = 50;

    /** Max URL characters per row in {@link #SEARCH_TABS} results before truncation. */
    public static final int MAX_TAB_LIST_URL_CHARS = 512;

    private static final String REQ_NODE_ID_PROP =
            "\"request_node_id\":{\"type\":\"integer\",\"minimum\":1,\"description\":\"Open repeater tab id from search_tabs rows; omit to use the UI-selected tab.\"}";

    private static final String OPTIONAL_TAB_PARAMS_SCHEMA =
            "{\"type\":\"object\",\"properties\":{" + REQ_NODE_ID_PROP + "},\"additionalProperties\":false}";

    private static final String READ_MESSAGE_SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
                    + REQ_NODE_ID_PROP
                    + ",\"side\":{\"type\":\"string\",\"enum\":[\"request\",\"response\"],\"description\":\"Whether to read the stored request or response.\"},\"history_index\":{\"type\":\"integer\",\"minimum\":0,\"description\":\"0-based history index; omit for the current entry.\"},\"offset\":{\"type\":\"integer\",\"minimum\":0,\"default\":0,\"description\":\"Byte offset into the raw wire message.\"},\"max_bytes\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":65536,\"default\":4096,\"description\":\"Maximum bytes to return in this call.\"}},\"required\":[\"side\"],\"additionalProperties\":false}";

    private static final String SEARCH_MESSAGE_SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
                    + REQ_NODE_ID_PROP
                    + ",\"side\":{\"type\":\"string\",\"enum\":[\"request\",\"response\"]},\"pattern\":{\"type\":\"string\",\"description\":\"Java java.util.regex pattern; use inline flags (?i), (?m), (?s) as needed.\"},\"history_index\":{\"type\":\"integer\",\"minimum\":0,\"description\":\"0-based history index; omit for the current entry.\"},\"scope\":{\"type\":\"string\",\"enum\":[\"headers\",\"body\",\"all\"],\"default\":\"all\",\"description\":\"headers: only before CRLFCRLF; body: only after; all: full message.\"},\"max_matches\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":100,\"default\":10,\"description\":\"Maximum matches to return.\"},\"context_bytes\":{\"type\":\"integer\",\"minimum\":0,\"maximum\":512,\"default\":64,\"description\":\"Context bytes on each side of each match.\"}},\"required\":[\"side\",\"pattern\"],\"additionalProperties\":false}";

    private static final String SEARCH_TABS_SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"offset\":{\"type\":\"integer\",\"minimum\":0,\"default\":0,\"description\":\"Index into the filtered tab list.\"},\"page_size\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":"
                    + MAX_TAB_PAGE_SIZE
                    + ",\"default\":"
                    + DEFAULT_TAB_PAGE_SIZE
                    + ",\"description\":\"Tabs per page (capped).\"},\"query\":{\"type\":\"string\",\"description\":\"Optional filter: match live request method and URL (e.g. POST /path or full https URL) or tab title substring; case-insensitive.\"}},\"additionalProperties\":false}";

    private static final int MAX_SUBSTRING_REPLACEMENTS = 100_000;

    private static final String REPLACE_BODY_SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
                    + REQ_NODE_ID_PROP
                    + ",\"old_text\":{\"type\":\"string\",\"description\":\"Literal text to find (non-empty).\"},\"new_text\":{\"type\":\"string\",\"description\":\"Replacement text (may be empty to delete matches).\"},\"max_replacements\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":%d,\"default\":1,\"description\":\"Maximum non-overlapping replacements (left to right). Ignored when replace_all is true.\"},\"replace_all\":{\"type\":\"boolean\",\"default\":false,\"description\":\"If true, replace every occurrence; max_replacements is ignored.\"}},\"required\":[\"old_text\",\"new_text\"],\"additionalProperties\":false}"
                    .formatted(MAX_SUBSTRING_REPLACEMENTS);

    private static final String PATCH_LINES_SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
                    + REQ_NODE_ID_PROP
                    + ",\"start_line\":{\"type\":\"integer\",\"minimum\":1,\"description\":\"First line to replace (1-based, inclusive).\"},\"end_line\":{\"type\":\"integer\",\"minimum\":1,\"description\":\"Last line to replace (1-based, inclusive).\"},\"content\":{\"type\":\"string\",\"description\":\"New text for that range; line breaks may be \\\\n or any Unicode line ending (split with Java \\\\R).\"}},\"required\":[\"start_line\",\"end_line\",\"content\"],\"additionalProperties\":false}";

    private static final String SET_BODY_SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
                    + REQ_NODE_ID_PROP
                    + ",\"body_utf8\":{\"type\":\"string\",\"description\":\"Full new body as UTF-8 text.\"},\"body_base64\":{\"type\":\"string\",\"description\":\"Full new body as standard Base64 (mutually exclusive with body_utf8).\"}},\"additionalProperties\":false}";

    private static final int MAX_SEMANTIC_OPERATIONS = 32;

    /**
     * Minimal valid payload example (also returned in structured errors when {@code operations} is missing or invalid).
     */
    public static final String APPLY_HTTP_REQUEST_SEMANTIC_CHANGES_EXAMPLE_ARGS =
            "{\"operations\":[{\"type\":\"header\",\"action\":\"set\",\"key\":\"X-Test\",\"value\":\"1\"}]}";

    private static final String SEMANTIC_ITEMS_ALLOF =
            "["
                    + "{\"if\":{\"properties\":{\"type\":{\"const\":\"header\"},\"action\":{\"const\":\"set\"}},\"required\":[\"type\",\"action\"]},\"then\":{\"required\":[\"key\",\"value\"]}}"
                    + ",{\"if\":{\"properties\":{\"type\":{\"const\":\"header\"},\"action\":{\"const\":\"remove\"}},\"required\":[\"type\",\"action\"]},\"then\":{\"required\":[\"key\"]}}"
                    + ",{\"if\":{\"properties\":{\"type\":{\"const\":\"cookie\"},\"action\":{\"const\":\"set\"}},\"required\":[\"type\",\"action\"]},\"then\":{\"required\":[\"key\",\"value\"]}}"
                    + ",{\"if\":{\"properties\":{\"type\":{\"const\":\"cookie\"},\"action\":{\"const\":\"remove\"}},\"required\":[\"type\",\"action\"]},\"then\":{\"required\":[\"key\"]}}"
                    + ",{\"if\":{\"properties\":{\"type\":{\"const\":\"json\"},\"action\":{\"const\":\"set\"}},\"required\":[\"type\",\"action\"]},\"then\":{\"required\":[\"path\",\"value\"]}}"
                    + ",{\"if\":{\"properties\":{\"type\":{\"const\":\"json\"},\"action\":{\"const\":\"remove\"}},\"required\":[\"type\",\"action\"]},\"then\":{\"required\":[\"path\"]}}"
                    + ",{\"if\":{\"properties\":{\"type\":{\"const\":\"xml\"},\"action\":{\"const\":\"set\"}},\"required\":[\"type\",\"action\"]},\"then\":{\"required\":[\"path\",\"value\"]}}"
                    + ",{\"if\":{\"properties\":{\"type\":{\"const\":\"xml\"},\"action\":{\"const\":\"remove\"}},\"required\":[\"type\",\"action\"]},\"then\":{\"required\":[\"path\"]}}"
                    + ",{\"if\":{\"properties\":{\"type\":{\"const\":\"method\"},\"action\":{\"const\":\"set\"}},\"required\":[\"type\",\"action\"]},\"then\":{\"required\":[\"value\"],\"properties\":{\"value\":{\"type\":\"string\"}}}}"
                    + ",{\"if\":{\"properties\":{\"type\":{\"const\":\"url\"},\"action\":{\"const\":\"set\"}},\"required\":[\"type\",\"action\"]},\"then\":{\"required\":[\"value\"],\"properties\":{\"value\":{\"type\":\"string\"}}}}"
                    + "]";

    private static final String SEMANTIC_OPERATION_ITEM_SCHEMA =
            "{\"type\":\"object\",\"required\":[\"type\",\"action\"],"
                    + "\"properties\":{"
                    + "\"type\":{\"type\":\"string\",\"enum\":[\"header\",\"cookie\",\"json\",\"xml\",\"method\",\"url\"]},"
                    + "\"action\":{\"type\":\"string\",\"enum\":[\"set\",\"remove\"]},"
                    + "\"key\":{\"type\":\"string\",\"description\":\"Header/cookie name; must be empty for method/url when set.\"},"
                    + "\"path\":{\"type\":\"string\",\"description\":\"JSON Pointer (type json) or XPath 1.0 (type xml).\"},"
                    + "\"value\":{}"
                    + "},"
                    + "\"allOf\":"
                    + SEMANTIC_ITEMS_ALLOF
                    + ",\"additionalProperties\":false}";

    private static final String APPLY_SEMANTIC_CHANGES_SCHEMA =
            "{\"type\":\"object\",\"required\":[\"operations\"],\"properties\":{"
                    + REQ_NODE_ID_PROP
                    + ",\"operations\":{\"type\":\"array\",\"minItems\":1,\"maxItems\":"
                    + MAX_SEMANTIC_OPERATIONS
                    + ",\"items\":"
                    + SEMANTIC_OPERATION_ITEM_SCHEMA
                    + "}},\"additionalProperties\":false}";

    private HttpTargetTools() {}

    public static List<ChatToolDefinition> definitions() {
        return List.of(
                new ChatToolDefinition(
                        GET_CURRENT_HTTP_TARGET,
                        "Returns the current repeater HTTP target (what is configured for this tab right now): "
                                + "scheme, host, port, SNI flag, method, full URL, and path, plus a send-history object "
                                + "(current index, prev/next, entries with index/time/target label). "
                                + "Optional `request_node_id` selects an open tab (from search_tabs); omit for the UI-selected tab. "
                                + "Use read_http_message or search_http_message to inspect the raw request/response for a past send.",
                        OPTIONAL_TAB_PARAMS_SCHEMA),
                new ChatToolDefinition(
                        SEARCH_TABS,
                        "Lists or searches open repeater tabs with pagination. Omit or blank `query` for all tabs (UI order). "
                                + "With `query`, each tab matches if the live request method+URL matches (e.g. `POST /api/foo` or a "
                                + "full https URL substring) **or** the tab title contains the query (case-insensitive). "
                                + "Use returned `request_node_id` on other HTTP tools. Defaults: offset 0, page_size "
                                + DEFAULT_TAB_PAGE_SIZE + " (max " + MAX_TAB_PAGE_SIZE + ").",
                        SEARCH_TABS_SCHEMA),
                new ChatToolDefinition(
                        READ_HTTP_MESSAGE,
                        "Reads a byte range of the raw HTTP request or response for one history entry (start-line + "
                                + "headers + CRLFCRLF + body, as serialized by the proxy). `side` selects request or response. "
                                + "Defaults return the first 4096 bytes, usually enough for the start-line and all headers. "
                                + "Returns `total_bytes`, `header_bytes` (first body byte; use it as `offset` on a follow-up "
                                + "read), `has_more`, `next_offset`, and `text` (utf-8) or `base64` on binary slices. For "
                                + "targeted values (a header, token in a body) prefer `search_http_message` to keep results small.",
                        READ_MESSAGE_SCHEMA),
                new ChatToolDefinition(
                        SEARCH_HTTP_MESSAGE,
                        "Regex search over the raw wire bytes of a request or response (same byte address space as "
                                + "read_http_message). Returns up to `max_matches` matches (default 10, cap 100) with "
                                + "absolute byte offsets, capture groups, and short `context_bytes` (default 64, cap 512) "
                                + "on each side. `scope` restricts to headers, body, or the full message. Use Java regex "
                                + "with inline flags: `(?i)` case-insensitive, `(?m)` ^ and $ per line, `(?s)` dot includes "
                                + "newlines. Examples: `(?im)^Set-Cookie:\\\\s*(.+)$`, `name=\\\"csrf_token\\\"\\\\s+value=\\\""
                                + "([^\\\"]+)\\\"`. Matching uses Latin-1 (byte offset equals char index); stick to ASCII "
                                + "or byte patterns. Pattern length max 1024; the scan is limited to 1MB per call "
                                + "(`scan_limited_bytes` when clipped); page with read_http_message past that.",
                        SEARCH_MESSAGE_SCHEMA),
                new ChatToolDefinition(
                        REPLACE_IN_HTTP_REQUEST_BODY,
                        "Replaces literal text in the **current** repeater request body only (live editor). "
                                + "Body must be valid UTF-8; if not, use set_http_request_body with body_base64. "
                                + "When max_replacements is 1 (default), old_text must match exactly once. "
                                + "Use replace_all to change every occurrence.",
                        REPLACE_BODY_SCHEMA),
                new ChatToolDefinition(
                        PATCH_HTTP_REQUEST_BODY_LINES,
                        "Replaces a 1-based inclusive range of **lines** in the **current** request body (UTF-8 text). "
                                + "Lines are split with Unicode line breaks (Java \\R). Stored body is joined with \\n. "
                                + "For non-text bodies use set_http_request_body.",
                        PATCH_LINES_SCHEMA),
                new ChatToolDefinition(
                        SET_HTTP_REQUEST_BODY,
                        "Sets the **entire** body of the **current** repeater request. Provide either body_utf8 or "
                                + "body_base64 (not both). bodyUtf8 / bodyBase64 are accepted as aliases. "
                                + "body_utf8 may be a JSON string or a JSON object/array (serialized to compact JSON). "
                                + "Use base64 for arbitrary bytes.",
                        SET_BODY_SCHEMA),
                new ChatToolDefinition(
                        APPLY_HTTP_REQUEST_SEMANTIC_CHANGES,
                        "Applies a batch of typed changes to the **current** repeater request in one call. **Required:** "
                                + "top-level `operations` (non-empty array). Each element has `type` (header|cookie|json|"
                                + "xml|method|url) and `action` (set|remove), plus `key` and/or `path` and/or `value` as "
                                + "required by that combination (see JSON Schema). Use `action: remove` to delete; use "
                                + "`action: set` with `value: null` to store a **literal JSON null** in the body (json "
                                + "type). `path` is a JSON Pointer (RFC 6901) for type json, or XPath 1.0 for type xml. "
                                + "For method and url with `set`, `key` must be empty. Omit the `value` field entirely "
                                + "on `remove` (when present, the call is rejected). If the body is not valid JSON or XML "
                                + "for that operation, the error includes `op_index` and a hint to use read_http_message "
                                + "or set_http_request_body. Minimal example: "
                                + APPLY_HTTP_REQUEST_SEMANTIC_CHANGES_EXAMPLE_ARGS,
                        APPLY_SEMANTIC_CHANGES_SCHEMA),
                new ChatToolDefinition(
                        SEND_CURRENT_HTTP_REQUEST,
                        "Sends the **current** repeater request (live editor, with target applied) and waits until the "
                                + "response is received. Updates the response pane and send history like the Send button. "
                                + "Returns only the HTTP status_code in the tool result (no body or headers). "
                                + "Optional `request_node_id` selects which open tab to send from.",
                        OPTIONAL_TAB_PARAMS_SCHEMA));
    }

    /**
     * Classifies a built-in tool by sensitivity. Unknown names return {@code null} (treat as requiring approval).
     */
    public static ToolActionLevel toolActionLevel(String toolName) {
        if (toolName == null) {
            return null;
        }
        return switch (toolName) {
            case GET_CURRENT_HTTP_TARGET, READ_HTTP_MESSAGE, SEARCH_HTTP_MESSAGE, SEARCH_TABS -> ToolActionLevel.READ_ONLY;
            case REPLACE_IN_HTTP_REQUEST_BODY,
                    PATCH_HTTP_REQUEST_BODY_LINES,
                    SET_HTTP_REQUEST_BODY,
                    APPLY_HTTP_REQUEST_SEMANTIC_CHANGES -> ToolActionLevel.WRITE;
            case SEND_CURRENT_HTTP_REQUEST -> ToolActionLevel.EXECUTE;
            default -> null;
        };
    }

    /**
     * Whether the chat must collect approval before running this tool. Modes only tighten or relax approval; they do
     * not block tools outright. Unknown tool names require approval except in {@link AgentMode#AUTONOMOUS}.
     * <ul>
     *   <li>{@link AgentMode#ASK}: approval for {@link ToolActionLevel#WRITE} and {@link ToolActionLevel#EXECUTE};
     *       read-only tools run without prompting.</li>
     *   <li>{@link AgentMode#HELPER}: approval only for {@link ToolActionLevel#EXECUTE}.</li>
     *   <li>{@link AgentMode#AUTONOMOUS}: no approval.</li>
     * </ul>
     */
    public static boolean requiresUserApprovalInAgentMode(String toolName, AgentMode mode) {
        if (mode == null) {
            mode = AgentMode.ASK;
        }
        ToolActionLevel level = toolActionLevel(toolName);
        if (level == null) {
            return mode != AgentMode.AUTONOMOUS;
        }
        return switch (mode) {
            case ASK -> level != ToolActionLevel.READ_ONLY;
            case HELPER -> level == ToolActionLevel.EXECUTE;
            case AUTONOMOUS -> false;
        };
    }

    /**
     * Dispatches built-in tools; resolves {@link AgentToolContext} per optional {@code request_node_id} on the bridge.
     */
    public static String execute(String toolName, String argumentsJson, RepeaterTabAgentBridge bridge) {
        if (bridge == null) {
            return errorJson("no bridge");
        }
        JsonNode args;
        try {
            args = parseArgs(argumentsJson);
        } catch (Exception e) {
            return errorJson("invalid tool arguments JSON");
        }
        if (SEARCH_TABS.equals(toolName)) {
            try {
                return capResult(searchTabs(bridge, args));
            } catch (Exception e) {
                return errorJson(e.getMessage() != null ? e.getMessage() : "tool error");
            }
        }
        OptionalInt nodeId = parseRequestNodeId(args);
        AgentToolContext ctx = bridge.contextForAgent(nodeId);
        if (ctx == null) {
            return errorJson("no target context");
        }
        String result;
        try {
            result = switch (toolName) {
                case GET_CURRENT_HTTP_TARGET -> targetWithHistoryJson(ctx);
                case READ_HTTP_MESSAGE -> readMessage(ctx, args);
                case SEARCH_HTTP_MESSAGE -> searchMessage(ctx, args);
                case REPLACE_IN_HTTP_REQUEST_BODY -> replaceInHttpRequestBody(ctx, args);
                case PATCH_HTTP_REQUEST_BODY_LINES -> patchHttpRequestBodyLines(ctx, args);
                case SET_HTTP_REQUEST_BODY -> setHttpRequestBody(ctx, args);
                case APPLY_HTTP_REQUEST_SEMANTIC_CHANGES -> applyHttpRequestSemanticChanges(ctx, args);
                case SEND_CURRENT_HTTP_REQUEST -> sendCurrentHttpRequest(ctx);
                default -> "{\"error\":\"unknown tool: " + escapeJson(toolName) + "\"}";
            };
        } catch (Exception e) {
            return errorJson(e.getMessage() != null ? e.getMessage() : "tool error");
        }
        return capResult(result);
    }

    /**
     * Same as {@link #execute(String, String, RepeaterTabAgentBridge)} with a fixed context (tests; {@link #SEARCH_TABS} unsupported).
     */
    public static String execute(String toolName, String argumentsJson, AgentToolContext ctx) {
        return execute(toolName, argumentsJson, RepeaterTabAgentBridge.singleTab(ctx));
    }

    /**
     * History index for tool transcript labels when the tool targets a specific tab via {@code request_node_id}.
     */
    public static int viewerHistoryIndexForToolCard(String toolName, String argumentsJson, RepeaterTabAgentBridge bridge) {
        if (bridge == null || SEARCH_TABS.equals(toolName)) {
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

    /** UI-selected tab id for {@link #humanToolUsage(String, String, int, int)}; {@link Integer#MIN_VALUE} if unknown. */
    public static int uiSelectedRequestNodeIdForToolCard(RepeaterTabAgentBridge bridge) {
        return bridge != null ? bridge.uiSelectedRequestNodeIdForToolCard() : Integer.MIN_VALUE;
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

    private static String searchTabs(RepeaterTabAgentBridge bridge, JsonNode args) {
        int offset = 0;
        JsonNode offN = argFirst(args, "offset");
        if (offN != null && offN.isNumber()) {
            offset = offN.intValue();
        }
        if (offset < 0) {
            offset = 0;
        }
        int pageSize = DEFAULT_TAB_PAGE_SIZE;
        JsonNode psN = argFirst(args, "page_size", "pageSize");
        if (psN != null && psN.isNumber()) {
            pageSize = psN.intValue();
        }
        if (pageSize < 1) {
            pageSize = DEFAULT_TAB_PAGE_SIZE;
        }
        pageSize = Math.min(pageSize, MAX_TAB_PAGE_SIZE);
        String query = argTextAny(args, "query", "q", "search");
        if (query.isEmpty()) {
            query = null;
        }
        return bridge.searchTabs(offset, pageSize, query);
    }

    /** JSON body for {@link RepeaterTabAgentBridge#searchTabs(int, int, String)}. */
    public static String formatSearchTabsResponse(
            int total, int offset, int pageSize, boolean hasMore, List<SearchTabRow> rows) {
        ObjectNode root = JSON.createObjectNode();
        root.put("total", total);
        root.put("offset", offset);
        root.put("page_size", pageSize);
        root.put("has_more", hasMore);
        if (hasMore) {
            root.put("next_offset", offset + rows.size());
        }
        ArrayNode arr = root.putArray("tabs");
        for (SearchTabRow r : rows) {
            arr.add(searchTabRowToObject(r));
        }
        return write(root);
    }

    private static ObjectNode searchTabRowToObject(SearchTabRow r) {
        ObjectNode o = JSON.createObjectNode();
        o.put("request_node_id", r.requestNodeId());
        o.put("title", r.title() != null ? r.title() : "");
        o.put("selected", r.selected());
        o.put("method", r.method() != null ? r.method() : "");
        o.put("url", r.url() != null ? r.url() : "");
        o.put("url_truncated", r.urlTruncated());
        return o;
    }

    /**
     * Returns an oversized tool result unchanged when within budget; otherwise replaces it with a
     * small structured error pointing the model at paginated alternatives. This backstops every tool
     * uniformly so an unexpectedly large payload can never explode the next round's input-token
     * count. A max-sized read_http_message chunk is sized to stay under this cap.
     */
    private static String capResult(String result) {
        if (result == null) {
            return errorJson("tool returned null");
        }
        if (result.length() <= MAX_TOOL_RESULT_CHARS) {
            return result;
        }
        ObjectNode n = JSON.createObjectNode();
        n.put("error", "tool_result_too_large");
        n.put("result_chars", result.length());
        n.put("max_result_chars", MAX_TOOL_RESULT_CHARS);
        n.put(
                "hint",
                "Call read_http_message with offset and max_bytes for smaller slices, or use search_http_message to "
                        + "match a small substring; narrow search scope (headers|body) when possible.");
        return write(n);
    }

    private static JsonNode parseArgs(String argumentsJson) throws JsonProcessingException {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return JSON.createObjectNode();
        }
        return JSON.readTree(argumentsJson);
    }

    /** Nested under {@code history} in {@link #get_current_http_target}. */
    private static ObjectNode buildHistoryStateObject(AgentToolContext ctx) {
        ObjectNode n = JSON.createObjectNode();
        int size = ctx.historySize();
        int cur = ctx.currentHistoryIndex();
        n.put("current_history_index", cur);
        n.put("entry_count", size);
        n.put("has_previous_history", size > 0 && cur > 0);
        n.put("has_next_history", size > 0 && cur >= 0 && cur < size - 1);
        ArrayNode arr = n.putArray("entries");
        for (AgentToolContext.HistoryEntryInfo e : ctx.historyEntries()) {
            ObjectNode row = arr.addObject();
            row.put("index", e.index());
            row.put("time", e.time() != null ? e.time() : "");
            row.put("target_label", e.targetLabel() != null ? e.targetLabel() : "");
        }
        return n;
    }

    private static String targetWithHistoryJson(AgentToolContext ctx) {
        try {
            ObjectNode n = (ObjectNode) JSON.readTree(ctx.target().toJson());
            n.put("request_node_id", ctx.requestNodeId());
            n.set("history", buildHistoryStateObject(ctx));
            return write(n);
        } catch (Exception e) {
            return ctx.target().toJson();
        }
    }

    private static String readMessage(AgentToolContext ctx, JsonNode args) {
        String side = resolveSide(args);
        int idx = resolveHistoryIndexOptional(ctx, args);
        int offset = readOffsetArg(args);
        int maxBytes = readMaxBytesForReadMessage(args);
        byte[] full = rawWireBytes(ctx, idx, side);
        int headerEnd = firstBodyByteIndex(full);
        int total = full.length;
        if (offset > total) {
            offset = total;
        }
        int len = Math.min(maxBytes, total - offset);
        byte[] chunk = len <= 0 ? new byte[0] : java.util.Arrays.copyOfRange(full, offset, offset + len);

        ObjectNode out = JSON.createObjectNode();
        out.put("history_index", idx);
        out.put("side", side);
        out.put("total_bytes", total);
        out.put("header_bytes", headerEnd);
        out.put("offset", offset);
        out.put("returned_bytes", chunk.length);
        out.put("has_more", offset + chunk.length < total);
        out.put("next_offset", offset + chunk.length);
        String utf8 = Utilities.decodeUtf8Strict(chunk);
        if (utf8 != null) {
            out.put("encoding", "utf-8");
            out.put("text", utf8);
        } else {
            out.put("encoding", "base64");
            out.put("is_binary_chunk", true);
            out.put("base64", Base64.getEncoder().encodeToString(chunk));
        }
        return write(out);
    }

    private static String searchMessage(AgentToolContext ctx, JsonNode args) {
        JsonNode patNode = argFirst(args, "pattern", "regex", "re");
        if (patNode == null || patNode.isNull()) {
            return errorJson("missing pattern");
        }
        String patternStr = patNode.isTextual() ? patNode.asText() : patNode.toString();
        if (patternStr.length() > MAX_SEARCH_PATTERN_CHARS) {
            return errorJson("pattern too long (max " + MAX_SEARCH_PATTERN_CHARS + " characters)");
        }
        final Pattern pattern;
        try {
            pattern = Pattern.compile(patternStr);
        } catch (PatternSyntaxException e) {
            return errorJson("invalid regex: " + e.getMessage());
        }
        String side = resolveSide(args);
        int idx = resolveHistoryIndexOptional(ctx, args);
        String scope = resolveSearchScope(args);
        int maxMatches = readMaxMatchesArg(args);
        int contextBytes = readContextBytesArg(args);
        byte[] full = rawWireBytes(ctx, idx, side);
        int total = full.length;
        int headerEnd = firstBodyByteIndex(full);
        int[] region = resolveSearchRegion(scope, total, headerEnd);
        int rStart = region[0];
        int rEnd = region[1];
        if (rStart >= rEnd) {
            ObjectNode out = baseSearchResultObject(idx, side, total, headerEnd, scope, patternStr);
            out.set("matches", JSON.createArrayNode());
            out.put("match_count", 0);
            out.put("truncated", false);
            ArrayNode emptyRange = out.putArray("scanned_range");
            emptyRange.add(rStart);
            emptyRange.add(rStart);
            return write(out);
        }
        int regionLen = rEnd - rStart;
        boolean limited = false;
        int scanEnd = rEnd;
        if (regionLen > MAX_SCAN_BYTES) {
            scanEnd = rStart + MAX_SCAN_BYTES;
            limited = true;
        }
        int scanLen = scanEnd - rStart;
        String searchSpace =
                new String(java.util.Arrays.copyOfRange(full, rStart, rStart + scanLen), StandardCharsets.ISO_8859_1);
        Matcher counter = pattern.matcher(searchSpace);
        int totalInScan = 0;
        while (counter.find()) {
            totalInScan++;
        }
        Matcher m = pattern.matcher(searchSpace);
        ArrayNode arr = JSON.createArrayNode();
        int collected = 0;
        while (m.find() && collected < maxMatches) {
            int absStart = rStart + m.start();
            int absEnd = rStart + m.end();
            ObjectNode one = arr.addObject();
            one.put("start", absStart);
            one.put("end", absEnd);
            addSliceFields(one, "match", "match_base64", full, absStart, absEnd);
            if (m.groupCount() > 0) {
                ArrayNode groups = one.putArray("groups");
                for (int g = 1; g <= m.groupCount(); g++) {
                    if (m.group(g) == null) {
                        groups.addNull();
                    } else {
                        int gStart = rStart + m.start(g);
                        int gEnd = rStart + m.end(g);
                        byte[] gSlice = java.util.Arrays.copyOfRange(full, gStart, gEnd);
                        String gUtf = Utilities.decodeUtf8Strict(gSlice);
                        if (gUtf != null) {
                            groups.add(gUtf);
                        } else {
                            groups.add(Base64.getEncoder().encodeToString(gSlice));
                        }
                    }
                }
            }
            int cBefore = Math.max(0, absStart - contextBytes);
            int cAfter = Math.min(total, absEnd + contextBytes);
            addSliceFields(one, "context_before", "context_before_base64", full, cBefore, absStart);
            addSliceFields(one, "context_after", "context_after_base64", full, absEnd, cAfter);
            collected++;
        }
        ObjectNode out = baseSearchResultObject(idx, side, total, headerEnd, scope, patternStr);
        ArrayNode range = out.putArray("scanned_range");
        range.add(rStart);
        range.add(scanEnd);
        if (limited) {
            out.put("scan_limited_bytes", MAX_SCAN_BYTES);
        }
        out.set("matches", arr);
        out.put("match_count", arr.size());
        if (totalInScan > maxMatches) {
            out.put("truncated", true);
            out.put("total_matches_in_scan", totalInScan);
        } else {
            out.put("truncated", false);
        }
        return write(out);
    }

    private static ObjectNode baseSearchResultObject(
            int idx, String side, int total, int headerEnd, String scope, String patternStr) {
        ObjectNode out = JSON.createObjectNode();
        out.put("history_index", idx);
        out.put("side", side);
        out.put("total_bytes", total);
        out.put("header_bytes", headerEnd);
        out.put("scope", scope);
        out.put("pattern", patternStr);
        return out;
    }

    private static void addSliceFields(
            ObjectNode n, String utf8Key, String b64Key, byte[] data, int start, int end) {
        if (start < 0 || end < start || end > data.length) {
            return;
        }
        int len = end - start;
        if (len == 0) {
            n.put(utf8Key, "");
            return;
        }
        byte[] slice = java.util.Arrays.copyOfRange(data, start, end);
        String utf8 = Utilities.decodeUtf8Strict(slice);
        if (utf8 != null) {
            n.put(utf8Key, utf8);
        } else {
            n.put(b64Key, Base64.getEncoder().encodeToString(slice));
        }
    }

    private static int[] resolveSearchRegion(String scope, int total, int headerEnd) {
        return switch (scope) {
            case "headers" -> new int[] {0, headerEnd};
            case "body" -> new int[] {headerEnd, total};
            default -> new int[] {0, total};
        };
    }

    private static String resolveSearchScope(JsonNode args) {
        JsonNode v = argFirst(args, "scope", "where");
        if (v == null) {
            return "all";
        }
        if (!v.isTextual()) {
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

    private static int readMaxMatchesArg(JsonNode args) {
        JsonNode v = argFirst(args, "max_matches", "maxMatches", "limit");
        if (v == null) {
            return DEFAULT_SEARCH_MAX_MATCHES;
        }
        return Math.min(MAX_SEARCH_MAX_MATCHES, Math.max(1, jsonToInt(v)));
    }

    private static int readContextBytesArg(JsonNode args) {
        JsonNode v = argFirst(args, "context_bytes", "contextBytes", "context");
        if (v == null) {
            return DEFAULT_SEARCH_CONTEXT_BYTES;
        }
        return Math.min(MAX_SEARCH_CONTEXT_BYTES, Math.max(0, jsonToInt(v)));
    }

    private static int readMaxBytesForReadMessage(JsonNode args) {
        JsonNode v = argFirst(args, "max_bytes", "maxBytes", "limit", "chunk_size", "chunkSize");
        if (v == null) {
            return DEFAULT_READ_CHUNK_BYTES;
        }
        return Math.min(MAX_BODY_CHUNK_BYTES, Math.max(1, jsonToInt(v)));
    }

    /**
     * First byte of the message body, i.e. index after the first {@code \r\n\r\n}. If missing, the whole range is
     * treated as headers (e.g. malformed message).
     */
    private static int firstBodyByteIndex(byte[] data) {
        for (int i = 0; i + 3 < data.length; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n' && data[i + 2] == '\r' && data[i + 3] == '\n') {
                return i + 4;
            }
        }
        return data.length;
    }

    private static byte[] rawWireBytes(AgentToolContext ctx, int idx, String side) {
        if ("request".equals(side)) {
            HttpRequest r = ctx.requestForHistoryIndex().apply(idx);
            if (r == null) {
                throw new IllegalArgumentException("no request for this history index");
            }
            return bytesFromByteArray(() -> r.toByteArray());
        }
        HttpResponse res = ctx.responseForHistoryIndex().apply(idx);
        if (res == null) {
            throw new IllegalArgumentException("no response for this history index");
        }
        return bytesFromByteArray(() -> res.toByteArray());
    }

    private static int resolveHistoryIndexOptional(AgentToolContext ctx, JsonNode args) {
        JsonNode v = argFirst(args, "history_index", "historyIndex", "index", "entry_index", "entryIndex");
        if (v == null) {
            int cur = ctx.currentHistoryIndex();
            if (cur >= 0 && cur < ctx.historySize()) {
                return cur;
            }
            throw new IllegalArgumentException(
                    "missing history_index: use history_index or historyIndex in range 0.." + (ctx.historySize() - 1));
        }
        int idx = jsonToInt(v);
        if (idx < 0 || idx >= ctx.historySize()) {
            throw new IllegalArgumentException(
                    "history_index out of range (0.." + (ctx.historySize() - 1) + ")");
        }
        return idx;
    }

    private static HttpRequest requireCurrentRequest(AgentToolContext ctx) {
        int cur = ctx.currentHistoryIndex();
        if (cur < 0 || cur >= ctx.historySize()) {
            throw new IllegalArgumentException("no current history entry");
        }
        HttpRequest req = ctx.requestForHistoryIndex().apply(cur);
        if (req == null) {
            throw new IllegalArgumentException("no request for current entry");
        }
        return req;
    }

    private static void commitLiveRequest(AgentToolContext ctx, HttpRequest updated) {
        Consumer<HttpRequest> applier = ctx.applyLiveRequest();
        if (applier == null) {
            throw new IllegalArgumentException("request body updates unavailable");
        }
        long t0 = System.nanoTime();
        if (TIMING.isLoggable(Level.FINE)) {
            TIMING.fine("commitLiveRequest: begin invokeAndWait (worker thread)");
        }
        try {
            SwingUtilities.invokeAndWait(
                    () -> {
                        if (TIMING.isLoggable(Level.FINE)) {
                            long waitMs = (System.nanoTime() - t0) / 1_000_000L;
                            TIMING.fine(
                                    "commitLiveRequest: EDT runnable started after " + waitMs + "ms (queue wait)");
                        }
                        applier.accept(updated);
                    });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while applying request");
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) {
                throw re;
            }
            if (c instanceof Error err) {
                throw err;
            }
            throw new IllegalStateException(c != null ? c.getMessage() : "request update failed");
        }
        if (TIMING.isLoggable(Level.FINE)) {
            long totalMs = (System.nanoTime() - t0) / 1_000_000L;
            TIMING.fine("commitLiveRequest: invokeAndWait returned after " + totalMs + "ms total (incl. apply on EDT)");
        }
    }

    private static HttpRequest withBodyBytes(HttpRequest req, byte[] body) {
        return req.withBody(ByteArray.byteArray(body));
    }

    private record HttpBodyAndReplaceStats(
            HttpRequest request, int bytesBefore, int bytesAfter, int replacements) {}

    private static HttpBodyAndReplaceStats replaceInHttpRequestBodyOnRequest(HttpRequest req, JsonNode args) {
        String oldText = argTextAny(args, "old_text", "oldText");
        if (oldText.isEmpty()) {
            throw new IllegalArgumentException("old_text must be non-empty");
        }
        String newText = "";
        JsonNode newNode = argFirst(args, "new_text", "newText");
        if (newNode != null && !newNode.isNull()) {
            newText = newNode.asText();
        }

        boolean replaceAll = args.has("replace_all") && args.get("replace_all").asBoolean(false);
        int maxRep = 1;
        if (!replaceAll) {
            JsonNode m = argFirst(args, "max_replacements", "maxReplacements");
            if (m != null) {
                maxRep = jsonToInt(m);
            }
            if (maxRep < 1) {
                maxRep = 1;
            }
            if (maxRep > MAX_SUBSTRING_REPLACEMENTS) {
                throw new IllegalArgumentException("max_replacements too large");
            }
        }

        byte[] rawBytes = bytesFromByteArray(() -> req.body());
        int before = rawBytes.length;
        String text = Utilities.decodeUtf8Strict(rawBytes);
        if (text == null) {
            throw new IllegalArgumentException(
                    "request body is not valid UTF-8; use set_http_request_body with body_base64");
        }

        if (!replaceAll) {
            int occ = countNonOverlappingMatches(text, oldText);
            if (occ == 0) {
                throw new IllegalArgumentException("old_text not found");
            }
            if (maxRep == 1 && occ > 1) {
                throw new IllegalArgumentException(
                        "old_text is not unique; narrow the match, increase max_replacements, or use replace_all");
            }
        } else if (!text.contains(oldText)) {
            throw new IllegalArgumentException("old_text not found");
        }

        String replaced;
        int replCount;
        if (replaceAll) {
            replaced = text.replace(oldText, newText);
            replCount = countNonOverlappingMatches(text, oldText);
        } else {
            StringBuilder out = new StringBuilder();
            int from = 0;
            int reps = 0;
            while (reps < maxRep) {
                int idx = text.indexOf(oldText, from);
                if (idx < 0) {
                    break;
                }
                out.append(text, from, idx);
                out.append(newText);
                from = idx + oldText.length();
                reps++;
            }
            out.append(text.substring(from));
            replaced = out.toString();
            replCount = reps;
        }

        byte[] outBytes = replaced.getBytes(StandardCharsets.UTF_8);
        return new HttpBodyAndReplaceStats(withBodyBytes(req, outBytes), before, outBytes.length, replCount);
    }

    private static String replaceInHttpRequestBody(AgentToolContext ctx, JsonNode args) {
        HttpRequest req = requireCurrentRequest(ctx);
        try {
            HttpBodyAndReplaceStats s = replaceInHttpRequestBodyOnRequest(req, args);
            commitLiveRequest(ctx, s.request);

            ObjectNode o = JSON.createObjectNode();
            o.put("ok", true);
            o.put("current_history_index", ctx.currentHistoryIndex());
            o.put("bytes_before", s.bytesBefore);
            o.put("bytes_after", s.bytesAfter);
            o.put("replacements", s.replacements);
            return write(o);
        } catch (IllegalArgumentException e) {
            return errorJson(e.getMessage());
        }
    }

    private static int countNonOverlappingMatches(String text, String needle) {
        if (needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int pos = 0; pos <= text.length() - needle.length(); ) {
            int idx = text.indexOf(needle, pos);
            if (idx < 0) {
                break;
            }
            count++;
            pos = idx + needle.length();
        }
        return count;
    }

    private record PatchedBody(
            HttpRequest request, int linesTotalBefore, int lineSpan, int linesPatchedIn, int beforeBytes, int afterBytes) {}

    private static PatchedBody patchHttpRequestBodyLinesOnRequest(HttpRequest req, JsonNode args) {
        int startLine = requiredPositiveInt(args, "start_line", "startLine");
        int endLine = requiredPositiveInt(args, "end_line", "endLine");
        if (endLine < startLine) {
            throw new IllegalArgumentException("end_line must be >= start_line");
        }
        JsonNode contentNode = argFirst(args, "content");
        if (contentNode == null || contentNode.isNull()) {
            throw new IllegalArgumentException("missing content");
        }
        String content = contentNode.asText();

        byte[] rawBytes = bytesFromByteArray(() -> req.body());
        int before = rawBytes.length;
        String text = Utilities.decodeUtf8Strict(rawBytes);
        if (text == null) {
            throw new IllegalArgumentException(
                    "request body is not valid UTF-8; use set_http_request_body with body_base64");
        }

        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\\R", -1)));
        int n = lines.size();
        if (startLine > n || endLine > n) {
            throw new IllegalArgumentException("line range out of bounds (body has " + n + " line(s))");
        }

        int startIdx = startLine - 1;
        int endExclusive = endLine;
        List<String> contentLines = Arrays.asList(content.split("\\R", -1));

        List<String> out = new ArrayList<>();
        out.addAll(lines.subList(0, startIdx));
        out.addAll(contentLines);
        out.addAll(lines.subList(endExclusive, n));

        String joined = String.join("\n", out);
        byte[] outBytes = joined.getBytes(StandardCharsets.UTF_8);
        return new PatchedBody(
                withBodyBytes(req, outBytes), n, endLine - startLine + 1, contentLines.size(), before, outBytes.length);
    }

    private static String patchHttpRequestBodyLines(AgentToolContext ctx, JsonNode args) {
        HttpRequest req = requireCurrentRequest(ctx);
        try {
            PatchedBody p = patchHttpRequestBodyLinesOnRequest(req, args);
            commitLiveRequest(ctx, p.request);
            ObjectNode o = JSON.createObjectNode();
            o.put("ok", true);
            o.put("current_history_index", ctx.currentHistoryIndex());
            o.put("lines_total_before", p.linesTotalBefore);
            o.put("lines_replaced_span", p.lineSpan);
            o.put("lines_patched_in", p.linesPatchedIn);
            o.put("bytes_before", p.beforeBytes);
            o.put("bytes_after", p.afterBytes);
            return write(o);
        } catch (IllegalArgumentException e) {
            return errorJson(e.getMessage());
        }
    }

    private static int requiredPositiveInt(JsonNode args, String... keys) {
        JsonNode v = argFirst(args, keys);
        if (v == null) {
            throw new IllegalArgumentException("missing " + keys[0]);
        }
        int i = jsonToInt(v);
        if (i < 1) {
            throw new IllegalArgumentException(keys[0] + " must be >= 1");
        }
        return i;
    }

    private static byte[] newBodyBytesForSetRequest(JsonNode args) {
        JsonNode utf8Node = argFirst(args, "body_utf8", "bodyUtf8", "bodyUTF8");
        JsonNode b64Node = argFirst(args, "body_base64", "bodyBase64", "bodyB64");
        boolean hasUtf8 = utf8Node != null && !utf8Node.isNull();
        boolean hasB64 = b64Node != null && !b64Node.isNull();
        if (hasUtf8 && hasB64) {
            throw new IllegalArgumentException("provide either body_utf8 or body_base64, not both");
        }
        if (!hasUtf8 && !hasB64) {
            throw new IllegalArgumentException("provide body_utf8 or body_base64");
        }

        if (hasUtf8) {
            String text;
            if (utf8Node.isTextual()) {
                text = utf8Node.asText();
            } else if (utf8Node.isNumber() || utf8Node.isBoolean()) {
                text = utf8Node.asText();
            } else if (utf8Node.isObject() || utf8Node.isArray()) {
                try {
                    text = JSON.writeValueAsString(utf8Node);
                } catch (JsonProcessingException e) {
                    throw new IllegalArgumentException("could not serialize body_utf8 JSON");
                }
            } else {
                throw new IllegalArgumentException(
                        "body_utf8 must be a string, JSON object/array, or number (use body_base64 for binary)");
            }
            return text.getBytes(StandardCharsets.UTF_8);
        }
        String b64 = b64Node.asText().replaceAll("\\s+", "");
        try {
            return Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid body_base64");
        }
    }

    private static HttpRequest setHttpRequestBodyOnRequest(HttpRequest req, JsonNode args) {
        return withBodyBytes(req, newBodyBytesForSetRequest(args));
    }

    private static String setHttpRequestBody(AgentToolContext ctx, JsonNode args) {
        HttpRequest req = requireCurrentRequest(ctx);
        try {
            int before = bytesFromByteArray(() -> req.body()).length;
            byte[] newB = newBodyBytesForSetRequest(args);
            HttpRequest updated = withBodyBytes(req, newB);
            int after = newB.length;
            commitLiveRequest(ctx, updated);

            ObjectNode o = JSON.createObjectNode();
            o.put("ok", true);
            o.put("current_history_index", ctx.currentHistoryIndex());
            o.put("bytes_before", before);
            o.put("bytes_after", after);
            return write(o);
        } catch (IllegalArgumentException e) {
            return errorJson(e.getMessage());
        }
    }

    /**
     * Rebuilds the {@code Cookie} header: removes any pair whose name matches case-insensitively, then adds {@code
     * name=value} unless {@code remove}.
     */
    private static HttpRequest mergeCookieHeader(HttpRequest req, String name, String value, boolean remove) {
        LinkedHashMap<String, String[]> byLower = new LinkedHashMap<>();
        for (HttpHeader h : safeHeaders(() -> req.headers())) {
            if (h == null || !"cookie".equalsIgnoreCase(safeString(() -> h.name(), ""))) {
                continue;
            }
            String hv = safeString(() -> h.value(), "");
            for (String[] nv : parseCookiePairs(hv)) {
                String low = nv[0].toLowerCase(Locale.ROOT);
                byLower.putIfAbsent(low, new String[] {nv[0], nv[1]});
            }
        }
        String low = name.toLowerCase(Locale.ROOT);
        byLower.remove(low);
        if (!remove) {
            byLower.put(low, new String[] {name, value});
        }
        HttpRequest cur = req.withRemovedHeader("Cookie");
        if (byLower.isEmpty()) {
            return cur;
        }
        StringBuilder sb = new StringBuilder();
        for (String[] nv : byLower.values()) {
            if (!sb.isEmpty()) {
                sb.append("; ");
            }
            sb.append(nv[0]).append("=").append(nv[1]);
        }
        return cur.withHeader("Cookie", sb.toString());
    }

    private static HttpRequest applyAbsoluteUrl(HttpRequest req, String urlRaw) {
        URI uri;
        try {
            uri = new URI(urlRaw.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("invalid URL: " + e.getMessage());
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("URL must include a scheme (http or https)");
        }
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("only http and https URLs are supported");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must include a host");
        }
        boolean secure = "https".equalsIgnoreCase(scheme);
        int port = uri.getPort();
        if (port < 0) {
            port = secure ? 443 : 80;
        }
        HttpService service = HttpService.httpService(host, port, secure);
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        String query = uri.getRawQuery();
        if (query != null) {
            path = path + "?" + query;
        }
        return req.withService(service).withPath(path);
    }

    private record SemanticApplyResult(HttpRequest request, String errorResultJson) {}

    private static SemanticApplyResult applyHttpRequestSemanticChangesToRequest0(HttpRequest start, JsonNode args) {
        HttpRequest[] current = {start};
        JsonNode opsNode = args.get("operations");
        if (opsNode == null || !opsNode.isArray()) {
            return new SemanticApplyResult(
                    null,
                    semanticOperationsShapeError(
                            "missing or invalid operations array",
                            "The tool arguments must include a JSON array field \"operations\" (see \"example\")."));
        }
        int n = opsNode.size();
        if (n == 0) {
            return new SemanticApplyResult(
                    null,
                    semanticOperationsShapeError(
                            "operations must not be empty",
                            "Include at least one operation object in \"operations\" (see \"example\")."));
        }
        if (n > MAX_SEMANTIC_OPERATIONS) {
            return new SemanticApplyResult(
                    null,
                    semanticOperationsShapeError(
                            "at most " + MAX_SEMANTIC_OPERATIONS + " operations per call",
                            "Split work into multiple apply_http_request_semantic_changes calls."));
        }
        for (int i = 0; i < n; i++) {
            JsonNode one = opsNode.get(i);
            if (one == null || !one.isObject()) {
                return new SemanticApplyResult(
                        null,
                        semanticMutationError(
                                i,
                                "?",
                                "invalid operation at index " + i,
                                "each operation must be a JSON object with type and action",
                                "read the tool description for apply_http_request_semantic_changes"));
            }
            String err = applyOneSemanticOperation(current, i, (ObjectNode) one);
            if (err != null) {
                return new SemanticApplyResult(null, err);
            }
        }
        return new SemanticApplyResult(current[0], null);
    }

    /** Error payload when the top-level {@code operations} array is missing, wrong type, empty, or too large. */
    private static String semanticOperationsShapeError(String message, String hint) {
        ObjectNode n = JSON.createObjectNode();
        n.put("error", message);
        n.put("op_index", -1);
        n.put("op_type", "");
        n.put("hint", hint);
        n.put("detail", "");
        n.put("example", APPLY_HTTP_REQUEST_SEMANTIC_CHANGES_EXAMPLE_ARGS);
        return write(n);
    }

    private static String applyHttpRequestSemanticChanges(AgentToolContext ctx, JsonNode args) {
        SemanticApplyResult r =
                applyHttpRequestSemanticChangesToRequest0(requireCurrentRequest(ctx), args);
        if (r.errorResultJson() != null) {
            return r.errorResultJson();
        }
        try {
            commitLiveRequest(ctx, r.request());
        } catch (Exception e) {
            return errorJson(e.getMessage() != null ? e.getMessage() : "commit failed");
        }
        int n = args.get("operations") != null && args.get("operations").isArray() ? args.get("operations").size() : 0;
        ObjectNode o = JSON.createObjectNode();
        o.put("ok", true);
        o.put("operations_applied", n);
        o.put("current_history_index", ctx.currentHistoryIndex());
        return write(o);
    }

    private static String semanticMutationError(
            int opIndex, String opType, String message, String hint, String moreHint) {
        ObjectNode n = JSON.createObjectNode();
        n.put("error", message);
        n.put("op_index", opIndex);
        n.put("op_type", opType != null ? opType : "");
        n.put("hint", hint);
        n.put("detail", moreHint);
        return write(n);
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

    private static boolean opHasExplicitValueProperty(ObjectNode op) {
        return op.has("value");
    }

    private static String applyOneSemanticOperation(HttpRequest[] current, int opIndex, ObjectNode op) {
        String type = opStringLower(op, "type");
        String action = opStringLower(op, "action");
        if (type.isEmpty() || !isKnownSemanticType(type)) {
            return semanticMutationError(
                    opIndex,
                    type,
                    "unknown or empty type (expected header, cookie, json, xml, method, or url)",
                    "set the type field to one of the allowed values",
                    "");
        }
        if (!"set".equals(action) && !"remove".equals(action)) {
            return semanticMutationError(
                    opIndex,
                    type,
                    "action must be set or remove",
                    "set action to remove to delete, or set to assign (including JSON null in-body via value: null for json type)",
                    "");
        }
        if ("remove".equals(action) && opHasExplicitValueProperty(op)) {
            return semanticMutationError(
                    opIndex,
                    type,
                    "value must be omitted when action is remove",
                    "removal is controlled only by action: remove; do not pass a value field at all on this op",
                    "");
        }
        return switch (type) {
            case "header" -> applySemanticHeader(current, opIndex, op, action);
            case "cookie" -> applySemanticCookie(current, opIndex, op, action);
            case "json" -> applySemanticJson(current, opIndex, op, action);
            case "xml" -> applySemanticXml(current, opIndex, op, action);
            case "method" -> applySemanticMethod(current, opIndex, op, action);
            case "url" -> applySemanticUrl(current, opIndex, op, action);
            default -> semanticMutationError(opIndex, type, "unhandled type", "", "");
        };
    }

    private static boolean isKnownSemanticType(String type) {
        return "header".equals(type)
                || "cookie".equals(type)
                || "json".equals(type)
                || "xml".equals(type)
                || "method".equals(type)
                || "url".equals(type);
    }

    private static String applySemanticHeader(HttpRequest[] current, int opIndex, ObjectNode op, String action) {
        String key = opTextTrimmed(op, "key");
        if (key.isEmpty()) {
            return semanticMutationError(
                    opIndex, "header", "key is required for type header", "set key to the header name", "");
        }
        if ("set".equals(action)) {
            if (!opHasExplicitValueProperty(op)) {
                return semanticMutationError(
                        opIndex, "header", "value is required for action set on header", "set the value string (empty allowed)", "");
            }
            JsonNode vn = op.get("value");
            String value = vn == null || vn.isNull() ? "" : vn.isTextual() ? vn.asText() : vn.asText();
            current[0] = current[0].withHeader(key, value);
        } else {
            current[0] = current[0].withRemovedHeader(key);
        }
        return null;
    }

    private static String applySemanticCookie(HttpRequest[] current, int opIndex, ObjectNode op, String action) {
        String name = opTextTrimmed(op, "key");
        if (name.isEmpty()) {
            return semanticMutationError(
                    opIndex, "cookie", "key is required for type cookie (cookie name)", "set key to the cookie name", "");
        }
        if ("set".equals(action)) {
            if (!opHasExplicitValueProperty(op)) {
                return semanticMutationError(
                        opIndex, "cookie", "value is required for action set on cookie", "set value to the cookie value string", "");
            }
            JsonNode vn = op.get("value");
            String value = vn == null || vn.isNull() ? "" : vn.isTextual() ? vn.asText() : vn.asText();
            current[0] = mergeCookieHeader(current[0], name, value, false);
        } else {
            current[0] = mergeCookieHeader(current[0], name, "", true);
        }
        return null;
    }

    private static String applySemanticMethod(HttpRequest[] current, int opIndex, ObjectNode op, String action) {
        if ("remove".equals(action)) {
            return semanticMutationError(
                    opIndex,
                    "method",
                    "type method does not support action remove",
                    "only action set is valid; give an HTTP method in value and leave key empty",
                    "");
        }
        String key = opTextTrimmed(op, "key");
        if (!key.isEmpty()) {
            return semanticMutationError(
                    opIndex,
                    "method",
                    "key must be empty for type method (use value for the HTTP method)",
                    "remove the key field or set it to an empty string",
                    "");
        }
        if (!opHasExplicitValueProperty(op) || !op.get("value").isTextual()) {
            return semanticMutationError(
                    opIndex, "method", "value is required and must be a string (the HTTP method)", "e.g. GET or POST", "");
        }
        String method = op.get("value").asText().trim();
        if (method.isEmpty()) {
            return semanticMutationError(opIndex, "method", "method value is empty", "set value to a non-empty method name", "");
        }
        current[0] = current[0].withMethod(method);
        return null;
    }

    private static String applySemanticUrl(HttpRequest[] current, int opIndex, ObjectNode op, String action) {
        if ("remove".equals(action)) {
            return semanticMutationError(
                    opIndex, "url", "type url does not support action remove", "only action set is valid with an absolute URL in value", "");
        }
        String key = opTextTrimmed(op, "key");
        if (!key.isEmpty()) {
            return semanticMutationError(
                    opIndex, "url", "key must be empty for type url (use value for the absolute URL)", "remove key or use an empty string", "");
        }
        if (!opHasExplicitValueProperty(op) || !op.get("value").isTextual()) {
            return semanticMutationError(
                    opIndex, "url", "value is required and must be a string (absolute http(s) URL)", "e.g. https://host/path?query=1", "");
        }
        String url = op.get("value").asText().trim();
        if (url.isEmpty()) {
            return semanticMutationError(opIndex, "url", "url value is empty", "set value to a full URL string", "");
        }
        try {
            current[0] = applyAbsoluteUrl(current[0], url);
        } catch (IllegalArgumentException e) {
            return semanticMutationError(
                    opIndex, "url", e.getMessage() != null ? e.getMessage() : "invalid url", "fix the URL and retry", "");
        }
        return null;
    }

    private static String applySemanticJson(HttpRequest[] current, int opIndex, ObjectNode op, String action) {
        String pathStr = opTextTrimmed(op, "path");
        if (pathStr.isEmpty()) {
            return semanticMutationError(
                    opIndex, "json", "path is required for type json (JSON Pointer, RFC 6901)", "e.g. /data/0/id", "");
        }
        final JsonPointer pointer;
        try {
            pointer = JsonPointer.valueOf(pathStr);
        } catch (IllegalArgumentException e) {
            return semanticMutationError(
                    opIndex, "json", e.getMessage() != null ? e.getMessage() : "invalid JSON Pointer", "path must be a valid JSON Pointer starting with /", "");
        }
        if (pointer.matches()) {
            return semanticMutationError(
                    opIndex,
                    "json",
                    "empty JSON Pointer is not allowed for type json; use set_http_request_body to replace the whole body",
                    "or use a non-empty path to patch part of the JSON",
                    "");
        }
        if ("set".equals(action) && !opHasExplicitValueProperty(op)) {
            return semanticMutationError(
                    opIndex, "json", "value is required for action set (use value: null for JSON null in the body)", "include a value field, even if null", "");
        }
        byte[] raw = bytesFromByteArray(() -> current[0].body());
        String utf8 = Utilities.decodeUtf8Strict(raw);
        if (utf8 == null) {
            return semanticMutationError(
                    opIndex,
                    "json",
                    "request body is not valid UTF-8 for JSON mutation",
                    "use set_http_request_body with body_base64, or fix encoding first",
                    "read raw bytes with read_http_message if you need to inspect the body");
        }
        JsonNode root;
        try {
            root = JSON.readTree(raw);
        } catch (JsonProcessingException e) {
            return semanticMutationError(
                    opIndex,
                    "json",
                    "request body is not valid JSON: " + (e.getOriginalMessage() != null ? e.getOriginalMessage() : e.getMessage()),
                    "use set_http_request_body to replace the body, or read_http_message to inspect it",
                    "");
        } catch (IOException e) {
            return semanticMutationError(
                    opIndex,
                    "json",
                    "request body is not valid JSON: " + (e.getMessage() != null ? e.getMessage() : e.toString()),
                    "use set_http_request_body to replace the body, or read_http_message to inspect it",
                    "");
        }
        try {
            if ("remove".equals(action)) {
                mutateJsonTreeAtPointer(root, pathStr, true, null);
            } else {
                mutateJsonTreeAtPointer(root, pathStr, false, op.get("value"));
            }
        } catch (IllegalArgumentException e) {
            return semanticMutationError(
                    opIndex, "json", e.getMessage() != null ? e.getMessage() : "JSON mutation failed", "check path against the current JSON and retry", "");
        }
        byte[] out;
        try {
            out = JSON.writeValueAsBytes(root);
        } catch (JsonProcessingException e) {
            return semanticMutationError(opIndex, "json", "failed to serialize JSON after mutation", e.getMessage(), "");
        }
        current[0] = withBodyBytes(current[0], out);
        return null;
    }

    private static void mutateJsonTreeAtPointer(JsonNode root, String pathStr, boolean remove, JsonNode newValue) {
        if (pathStr == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        String s = pathStr.trim();
        if (s.isEmpty() || s.equals("/")) {
            throw new IllegalArgumentException("empty JSON Pointer is not allowed; use set_http_request_body to replace the whole body");
        }
        if (s.charAt(0) != '/') {
            throw new IllegalArgumentException("JSON Pointer must start with /");
        }
        int lastSlash = s.lastIndexOf('/');
        if (lastSlash < 0) {
            throw new IllegalArgumentException("JSON Pointer must start with /");
        }
        String lastTokenRaw = s.substring(lastSlash + 1);
        String lastRef = rfc6901UnescapeToken(lastTokenRaw);
        JsonNode parent = lastSlash == 0 ? root : root.at(JsonPointer.valueOf(s.substring(0, lastSlash)));
        if (parent == null || parent.isMissingNode()) {
            throw new IllegalArgumentException("JSON Pointer path not found (parent is missing)");
        }
        if (!parent.isObject() && !parent.isArray()) {
            throw new IllegalArgumentException("JSON Pointer path not found: parent is not an object or array");
        }
        if (parent.isObject()) {
            ObjectNode ob = (ObjectNode) parent;
            if (remove) {
                if (!ob.has(lastRef)) {
                    throw new IllegalArgumentException("JSON Pointer path not found: no such property to remove");
                }
                ob.remove(lastRef);
            } else {
                ob.set(lastRef, newValue);
            }
            return;
        }
        int idx;
        try {
            idx = Integer.parseInt(lastRef, 10);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("JSON Pointer for array must end with a non-negative integer index");
        }
        if (idx < 0) {
            throw new IllegalArgumentException("JSON Pointer array index must be non-negative");
        }
        ArrayNode ar = (ArrayNode) parent;
        if (remove) {
            if (idx >= ar.size()) {
                throw new IllegalArgumentException("JSON Pointer array index out of range for remove");
            }
            ar.remove(idx);
        } else {
            if (idx > ar.size()) {
                throw new IllegalArgumentException("JSON Pointer array index out of range (gaps are not allowed)");
            }
            if (idx == ar.size()) {
                ar.add(newValue);
            } else {
                ar.set(idx, newValue);
            }
        }
    }

    /** RFC 6901 reference token unescape: ~1 -> /, ~0 -> ~. */
    private static String rfc6901UnescapeToken(String raw) {
        if (raw.isEmpty()) {
            return "";
        }
        return raw.replace("~1", "/").replace("~0", "~");
    }

    private static String applySemanticXml(HttpRequest[] current, int opIndex, ObjectNode op, String action) {
        String pathStr = opTextTrimmed(op, "path");
        if (pathStr.isEmpty()) {
            return semanticMutationError(
                    opIndex, "xml", "path is required for type xml (XPath 1.0 expression)", "e.g. //item or /root/a", "");
        }
        if ("set".equals(action) && !opHasExplicitValueProperty(op)) {
            return semanticMutationError(
                    opIndex, "xml", "value is required for action set on xml (text to set for matched elements)", "set value to a string; attribute targets are not supported in v1", "");
        }
        if ("set".equals(action) && !op.get("value").isTextual()) {
            return semanticMutationError(
                    opIndex, "xml", "value for type xml on set must be a string in v1", "only text content of matched elements is updated", "");
        }
        byte[] raw = bytesFromByteArray(() -> current[0].body());
        String utf8 = Utilities.decodeUtf8Strict(raw);
        if (utf8 == null) {
            return semanticMutationError(
                    opIndex, "xml", "request body is not valid UTF-8 for xml mutation", "use set_http_request_body with body_base64 first", "read the body with read_http_message if needed");
        }
        DocumentBuilder db;
        try {
            db = newSecureDocumentBuilder();
        } catch (Exception e) {
            return semanticMutationError(
                    opIndex, "xml", "could not create XML parser: " + (e.getMessage() != null ? e.getMessage() : e), "", "");
        }
        Document doc;
        try {
            doc = db.parse(new ByteArrayInputStream(raw));
        } catch (Exception e) {
            return semanticMutationError(
                    opIndex,
                    "xml",
                    "request body is not well-formed XML: " + (e.getMessage() != null ? e.getMessage() : e.toString()),
                    "use set_http_request_body to replace the body, or read_http_message to inspect it",
                    "");
        }
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nl;
        try {
            nl = (NodeList) xPath.compile(pathStr).evaluate(doc, XPathConstants.NODESET);
        } catch (Exception e) {
            return semanticMutationError(
                    opIndex,
                    "xml",
                    "invalid or unsupported XPath: " + (e.getMessage() != null ? e.getMessage() : e),
                    "simplify the XPath expression and retry",
                    "");
        }
        if (nl.getLength() == 0) {
            return semanticMutationError(
                    opIndex, "xml", "XPath matched no nodes", "adjust path or set the body so the target exists", "use read_http_message to inspect the XML");
        }
        if ("set".equals(action)) {
            String v = op.get("value").asText();
            Node n = nl.item(0);
            if (n.getNodeType() == Node.ATTRIBUTE_NODE) {
                return semanticMutationError(
                        opIndex,
                        "xml",
                        "type xml set: attribute nodes are not supported in v1 (matched an attribute with XPath)",
                        "use an XPath to an element and set its text, or set_http_request_body to rewrite attributes",
                        "");
            }
            n.setTextContent(v);
        } else {
            List<Node> toRemove = new ArrayList<>();
            for (int k = 0; k < nl.getLength(); k++) {
                toRemove.add(nl.item(k));
            }
            for (Node n : toRemove) {
                if (n.getNodeType() == Node.ATTRIBUTE_NODE) {
                    Attr a = (Attr) n;
                    Element e = a.getOwnerElement();
                    if (e != null) {
                        e.removeAttributeNode(a);
                    }
                } else {
                    Node p = n.getParentNode();
                    p.removeChild(n);
                }
            }
        }
        try {
            current[0] = withBodyBytes(current[0], serializeXmlDocument(doc));
        } catch (Exception e) {
            return semanticMutationError(
                    opIndex, "xml", "failed to serialize XML: " + (e.getMessage() != null ? e.getMessage() : e), "", "");
        }
        return null;
    }

    private static DocumentBuilder newSecureDocumentBuilder() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        try {
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (java.lang.IllegalArgumentException ignored) {
            // not supported on all JAXP providers
        }
        dbf.setExpandEntityReferences(false);
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder();
    }

    private static byte[] serializeXmlDocument(Document doc) throws TransformerException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TransformerFactory tf = TransformerFactory.newInstance();
        try {
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        } catch (java.lang.IllegalArgumentException ignored) {
            // not supported on all JAXP
        }
        Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        t.transform(new DOMSource(doc), new StreamResult(out));
        return out.toByteArray();
    }

    /** Tool result intentionally contains only {@code status_code} (no body or headers). */
    private static String sendCurrentHttpRequest(AgentToolContext ctx) throws Exception {
        Callable<Integer> sender = ctx.sendCurrentHttpRequest();
        if (sender == null) {
            return errorJson("send is unavailable in this context");
        }
        int code = sender.call();
        ObjectNode o = JSON.createObjectNode();
        o.put("status_code", code);
        return write(o);
    }

    private static List<HttpHeader> safeHeaders(java.util.function.Supplier<List<HttpHeader>> supplier) {
        try {
            List<HttpHeader> h = supplier.get();
            return h != null ? h : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static byte[] bytesFromByteArray(java.util.function.Supplier<ByteArray> supplier) {
        try {
            ByteArray b = supplier.get();
            if (b == null) {
                return new byte[0];
            }
            byte[] raw = b.getBytes();
            return raw != null ? raw : new byte[0];
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static List<String[]> parseCookiePairs(String cookieHeaderValue) {
        List<String[]> out = new ArrayList<>();
        if (cookieHeaderValue == null || cookieHeaderValue.isBlank()) {
            return out;
        }
        for (String part : cookieHeaderValue.split(";")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            int eq = p.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = p.substring(0, eq).trim();
            String value = p.substring(eq + 1).trim();
            if (!name.isEmpty() && !name.startsWith("$")) {
                out.add(new String[] {name, value});
            }
        }
        return out;
    }

    /** Accepts camelCase {@code side} and short aliases ({@code req}/{@code res}). */
    private static String resolveSide(JsonNode args) {
        JsonNode v = argFirst(args, "side", "Side", "http_side", "httpSide");
        if (v == null) {
            throw new IllegalArgumentException(
                    "missing side: use \"request\" or \"response\" (field name: side)");
        }
        if (!v.isTextual()) {
            throw new IllegalArgumentException("side must be a string: \"request\" or \"response\"");
        }
        String raw = v.asText().trim();
        if (raw.isBlank()) {
            throw new IllegalArgumentException("missing side");
        }
        String s = normalizeSideLiteral(raw);
        if (!"request".equals(s) && !"response".equals(s)) {
            throw new IllegalArgumentException("side must be \"request\" or \"response\" (got \"" + raw + "\")");
        }
        return s;
    }

    private static String normalizeSideLiteral(String raw) {
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

    private static int readOffsetArg(JsonNode args) {
        JsonNode v = argFirst(args, "offset", "byte_offset", "byteOffset", "start");
        if (v == null) {
            return 0;
        }
        return Math.max(0, jsonToInt(v));
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

    private static String safeString(java.util.function.Supplier<String> supplier, String onFailure) {
        try {
            String s = supplier.get();
            return s != null ? s : onFailure;
        } catch (Exception e) {
            return onFailure;
        }
    }

    private static String write(ObjectNode n) {
        try {
            return JSON.writeValueAsString(n);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String errorJson(String message) {
        ObjectNode n = JSON.createObjectNode();
        n.put("error", message);
        try {
            return JSON.writeValueAsString(n);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"error\"}";
        }
    }

    /** JSON tool result when the user declines to run a tool. */
    public static String permissionDeniedResult() {
        return errorJson("permission denied");
    }

    private static final int MAX_SEMANTIC_HUMAN_DETAIL_CHARS = 12_000;

    /** One line per operation for agent tool cards ({@link #humanToolUsage}). */
    private static String formatSemanticOperationsHumanDetail(JsonNode args) {
        JsonNode arr = args.get("operations");
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

    /**
     * Preview-only mutation: same in-memory result as the corresponding tool, without updating the Repeater editor.
     * Returns {@code null} for non-mutating tools, invalid arguments, or if the change cannot be applied.
     */
    public static HttpRequest tryPreviewRequestMutation(
            String toolName, String argumentsJson, HttpRequest current) {
        if (current == null) {
            return null;
        }
        try {
            JsonNode args = parseArgs(argumentsJson);
            return switch (toolName) {
                case REPLACE_IN_HTTP_REQUEST_BODY -> replaceInHttpRequestBodyOnRequest(current, args).request();
                case PATCH_HTTP_REQUEST_BODY_LINES -> patchHttpRequestBodyLinesOnRequest(current, args).request();
                case SET_HTTP_REQUEST_BODY -> setHttpRequestBodyOnRequest(current, args);
                case APPLY_HTTP_REQUEST_SEMANTIC_CHANGES -> {
                    SemanticApplyResult s = applyHttpRequestSemanticChangesToRequest0(current, args);
                    yield s.errorResultJson() != null ? null : s.request();
                }
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Title and optional detail for the tool transcript card. Mutations that change the in-editor request include a
     * non-empty {@link HumanToolUsage#detail} describing the change, except for {@link #SET_HTTP_REQUEST_BODY} where
     * the new body is omitted.
     *
     * @param viewerHistoryIndex the tab's current history index, or {@link Integer#MIN_VALUE} if unknown; when equal to
     *     {@code history_index} in the tool args, the "· history #n" suffix is omitted.
     */
    public static HumanToolUsage humanToolUsage(String toolName, String argumentsJson, int viewerHistoryIndex) {
        return humanToolUsage(toolName, argumentsJson, viewerHistoryIndex, Integer.MIN_VALUE);
    }

    /**
     * @param uiSelectedRequestNodeId {@link RepeaterTabAgentBridge#uiSelectedRequestNodeIdForToolCard()}; used to add
     *     {@code · node id n} to titles and to omit that suffix when {@code request_node_id} matches the UI-selected
     *     tab. {@link Integer#MIN_VALUE} skips suffix unless {@code request_node_id} is set in args.
     */
    public static HumanToolUsage humanToolUsage(
            String toolName, String argumentsJson, int viewerHistoryIndex, int uiSelectedRequestNodeId) {
        JsonNode args;
        try {
            args = parseArgs(argumentsJson);
        } catch (Exception e) {
            args = JSON.createObjectNode();
        }
        JsonNode sideNode = argFirst(args, "side", "Side", "http_side", "httpSide");
        String sideNorm =
                sideNode != null && sideNode.isTextual()
                        ? normalizeSideLiteral(sideNode.asText())
                        : "";
        String sideLabel =
                "response".equals(sideNorm)
                        ? "Response"
                        : "request".equals(sideNorm) ? "Request" : "";
        String hist = formatHistoryIndexArg(args, viewerHistoryIndex);
        String nodeSuf = formatRequestNodeIdSuffix(args, uiSelectedRequestNodeId);
        return switch (toolName) {
            case GET_CURRENT_HTTP_TARGET ->
                    new HumanToolUsage("Getting current repeater target and send history" + nodeSuf, "");
            case READ_HTTP_MESSAGE -> {
                String head =
                        sideLabel.isEmpty()
                                ? "Reading HTTP message"
                                : "Reading " + sideLabel.toLowerCase();
                StringBuilder b = new StringBuilder(head);
                if (!hist.isEmpty()) {
                    b.append(hist);
                }
                int offset = readOffsetArg(args);
                int maxBytes = readMaxBytesForReadMessage(args);
                b.append(" · offset ").append(offset).append(", max ").append(maxBytes).append(" B");
                b.append(nodeSuf);
                yield new HumanToolUsage(b.toString(), "");
            }
            case SEARCH_HTTP_MESSAGE -> {
                String head =
                        sideLabel.isEmpty()
                                ? "Searching HTTP message"
                                : "Searching " + sideLabel.toLowerCase();
                StringBuilder b = new StringBuilder(head);
                String sc = resolveSearchScope(args);
                if (!"all".equals(sc)) {
                    b.append(" (scope=").append(sc).append(")");
                }
                if (!hist.isEmpty()) {
                    b.append(hist);
                }
                String pat = argTextAny(args, "pattern", "regex", "re");
                String det = pat.isEmpty() ? "" : quotedSnippet(pat, 96);
                b.append(nodeSuf);
                yield new HumanToolUsage(b.toString(), det);
            }
            case REPLACE_IN_HTTP_REQUEST_BODY -> {
                String oldT = argTextAny(args, "old_text", "oldText");
                String newT = "";
                JsonNode newNode = argFirst(args, "new_text", "newText");
                if (newNode != null && !newNode.isNull()) {
                    newT = newNode.asText();
                }
                boolean replaceAll = args.has("replace_all") && args.get("replace_all").asBoolean(false);
                int maxRep = 1;
                if (!replaceAll) {
                    JsonNode m = argFirst(args, "max_replacements", "maxReplacements");
                    if (m != null) {
                        maxRep = jsonToInt(m);
                    }
                }
                StringBuilder d = new StringBuilder();
                d.append("Find ")
                        .append(quotedSnippet(oldT, 72))
                        .append(" → replace with ")
                        .append(quotedSnippet(newT, 72));
                if (replaceAll) {
                    d.append(" · all occurrences");
                } else if (maxRep > 1) {
                    d.append(" · up to ").append(maxRep).append(" time(s)");
                }
                yield new HumanToolUsage("Replace text in request body" + nodeSuf, d.toString());
            }
            case PATCH_HTTP_REQUEST_BODY_LINES -> {
                int sl = jsonToInt(argFirst(args, "start_line", "startLine"));
                int el = jsonToInt(argFirst(args, "end_line", "endLine"));
                JsonNode contentNode = argFirst(args, "content");
                String content = contentNode != null && !contentNode.isNull() ? contentNode.asText() : "";
                String preview = singleLinePreview(content, 200);
                String det =
                        "Lines " + sl + "–" + el
                                + (preview.isEmpty() ? "" : " · new text: " + preview);
                yield new HumanToolUsage("Patch request body line range" + nodeSuf, det);
            }
            case SET_HTTP_REQUEST_BODY -> new HumanToolUsage("Setting full request body" + nodeSuf, "");
            case APPLY_HTTP_REQUEST_SEMANTIC_CHANGES ->
                    new HumanToolUsage(
                            "Apply semantic request changes" + nodeSuf, formatSemanticOperationsHumanDetail(args));
            case SEND_CURRENT_HTTP_REQUEST ->
                    new HumanToolUsage(
                            "Send current HTTP request" + nodeSuf,
                            "Sends the in-editor request and waits for the response (status only)");
            case SEARCH_TABS -> {
                int off = 0;
                JsonNode offN = argFirst(args, "offset");
                if (offN != null && offN.isNumber()) {
                    off = Math.max(0, offN.intValue());
                }
                int ps = DEFAULT_TAB_PAGE_SIZE;
                JsonNode psN = argFirst(args, "page_size", "pageSize");
                if (psN != null && psN.isNumber()) {
                    ps = Math.min(MAX_TAB_PAGE_SIZE, Math.max(1, psN.intValue()));
                }
                String q = argTextAny(args, "query", "q", "search");
                String det = q.isEmpty() ? "all tabs" : quotedSnippet(q, 80);
                yield new HumanToolUsage("Search repeater tabs · offset " + off + ", page " + ps, det);
            }
            default -> new HumanToolUsage("Working…" + nodeSuf, "");
        };
    }

    private static String quotedSnippet(String s, int maxTotal) {
        if (s == null) {
            s = "";
        }
        return "\"" + truncateForStatus(s, Math.max(8, maxTotal - 2)) + "\"";
    }

    private static String singleLinePreview(String s, int max) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String one = s.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        while (one.contains("  ")) {
            one = one.replace("  ", " ");
        }
        return truncateForStatus(one.trim(), max);
    }

    /**
     * When {@code request_node_id} is in args, appends {@code · node id n} unless it matches {@code uiSelectedId}.
     * When args omit it, appends the UI-selected id if known. Mirrors {@link #formatHistoryIndexArg} for explicit args.
     */
    private static String formatRequestNodeIdSuffix(JsonNode args, int uiSelectedId) {
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
    private static String formatHistoryIndexArg(JsonNode args, int viewerHistoryIndex) {
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

    private static String argTextAny(JsonNode args, String... keys) {
        JsonNode v = argFirst(args, keys);
        if (v == null) {
            return "";
        }
        String s = v.isTextual() ? v.asText().trim() : v.asText();
        return s != null ? s.trim() : "";
    }

    private static String truncateForStatus(String s, int maxChars) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars - 1) + "…";
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
