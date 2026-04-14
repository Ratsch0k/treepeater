package treepeater.ai;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                                .formatted(SIDE_ENUM, MAX_BODY_CHUNK_BYTES, DEFAULT_BODY_CHUNK_BYTES)));
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
