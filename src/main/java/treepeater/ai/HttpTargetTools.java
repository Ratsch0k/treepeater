package treepeater.ai;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.SwingUtilities;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

/**
 * Built-in tools: HTTP target summary, repeater history navigation, and scoped inspection of request/response
 * headers, cookies, and body (chunked reads for large bodies).
 */
public final class HttpTargetTools {
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Tool name: current editor/target only; JSON from {@link HttpTargetSnapshot#toJson()} plus nested {@code history}. */
    public static final String GET_CURRENT_HTTP_TARGET = "get_current_http_target";

    public static final String GET_HTTP_HISTORY_STATE = "get_http_history_state";

    /** Request line / target line for a specific send-history entry (method, URL, path, HTTP version, service). */
    public static final String GET_HTTP_REQUEST_LINE = "get_http_request_line";

    public static final String LIST_HTTP_HEADER_NAMES = "list_http_header_names";
    public static final String GET_HTTP_HEADER = "get_http_header";

    public static final String LIST_HTTP_COOKIES = "list_http_cookies";
    public static final String GET_HTTP_COOKIE = "get_http_cookie";

    public static final String READ_HTTP_BODY = "read_http_body";

    /** Substring replace in the current (live editor) request body only. */
    public static final String REPLACE_IN_HTTP_REQUEST_BODY = "replace_in_http_request_body";

    /** Replace a 1-based inclusive line range in the current request body (UTF-8 text). */
    public static final String PATCH_HTTP_REQUEST_BODY_LINES = "patch_http_request_body_lines";

    /** Replace the entire current request body. */
    public static final String SET_HTTP_REQUEST_BODY = "set_http_request_body";

    /** Add or update one header on the current request (live editor). */
    public static final String SET_HTTP_REQUEST_HEADER = "set_http_request_header";

    /** Remove all request headers with the given name (case-insensitive). */
    public static final String REMOVE_HTTP_REQUEST_HEADER = "remove_http_request_header";

    /** Set or remove one Cookie header pair on the current request. */
    public static final String SET_HTTP_REQUEST_COOKIE = "set_http_request_cookie";

    /** Set the HTTP method on the current request. */
    public static final String SET_HTTP_REQUEST_METHOD = "set_http_request_method";

    /** Set scheme, host, port, and path (and query) from an absolute http(s) URL on the current request. */
    public static final String SET_HTTP_REQUEST_URL = "set_http_request_url";

    /** Send the current repeater request and wait for the response; tool result contains only HTTP status_code. */
    public static final String SEND_CURRENT_HTTP_REQUEST = "send_current_http_request";

    private static final int DEFAULT_BODY_CHUNK_BYTES = 16_384;
    private static final int MAX_BODY_CHUNK_BYTES = 262_144;

    private static final String EMPTY_PARAMS_SCHEMA =
            """
            {"type":"object","properties":{},"additionalProperties":false}\
            """;

    private static final String HISTORY_INDEX_ONLY_SCHEMA =
            """
            {"type":"object","properties":{"history_index":{"type":"integer","minimum":0,"description":"Send-history index (0-based). Omit to use the current entry; historyIndex is accepted as an alias."}},"additionalProperties":false}\
            """;

    private static final String SIDE_ENUM = "\"request\",\"response\"";

    private static final int MAX_SUBSTRING_REPLACEMENTS = 100_000;

    private static final String REPLACE_BODY_SCHEMA =
            """
            {"type":"object","properties":{"old_text":{"type":"string","description":"Literal text to find (non-empty)."},"new_text":{"type":"string","description":"Replacement text (may be empty to delete matches)."},"max_replacements":{"type":"integer","minimum":1,"maximum":%d,"default":1,"description":"Maximum non-overlapping replacements (left to right). Ignored when replace_all is true."},"replace_all":{"type":"boolean","default":false,"description":"If true, replace every occurrence; max_replacements is ignored."}},"required":["old_text","new_text"],"additionalProperties":false}\
            """
                    .formatted(MAX_SUBSTRING_REPLACEMENTS);

    private static final String PATCH_LINES_SCHEMA =
            """
            {"type":"object","properties":{"start_line":{"type":"integer","minimum":1,"description":"First line to replace (1-based, inclusive)."},"end_line":{"type":"integer","minimum":1,"description":"Last line to replace (1-based, inclusive)."},"content":{"type":"string","description":"New text for that range; line breaks may be \\\\n or any Unicode line ending (split with Java \\\\R)."}},"required":["start_line","end_line","content"],"additionalProperties":false}\
            """;

    private static final String SET_BODY_SCHEMA =
            """
            {"type":"object","properties":{"body_utf8":{"type":"string","description":"Full new body as UTF-8 text."},"body_base64":{"type":"string","description":"Full new body as standard Base64 (mutually exclusive with body_utf8)."}},"additionalProperties":false}\
            """;

    private static final String SET_HEADER_SCHEMA =
            """
            {"type":"object","properties":{"name":{"type":"string","description":"Header name (e.g. Content-Type)."},"value":{"type":"string","description":"Header value (may be empty)."}},"required":["name","value"],"additionalProperties":false}\
            """;

    private static final String REMOVE_HEADER_SCHEMA =
            """
            {"type":"object","properties":{"name":{"type":"string","description":"Header name to remove (all matching, case-insensitive)."}},"required":["name"],"additionalProperties":false}\
            """;

    private static final String SET_COOKIE_SCHEMA =
            """
            {"type":"object","properties":{"name":{"type":"string","description":"Cookie name."},"value":{"type":"string","description":"Cookie value; ignored when remove is true."},"remove":{"type":"boolean","default":false,"description":"If true, delete this cookie from the Cookie header."}},"required":["name"],"additionalProperties":false}\
            """;

    private static final String SET_METHOD_SCHEMA =
            """
            {"type":"object","properties":{"method":{"type":"string","description":"HTTP method (e.g. GET, POST)."}},"required":["method"],"additionalProperties":false}\
            """;

    private static final String SET_URL_SCHEMA =
            """
            {"type":"object","properties":{"url":{"type":"string","description":"Absolute URL including scheme and host (e.g. https://example.com/path?x=1)."}},"required":["url"],"additionalProperties":false}\
            """;

    private static final Pattern SET_COOKIE_NAME_PATTERN = Pattern.compile("^\\s*([^=;\\s]+)\\s*=");

    private HttpTargetTools() {}

    public static List<ChatToolDefinition> definitions() {
        return List.of(
                new ChatToolDefinition(
                        GET_CURRENT_HTTP_TARGET,
                        "Returns the current repeater HTTP target only (what is configured for this tab right now): "
                                + "scheme, host, port, SNI flag, method, full URL, and path, plus the same send-history "
                                + "summary as get_http_history_state under a history object (current index, prev/next, "
                                + "entries with index/time/target label). For the request line of a specific past send, "
                                + "use get_http_request_line.",
                        EMPTY_PARAMS_SCHEMA),
                new ChatToolDefinition(
                        GET_HTTP_HISTORY_STATE,
                        "Returns Treepeater send history for this tab: current index, whether older/newer entries exist, "
                                + "and a short summary per entry (index, time, target label). "
                                + "Use this before other tools to choose a valid history_index.",
                        EMPTY_PARAMS_SCHEMA),
                new ChatToolDefinition(
                        GET_HTTP_REQUEST_LINE,
                        "Returns the HTTP request first-line details for one send-history entry: method, full URL, path, "
                                + "HTTP version, and http_service (scheme, host, port, secure). "
                                + "Use history_index to select an entry (omit for the current entry).",
                        HISTORY_INDEX_ONLY_SCHEMA),
                new ChatToolDefinition(
                        LIST_HTTP_HEADER_NAMES,
                        "Lists HTTP header field names only (no values) for the request or response of one history entry.",
                        """
                        {"type":"object","properties":{"history_index":{"type":"integer","minimum":0,"description":"0-based Treepeater history index."},"side":{"type":"string","enum":[%s],"description":"Whether to read from the stored request or response."}},"required":["history_index","side"],"additionalProperties":false}\
                        """
                                .formatted(SIDE_ENUM)),
                new ChatToolDefinition(
                        GET_HTTP_HEADER,
                        "Returns all values for a header on the request or response (HTTP allows duplicate header names). "
                                + "Name matching is case-insensitive.",
                        """
                        {"type":"object","properties":{"history_index":{"type":"integer","minimum":0},"side":{"type":"string","enum":[%s]},"name":{"type":"string","description":"Header name (e.g. Content-Type)."}},"required":["history_index","side","name"],"additionalProperties":false}\
                        """
                                .formatted(SIDE_ENUM)),
                new ChatToolDefinition(
                        LIST_HTTP_COOKIES,
                        "Lists cookie names: for requests, names from the Cookie header; for responses, names from "
                                + "Set-Cookie headers.",
                        """
                        {"type":"object","properties":{"history_index":{"type":"integer","minimum":0},"side":{"type":"string","enum":[%s]}},"required":["history_index","side"],"additionalProperties":false}\
                        """
                                .formatted(SIDE_ENUM)),
                new ChatToolDefinition(
                        GET_HTTP_COOKIE,
                        "Returns one cookie by name. Request: parsed from Cookie header. Response: first Set-Cookie whose "
                                + "name matches (case-sensitive cookie name).",
                        """
                        {"type":"object","properties":{"history_index":{"type":"integer","minimum":0},"side":{"type":"string","enum":[%s]},"name":{"type":"string","description":"Cookie name."}},"required":["history_index","side","name"],"additionalProperties":false}\
                        """
                                .formatted(SIDE_ENUM)),
                new ChatToolDefinition(
                        READ_HTTP_BODY,
                        "Reads a byte range of the message body for one history entry. Large bodies must be read in "
                                + "multiple calls using offset and max_bytes (default "
                                + DEFAULT_BODY_CHUNK_BYTES
                                + ", hard cap "
                                + MAX_BODY_CHUNK_BYTES
                                + "). Returns total_bytes, UTF-8 text when the chunk is valid UTF-8, otherwise base64.",
                        """
                        {"type":"object","properties":{"history_index":{"type":"integer","minimum":0},"side":{"type":"string","enum":[%s]},"offset":{"type":"integer","minimum":0,"default":0,"description":"Byte offset into the body."},"max_bytes":{"type":"integer","minimum":1,"maximum":%d,"default":%d,"description":"Maximum body bytes to return in this call."}},"required":["history_index","side"],"additionalProperties":false}\
                        """
                                .formatted(SIDE_ENUM, MAX_BODY_CHUNK_BYTES, DEFAULT_BODY_CHUNK_BYTES)),
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
                        SET_HTTP_REQUEST_HEADER,
                        "Adds or updates one HTTP header on the **current** repeater request (live editor). "
                                + "If the header already exists, it is updated.",
                        SET_HEADER_SCHEMA),
                new ChatToolDefinition(
                        REMOVE_HTTP_REQUEST_HEADER,
                        "Removes all HTTP headers with the given name from the **current** request (case-insensitive name).",
                        REMOVE_HEADER_SCHEMA),
                new ChatToolDefinition(
                        SET_HTTP_REQUEST_COOKIE,
                        "Sets or removes one cookie in the **Cookie** header of the **current** request. "
                                + "Other cookies are preserved. Use remove=true to delete a cookie by name.",
                        SET_COOKIE_SCHEMA),
                new ChatToolDefinition(
                        SET_HTTP_REQUEST_METHOD,
                        "Sets the HTTP method of the **current** repeater request (e.g. GET, POST).",
                        SET_METHOD_SCHEMA),
                new ChatToolDefinition(
                        SET_HTTP_REQUEST_URL,
                        "Sets target and path from an **absolute** http(s) URL for the **current** request: updates "
                                + "scheme, host, port, and path (including query string).",
                        SET_URL_SCHEMA),
                new ChatToolDefinition(
                        SEND_CURRENT_HTTP_REQUEST,
                        "Sends the **current** repeater request (live editor, with target applied) and waits until the "
                                + "response is received. Updates the response pane and send history like the Send button. "
                                + "Returns only the HTTP status_code in the tool result (no body or headers).",
                        EMPTY_PARAMS_SCHEMA));
    }

