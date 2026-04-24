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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
 * Built-in tools: HTTP target summary, raw wire read ({@value #READ_HTTP_MESSAGE}), regex search
 * ({@value #SEARCH_HTTP_MESSAGE}), and request mutation / send in Repeater.
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

    private static final String EMPTY_PARAMS_SCHEMA =
            """
            {"type":"object","properties":{},"additionalProperties":false}\
            """;

    private static final String READ_MESSAGE_SCHEMA =
            """
            {"type":"object","properties":{"side":{"type":"string","enum":["request","response"],"description":"Whether to read the stored request or response."},"history_index":{"type":"integer","minimum":0,"description":"0-based history index; omit for the current entry."},"offset":{"type":"integer","minimum":0,"default":0,"description":"Byte offset into the raw wire message."},"max_bytes":{"type":"integer","minimum":1,"maximum":65536,"default":4096,"description":"Maximum bytes to return in this call."}},"required":["side"],"additionalProperties":false}\
            """;

    private static final String SEARCH_MESSAGE_SCHEMA =
            """
            {"type":"object","properties":{"side":{"type":"string","enum":["request","response"]},"pattern":{"type":"string","description":"Java java.util.regex pattern; use inline flags (?i), (?m), (?s) as needed."},"history_index":{"type":"integer","minimum":0,"description":"0-based history index; omit for the current entry."},"scope":{"type":"string","enum":["headers","body","all"],"default":"all","description":"headers: only before CRLFCRLF; body: only after; all: full message."},"max_matches":{"type":"integer","minimum":1,"maximum":100,"default":10,"description":"Maximum matches to return."},"context_bytes":{"type":"integer","minimum":0,"maximum":512,"default":64,"description":"Context bytes on each side of each match."}},"required":["side","pattern"],"additionalProperties":false}\
            """;

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

    private HttpTargetTools() {}

    public static List<ChatToolDefinition> definitions() {
        return List.of(
                new ChatToolDefinition(
                        GET_CURRENT_HTTP_TARGET,
                        "Returns the current repeater HTTP target (what is configured for this tab right now): "
                                + "scheme, host, port, SNI flag, method, full URL, and path, plus a send-history object "
                                + "(current index, prev/next, entries with index/time/target label). "
                                + "Use read_http_message or search_http_message to inspect the raw request/response for a past send.",
                        EMPTY_PARAMS_SCHEMA),
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
            case GET_CURRENT_HTTP_TARGET, READ_HTTP_MESSAGE, SEARCH_HTTP_MESSAGE -> ToolActionLevel.READ_ONLY;
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
        String result;
        try {
            result = switch (toolName) {
                case GET_CURRENT_HTTP_TARGET -> targetWithHistoryJson(ctx);
                case READ_HTTP_MESSAGE -> readMessage(ctx, args);
                case SEARCH_HTTP_MESSAGE -> searchMessage(ctx, args);
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
        return capResult(result);
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
                        String gUtf = decodeUtf8Strict(gSlice);
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
        String utf8 = decodeUtf8Strict(slice);
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
     * Title and optional detail for the tool transcript card. Mutations that change the in-editor request include a
     * non-empty {@link HumanToolUsage#detail} describing the change, except for {@link #SET_HTTP_REQUEST_BODY} where
     * the new body is omitted.
     *
     * @param viewerHistoryIndex the tab's current history index, or {@link Integer#MIN_VALUE} if unknown; when equal to
     *     {@code history_index} in the tool args, the "· history #n" suffix is omitted.
     */
    public static HumanToolUsage humanToolUsage(String toolName, String argumentsJson, int viewerHistoryIndex) {
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
            case GET_CURRENT_HTTP_TARGET -> new HumanToolUsage("Getting current repeater target and send history", "");
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
                yield new HumanToolUsage("Replace text in request body", d.toString());
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
                yield new HumanToolUsage("Patch request body line range", det);
            }
            case SET_HTTP_REQUEST_BODY -> new HumanToolUsage("Setting full request body", "");
            case SET_HTTP_REQUEST_HEADER -> {
                String hn = argTextAny(args, "name", "header_name", "headerName");
                String value = "";
                if (args.has("value") && !args.get("value").isNull()) {
                    value = args.get("value").asText();
                }
                String det =
                        hn.isEmpty()
                                ? (value.isEmpty() ? "(name required)" : "(name required) value: " + truncateForStatus(value, 200))
                                : truncateForStatus(hn + ": " + value, 220);
                yield new HumanToolUsage("Set request header", det);
            }
            case REMOVE_HTTP_REQUEST_HEADER -> {
                String hn = truncateForStatus(argTextAny(args, "name", "header_name", "headerName"), 80);
                yield new HumanToolUsage(
                        "Remove request header",
                        hn.isEmpty() ? "(name not set)" : "Remove all \"" + hn + "\" headers");
            }
            case SET_HTTP_REQUEST_COOKIE -> {
                String cn = argTextAny(args, "name", "cookie_name", "cookieName");
                boolean rem = args.has("remove") && args.get("remove").asBoolean(false);
                String value = "";
                if (!rem) {
                    JsonNode vn = argFirst(args, "value", "cookie_value", "cookieValue");
                    if (vn != null && !vn.isNull()) {
                        value = vn.asText();
                    }
                }
                String t = rem ? "Remove cookie" : "Set cookie";
                String det =
                        rem
                                ? (cn.isEmpty()
                                        ? "Remove by name (name missing in tool args)"
                                        : "Remove \"" + truncateForStatus(cn, 80) + "\" from Cookie")
                                : (cn.isEmpty()
                                        ? (value.isEmpty() ? "Name required" : "Name required · value: " + truncateForStatus(value, 180))
                                        : truncateForStatus(cn + "=" + value, 220));
                yield new HumanToolUsage(t, det);
            }
            case SET_HTTP_REQUEST_METHOD -> {
                String m = truncateForStatus(argTextAny(args, "method", "http_method", "httpMethod"), 32);
                yield new HumanToolUsage(
                        "Set request method",
                        m.isEmpty() ? "Method not specified" : "New method: " + m);
            }
            case SET_HTTP_REQUEST_URL -> {
                String u = argTextAny(args, "url", "URL");
                String t = "Set request URL";
                yield new HumanToolUsage(t, u.isEmpty() ? "URL not specified" : truncateForStatus(u, 220));
            }
            case SEND_CURRENT_HTTP_REQUEST -> new HumanToolUsage(
                    "Send current HTTP request", "Sends the in-editor request and waits for the response (status only)");
            default -> new HumanToolUsage("Working…", "");
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