    /**
     * Classifies a built-in tool by sensitivity. Unknown names return {@code null} (treat as requiring approval).
     */
    public static ToolActionLevel toolActionLevel(String toolName) {
        if (toolName == null) {
            return null;
        }
        return switch (toolName) {
            case GET_CURRENT_HTTP_TARGET,
                    GET_HTTP_HISTORY_STATE,
                    GET_HTTP_REQUEST_LINE,
                    LIST_HTTP_HEADER_NAMES,
                    GET_HTTP_HEADER,
                    LIST_HTTP_COOKIES,
                    GET_HTTP_COOKIE,
                    READ_HTTP_BODY -> ToolActionLevel.READ_ONLY;
            case REPLACE_IN_HTTP_REQUEST_BODY,
                    PATCH_HTTP_REQUEST_BODY_LINES,
                    SET_HTTP_REQUEST_BODY,
                    SET_HTTP_REQUEST_HEADER,
                    REMOVE_HTTP_REQUEST_HEADER,
                    SET_HTTP_REQUEST_COOKIE,
                    SET_HTTP_REQUEST_METHOD,
                    SET_HTTP_REQUEST_URL -> ToolActionLevel.WRITE;
            case SEND_CURRENT_HTTP_REQUEST -> ToolActionLevel.EXECUTE;
            default -> null;
        };
    }

    /**
     * Whether the chat UI should ask the user before running this tool. Read-only tools return {@code false};
     * write, execute, and unknown names return {@code true}.
     */
    public static boolean requiresUserApproval(String toolName) {
        return toolActionLevel(toolName) != ToolActionLevel.READ_ONLY;
    }

    /**
     * Dispatches built-in tools against a snapshot supplier (typically the live request editor + history).
     */
    public static String execute(String toolName, String argumentsJson, AgentToolContext ctx) {
        if (ctx == null) {
            return "{\"error\":\"no target context\"}";
        }
        JsonNode args;
        try {
            args = parseArgs(argumentsJson);
        } catch (Exception e) {
            return errorJson("invalid tool arguments JSON");
        }
        try {
            return switch (toolName) {
                case GET_CURRENT_HTTP_TARGET -> targetWithHistoryJson(ctx);
                case GET_HTTP_HISTORY_STATE -> historyStateJson(ctx);
                case GET_HTTP_REQUEST_LINE -> requestLineJson(ctx, args);
                case LIST_HTTP_HEADER_NAMES -> listHeaderNames(ctx, args);
                case GET_HTTP_HEADER -> getHeader(ctx, args);
                case LIST_HTTP_COOKIES -> listCookies(ctx, args);
                case GET_HTTP_COOKIE -> getCookie(ctx, args);
                case READ_HTTP_BODY -> readBody(ctx, args);
                case REPLACE_IN_HTTP_REQUEST_BODY -> replaceInHttpRequestBody(ctx, args);
                case PATCH_HTTP_REQUEST_BODY_LINES -> patchHttpRequestBodyLines(ctx, args);
                case SET_HTTP_REQUEST_BODY -> setHttpRequestBody(ctx, args);
                case SET_HTTP_REQUEST_HEADER -> setHttpRequestHeader(ctx, args);
                case REMOVE_HTTP_REQUEST_HEADER -> removeHttpRequestHeader(ctx, args);
                case SET_HTTP_REQUEST_COOKIE -> setHttpRequestCookie(ctx, args);
                case SET_HTTP_REQUEST_METHOD -> setHttpRequestMethod(ctx, args);
                case SET_HTTP_REQUEST_URL -> setHttpRequestUrl(ctx, args);
                case SEND_CURRENT_HTTP_REQUEST -> sendCurrentHttpRequest(ctx);
                default -> "{\"error\":\"unknown tool: " + escapeJson(toolName) + "\"}";
            };
        } catch (Exception e) {
            return errorJson(e.getMessage() != null ? e.getMessage() : "tool error");
        }
    }

    private static JsonNode parseArgs(String argumentsJson) throws JsonProcessingException {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return JSON.createObjectNode();
        }
        return JSON.readTree(argumentsJson);
    }

    /** Same payload shape as {@link #historyStateJson} (standalone and nested under {@code history} for get_current_http_target). */
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

    private static String historyStateJson(AgentToolContext ctx) {
        return write(buildHistoryStateObject(ctx));
    }

    private static String targetWithHistoryJson(AgentToolContext ctx) {
        try {
            ObjectNode n = (ObjectNode) JSON.readTree(ctx.target().toJson());
            n.set("history", buildHistoryStateObject(ctx));
            return write(n);
        } catch (Exception e) {
            return ctx.target().toJson();
        }
    }

    private static String requestLineJson(AgentToolContext ctx, JsonNode args) {
        int idx = resolveHistoryIndex(ctx, args);
        HttpRequest req = ctx.requestForHistoryIndex().apply(idx);
        if (req == null) {
            return errorJson("no request for this history index");
        }
        ObjectNode n = JSON.createObjectNode();
        n.put("history_index", idx);
        n.put("method", safeString(() -> req.method(), ""));
        n.put("url", safeString(() -> req.url(), ""));
        n.put("path", safeString(() -> req.path(), ""));
        n.put("http_version", safeString(() -> req.httpVersion(), ""));
        HttpService resolvedService = null;
        try {
            resolvedService = req.httpService();
        } catch (Exception ignored) {
        }
        final HttpService svc = resolvedService;
        if (svc != null) {
            ObjectNode s = n.putObject("http_service");
            s.put("scheme", svc.secure() ? "https" : "http");
            s.put("host", safeString(() -> svc.host(), ""));
            s.put("port", svc.port());
            s.put("secure", svc.secure());
        }
        return write(n);
    }

    private static String listHeaderNames(AgentToolContext ctx, JsonNode args) {
        int idx = resolveHistoryIndex(ctx, args);
        String side = resolveSide(args);
        List<HttpHeader> headers = headersForSide(ctx, idx, side);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        ArrayNode names = JSON.createArrayNode();
        for (HttpHeader h : headers) {
            if (h == null) {
                continue;
            }
            String name = safeString(() -> h.name(), "");
            if (name.isEmpty()) {
                continue;
            }
            String key = name.toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                names.add(name);
            }
        }
        ObjectNode out = JSON.createObjectNode();
        out.put("history_index", idx);
        out.put("side", side);
        out.set("header_names", names);
        return write(out);
    }

    private static String getHeader(AgentToolContext ctx, JsonNode args) {
        int idx = resolveHistoryIndex(ctx, args);
        String side = resolveSide(args);
        String want = requiredTextAny(args, "name", "header_name", "headerName");
        List<HttpHeader> headers = headersForSide(ctx, idx, side);
        ArrayNode values = JSON.createArrayNode();
        for (HttpHeader h : headers) {
            if (h == null) {
                continue;
            }
            String n = safeString(() -> h.name(), "");
            if (n.isEmpty()) {
                continue;
            }
            if (n.equalsIgnoreCase(want)) {
                values.add(safeString(() -> h.value(), ""));
            }
        }
        ObjectNode out = JSON.createObjectNode();
        out.put("history_index", idx);
        out.put("side", side);
        out.put("name", want);
        out.set("values", values);
        out.put("found", values.size() > 0);
        return write(out);
    }

    private static String listCookies(AgentToolContext ctx, JsonNode args) {
        int idx = resolveHistoryIndex(ctx, args);
        String side = resolveSide(args);
        ObjectNode out = JSON.createObjectNode();
        out.put("history_index", idx);
        out.put("side", side);
        ArrayNode names = out.putArray("cookie_names");
        if ("request".equals(side)) {
            HttpRequest req = ctx.requestForHistoryIndex().apply(idx);
            if (req == null) {
                return errorJson("no request for this history index");
            }
            for (String name : cookieNamesFromRequest(req)) {
                names.add(name);
            }
        } else {
            HttpResponse res = ctx.responseForHistoryIndex().apply(idx);
            if (res == null) {
                return errorJson("no response for this history index");
            }
            for (String name : cookieNamesFromResponse(res)) {
                names.add(name);
            }
        }
        return write(out);
    }

    private static String getCookie(AgentToolContext ctx, JsonNode args) {
        int idx = resolveHistoryIndex(ctx, args);
        String side = resolveSide(args);
        String want = requiredTextAny(args, "name", "cookie_name", "cookieName");
        ObjectNode out = JSON.createObjectNode();
        out.put("history_index", idx);
        out.put("side", side);
        out.put("name", want);
        if ("request".equals(side)) {
            HttpRequest req = ctx.requestForHistoryIndex().apply(idx);
            if (req == null) {
                return errorJson("no request for this history index");
            }
            String value = cookieValueFromRequest(req, want);
            out.put("found", value != null);
            if (value != null) {
                out.put("value", value);
            }
        } else {
            HttpResponse res = ctx.responseForHistoryIndex().apply(idx);
            if (res == null) {
                return errorJson("no response for this history index");
            }
            String raw = setCookieRawForName(res, want);
            out.put("found", raw != null);
            if (raw != null) {
                out.put("set_cookie_header", raw);
            }
        }
        return write(out);
    }

    private static String readBody(AgentToolContext ctx, JsonNode args) {
        int idx = resolveHistoryIndex(ctx, args);
        String side = resolveSide(args);
        int offset = readOffsetArg(args);
        int maxBytes = readMaxBytesArg(args);
        byte[] body;
        if ("request".equals(side)) {
            HttpRequest req = ctx.requestForHistoryIndex().apply(idx);
            if (req == null) {
                return errorJson("no request for this history index");
            }
            body = bytesFromByteArray(() -> req.body());
        } else {
            HttpResponse res = ctx.responseForHistoryIndex().apply(idx);
            if (res == null) {
                return errorJson("no response for this history index");
            }
            body = bytesFromByteArray(() -> res.body());
        }
        int total = body.length;
        if (offset > total) {
            offset = total;
        }
        int len = Math.min(maxBytes, total - offset);
        byte[] chunk = len <= 0 ? new byte[0] : java.util.Arrays.copyOfRange(body, offset, offset + len);

        ObjectNode out = JSON.createObjectNode();
        out.put("history_index", idx);
        out.put("side", side);
        out.put("total_bytes", total);
        out.put("offset", offset);
        out.put("returned_bytes", chunk.length);
        out.put("has_more", offset + chunk.length < total);
        out.put("next_offset", offset + chunk.length);
        String utf8 = decodeUtf8Strict(chunk);
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
        try {
            SwingUtilities.invokeAndWait(() -> applier.accept(updated));
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
    }

    private static HttpRequest withBodyBytes(HttpRequest req, byte[] body) {
        return req.withBody(ByteArray.byteArray(body));
    }

    private static String replaceInHttpRequestBody(AgentToolContext ctx, JsonNode args) {
        HttpRequest req = requireCurrentRequest(ctx);
        String oldText = argTextAny(args, "old_text", "oldText");
        if (oldText.isEmpty()) {
            return errorJson("old_text must be non-empty");
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
                return errorJson("max_replacements too large");
            }
        }

        byte[] rawBytes = bytesFromByteArray(() -> req.body());
        String text = decodeUtf8Strict(rawBytes);
        if (text == null) {
            return errorJson("request body is not valid UTF-8; use set_http_request_body with body_base64");
        }

        if (!replaceAll) {
            int occ = countNonOverlappingMatches(text, oldText);
            if (occ == 0) {
                return errorJson("old_text not found");
            }
            if (maxRep == 1 && occ > 1) {
                return errorJson("old_text is not unique; narrow the match, increase max_replacements, or use replace_all");
            }
        } else if (!text.contains(oldText)) {
            return errorJson("old_text not found");
        }

        int before = rawBytes.length;
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
        HttpRequest updated = withBodyBytes(req, outBytes);
        commitLiveRequest(ctx, updated);

        ObjectNode o = JSON.createObjectNode();
        o.put("ok", true);
        o.put("current_history_index", ctx.currentHistoryIndex());
        o.put("bytes_before", before);
        o.put("bytes_after", outBytes.length);
        o.put("replacements", replCount);
        return write(o);
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

    private static String patchHttpRequestBodyLines(AgentToolContext ctx, JsonNode args) {
        HttpRequest req = requireCurrentRequest(ctx);
        int startLine = requiredPositiveInt(args, "start_line", "startLine");
        int endLine = requiredPositiveInt(args, "end_line", "endLine");
        if (endLine < startLine) {
            return errorJson("end_line must be >= start_line");
        }
        JsonNode contentNode = argFirst(args, "content");
        if (contentNode == null || contentNode.isNull()) {
            return errorJson("missing content");
        }
        String content = contentNode.asText();

        byte[] rawBytes = bytesFromByteArray(() -> req.body());
        String text = decodeUtf8Strict(rawBytes);
        if (text == null) {
            return errorJson("request body is not valid UTF-8; use set_http_request_body with body_base64");
        }

        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\\R", -1)));
        int n = lines.size();
        if (startLine > n || endLine > n) {
            return errorJson("line range out of bounds (body has " + n + " line(s))");
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
        int before = rawBytes.length;
        HttpRequest updated = withBodyBytes(req, outBytes);
        commitLiveRequest(ctx, updated);

        ObjectNode o = JSON.createObjectNode();
        o.put("ok", true);
        o.put("current_history_index", ctx.currentHistoryIndex());
        o.put("lines_total_before", n);
        o.put("lines_replaced_span", endLine - startLine + 1);
        o.put("lines_patched_in", contentLines.size());
        o.put("bytes_before", before);
        o.put("bytes_after", outBytes.length);
        return write(o);
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

    private static String setHttpRequestBody(AgentToolContext ctx, JsonNode args) {
        HttpRequest req = requireCurrentRequest(ctx);
        JsonNode utf8Node = argFirst(args, "body_utf8", "bodyUtf8", "bodyUTF8");
        JsonNode b64Node = argFirst(args, "body_base64", "bodyBase64", "bodyB64");
        boolean hasUtf8 = utf8Node != null && !utf8Node.isNull();
        boolean hasB64 = b64Node != null && !b64Node.isNull();
        if (hasUtf8 && hasB64) {
            return errorJson("provide either body_utf8 or body_base64, not both");
        }
        if (!hasUtf8 && !hasB64) {
            return errorJson("provide body_utf8 or body_base64");
        }

        byte[] bodyBytes;
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
                    return errorJson("could not serialize body_utf8 JSON");
                }
            } else {
                return errorJson("body_utf8 must be a string, JSON object/array, or number (use body_base64 for binary)");
            }
            bodyBytes = text.getBytes(StandardCharsets.UTF_8);
        } else {
            String b64 = b64Node.asText().replaceAll("\\s+", "");
            try {
                bodyBytes = Base64.getDecoder().decode(b64);
            } catch (IllegalArgumentException e) {
                return errorJson("invalid body_base64");
            }
        }

        int before = bytesFromByteArray(() -> req.body()).length;
        HttpRequest updated = withBodyBytes(req, bodyBytes);
        commitLiveRequest(ctx, updated);

        ObjectNode o = JSON.createObjectNode();
        o.put("ok", true);
        o.put("current_history_index", ctx.currentHistoryIndex());
        o.put("bytes_before", before);
        o.put("bytes_after", bodyBytes.length);
        return write(o);
    }

    private static String setHttpRequestHeader(AgentToolContext ctx, JsonNode args) {
        HttpRequest req = requireCurrentRequest(ctx);
        String name = requiredTextAny(args, "name", "header_name", "headerName");
        String value = "";
        if (args.has("value") && !args.get("value").isNull()) {
            value = args.get("value").asText();
        }
        HttpRequest updated = req.withHeader(name, value);
        commitLiveRequest(ctx, updated);
        return okMutationJson(ctx);
    }

    private static String removeHttpRequestHeader(AgentToolContext ctx, JsonNode args) {
        HttpRequest req = requireCurrentRequest(ctx);
        String name = requiredTextAny(args, "name", "header_name", "headerName");
        HttpRequest updated = req.withRemovedHeader(name);
        commitLiveRequest(ctx, updated);
        return okMutationJson(ctx);
    }

    private static String setHttpRequestCookie(AgentToolContext ctx, JsonNode args) {
        HttpRequest req = requireCurrentRequest(ctx);
        String name = requiredTextAny(args, "name", "cookie_name", "cookieName");
        boolean remove = args.has("remove") && args.get("remove").asBoolean(false);
        String value = "";
        if (!remove) {
            JsonNode vn = argFirst(args, "value", "cookie_value", "cookieValue");
            if (vn != null && !vn.isNull()) {
                value = vn.asText();
            }
        }
        HttpRequest updated = mergeCookieHeader(req, name, value, remove);
        commitLiveRequest(ctx, updated);
        return okMutationJson(ctx);
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

    private static String setHttpRequestMethod(AgentToolContext ctx, JsonNode args) {
        HttpRequest req = requireCurrentRequest(ctx);
        String method = requiredTextAny(args, "method", "http_method", "httpMethod");
        HttpRequest updated = req.withMethod(method);
        commitLiveRequest(ctx, updated);
        return okMutationJson(ctx);
    }

    private static String setHttpRequestUrl(AgentToolContext ctx, JsonNode args) {
        HttpRequest req = requireCurrentRequest(ctx);
        String url = requiredTextAny(args, "url", "URL");
        HttpRequest updated = applyAbsoluteUrl(req, url);
        commitLiveRequest(ctx, updated);
        return okMutationJson(ctx);
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

    private static String okMutationJson(AgentToolContext ctx) {
        ObjectNode o = JSON.createObjectNode();
        o.put("ok", true);
        o.put("current_history_index", ctx.currentHistoryIndex());
        return write(o);
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

    private static List<HttpHeader> headersForSide(AgentToolContext ctx, int idx, String side) {
        if ("request".equals(side)) {
            HttpRequest req = ctx.requestForHistoryIndex().apply(idx);
            if (req == null) {
                throw new IllegalArgumentException("no request for this history index");
            }
            return safeHeaders(() -> req.headers());
        }
        HttpResponse res = ctx.responseForHistoryIndex().apply(idx);
        if (res == null) {
            throw new IllegalArgumentException("no response for this history index");
        }
        return safeHeaders(() -> res.headers());
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

    private static String decodeUtf8Strict(byte[] chunk) {
        if (chunk.length == 0) {
            return "";
        }
        CharsetDecoder dec =
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return dec.decode(ByteBuffer.wrap(chunk)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static List<String> cookieNamesFromRequest(HttpRequest req) {
        List<String> out = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (HttpHeader h : safeHeaders(() -> req.headers())) {
            if (h == null || !"cookie".equalsIgnoreCase(safeString(() -> h.name(), ""))) {
                continue;
            }
            String v = safeString(() -> h.value(), "");
            for (String name : parseCookieHeaderNames(v)) {
                if (seen.add(name.toLowerCase(Locale.ROOT))) {
                    out.add(name);
                }
            }
        }
        return out;
    }

    private static List<String> parseCookieHeaderNames(String cookieHeaderValue) {
        List<String> names = new ArrayList<>();
        if (cookieHeaderValue == null || cookieHeaderValue.isBlank()) {
            return names;
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
            if (!name.isEmpty() && !name.startsWith("$")) {
                names.add(name);
            }
        }
        return names;
    }

    private static String cookieValueFromRequest(HttpRequest req, String want) {
        String wantKey = want.toLowerCase(Locale.ROOT);
        for (HttpHeader h : safeHeaders(() -> req.headers())) {
            if (h == null || !"cookie".equalsIgnoreCase(safeString(() -> h.name(), ""))) {
                continue;
            }
            String v = safeString(() -> h.value(), "");
            for (String[] nv : parseCookiePairs(v)) {
                if (nv[0].toLowerCase(Locale.ROOT).equals(wantKey)) {
                    return nv[1];
                }
            }
        }
        return null;
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

    private static List<String> cookieNamesFromResponse(HttpResponse res) {
        List<String> out = new ArrayList<>();
        for (HttpHeader h : safeHeaders(() -> res.headers())) {
            if (h == null || !"set-cookie".equalsIgnoreCase(safeString(() -> h.name(), ""))) {
                continue;
            }
            String line = safeString(() -> h.value(), "");
            Matcher m = SET_COOKIE_NAME_PATTERN.matcher(line);
            if (m.find()) {
                out.add(m.group(1));
            }
        }
        return out;
    }

    private static String setCookieRawForName(HttpResponse res, String want) {
        for (HttpHeader h : safeHeaders(() -> res.headers())) {
            if (h == null || !"set-cookie".equalsIgnoreCase(safeString(() -> h.name(), ""))) {
                continue;
            }
            String line = safeString(() -> h.value(), "");
            Matcher m = SET_COOKIE_NAME_PATTERN.matcher(line);
            if (m.find() && m.group(1).equals(want)) {
                return line;
            }
        }
        return null;
    }

    /**
     * Models often send {@code historyIndex} or omit the field (meaning “current entry”); values may be JSON strings.
     */
    private static int resolveHistoryIndex(AgentToolContext ctx, JsonNode args) {
        JsonNode v = argFirst(args, "history_index", "historyIndex", "index", "entry_index", "entryIndex");
        if (v == null) {
            int cur = ctx.currentHistoryIndex();
            if (cur >= 0 && cur < ctx.historySize()) {
                return cur;
            }
            throw new IllegalArgumentException(
                    "missing history_index: use history_index or historyIndex in range 0.."
                            + (ctx.historySize() - 1));
        }
        int idx = jsonToInt(v);
        if (idx < 0 || idx >= ctx.historySize()) {
            throw new IllegalArgumentException(
                    "history_index out of range (0.." + (ctx.historySize() - 1) + ")");
        }
        return idx;
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

    private static String requiredTextAny(JsonNode args, String... keys) {
        JsonNode v = argFirst(args, keys);
        if (v == null) {
            throw new IllegalArgumentException("missing " + keys[0]);
        }
        String s = v.isTextual() ? v.asText().trim() : v.asText();
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("missing " + keys[0]);
        }
        return s;
    }

    private static int readOffsetArg(JsonNode args) {
        JsonNode v = argFirst(args, "offset", "byte_offset", "byteOffset", "start");
        if (v == null) {
            return 0;
        }
        return Math.max(0, jsonToInt(v));
    }

    private static int readMaxBytesArg(JsonNode args) {
        JsonNode v = argFirst(args, "max_bytes", "maxBytes", "limit", "chunk_size", "chunkSize");
        if (v == null) {
            return DEFAULT_BODY_CHUNK_BYTES;
        }
        return Math.min(MAX_BODY_CHUNK_BYTES, Math.max(1, jsonToInt(v)));
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

    /**
     * Single-line status for the UI when a tool runs, including key arguments so the transcript shows what was invoked.
     *
     * @param viewerHistoryIndex the tab's current history index, or {@link Integer#MIN_VALUE} if unknown; when equal to
     *     {@code history_index} in the tool args, the "· history #n" suffix is omitted.
     */
    public static String humanReadableUsage(String toolName, String argumentsJson, int viewerHistoryIndex) {
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
        return switch (toolName) {
            case GET_CURRENT_HTTP_TARGET -> "Getting current repeater target and send history";
            case GET_HTTP_HISTORY_STATE -> "Reading send history (indices, prev/next)";
            case GET_HTTP_REQUEST_LINE -> {
                String base = "Getting request line (method, URL, path, version)";
                yield hist.isEmpty() ? base : base + hist;
            }
            case LIST_HTTP_HEADER_NAMES -> {
                String base =
                        sideLabel.isEmpty()
                                ? "Listing header names"
                                : "Listing " + sideLabel.toLowerCase() + " header names";
                yield hist.isEmpty() ? base : base + hist;
            }
            case GET_HTTP_HEADER -> {
                String n = truncateForStatus(argTextAny(args, "name", "header_name", "headerName"), 56);
                String head = sideLabel.isEmpty() ? "Getting header" : "Getting " + sideLabel.toLowerCase() + " header";
                if (!n.isEmpty()) {
                    head += " \"" + n + "\"";
                }
                yield hist.isEmpty() ? head : head + hist;
            }
            case LIST_HTTP_COOKIES -> {
                String base =
                        sideLabel.isEmpty()
                                ? "Listing cookie names"
                                : "Listing " + sideLabel.toLowerCase() + " cookie names";
                yield hist.isEmpty() ? base : base + hist;
            }
            case GET_HTTP_COOKIE -> {
                String n = truncateForStatus(argTextAny(args, "name", "cookie_name", "cookieName"), 56);
                String head = sideLabel.isEmpty() ? "Getting cookie" : "Getting " + sideLabel.toLowerCase() + " cookie";
                if (!n.isEmpty()) {
                    head += " \"" + n + "\"";
                }
                yield hist.isEmpty() ? head : head + hist;
            }
            case READ_HTTP_BODY -> {
                String head =
                        sideLabel.isEmpty() ? "Reading message body" : "Reading " + sideLabel.toLowerCase() + " body";
                StringBuilder b = new StringBuilder(head);
                if (!hist.isEmpty()) {
                    b.append(hist);
                }
                int offset = readOffsetArg(args);
                int maxBytes = readMaxBytesArg(args);
                b.append(" · offset ").append(offset).append(", max ").append(maxBytes).append(" B");
                yield b.toString();
            }
            case REPLACE_IN_HTTP_REQUEST_BODY -> {
                String oldT = truncateForStatus(argTextAny(args, "old_text", "oldText"), 40);
                String head = "Replacing text in request body";
                if (!oldT.isEmpty()) {
                    head += " (\"" + oldT + "\")";
                }
                yield head;
            }
            case PATCH_HTTP_REQUEST_BODY_LINES -> {
                int sl = jsonToInt(argFirst(args, "start_line", "startLine"));
                int el = jsonToInt(argFirst(args, "end_line", "endLine"));
                yield "Patching request body lines " + sl + "–" + el;
            }
            case SET_HTTP_REQUEST_BODY -> "Setting full request body";
            case SET_HTTP_REQUEST_HEADER -> {
                String hn = truncateForStatus(argTextAny(args, "name", "header_name", "headerName"), 48);
                yield hn.isEmpty() ? "Setting request header" : "Setting request header \"" + hn + "\"";
            }
            case REMOVE_HTTP_REQUEST_HEADER -> {
                String hn = truncateForStatus(argTextAny(args, "name", "header_name", "headerName"), 48);
                yield hn.isEmpty() ? "Removing request header" : "Removing request header \"" + hn + "\"";
            }
            case SET_HTTP_REQUEST_COOKIE -> {
                String cn = truncateForStatus(argTextAny(args, "name", "cookie_name", "cookieName"), 40);
                boolean rem = args.has("remove") && args.get("remove").asBoolean(false);
                String head = rem ? "Removing cookie" : "Setting cookie";
                if (!cn.isEmpty()) {
                    head += " \"" + cn + "\"";
                }
                yield head;
            }
            case SET_HTTP_REQUEST_METHOD -> {
                String m = truncateForStatus(argTextAny(args, "method", "http_method", "httpMethod"), 24);
                yield m.isEmpty() ? "Setting request method" : "Setting request method " + m;
            }
            case SET_HTTP_REQUEST_URL -> {
                String u = truncateForStatus(argTextAny(args, "url", "URL"), 64);
                yield u.isEmpty() ? "Setting request URL" : "Setting request URL · " + u;
            }
            case SEND_CURRENT_HTTP_REQUEST -> "Sending current HTTP request";
            default -> "Working…";
        };
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
