package treepeater.api.tools.http.support;

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
import treepeater.TreepeaterModel.SiblingCopyPlacement;
import treepeater.ai.AgentToolContext;
import treepeater.ai.SearchTabRow;
import treepeater.ai.TreepeaterTabAgentBridge;
import treepeater.api.tools.http.ApplyHttpRequestSemanticChangesTool;
import treepeater.api.tools.http.PatchHttpRequestBodyLinesTool;
import treepeater.api.tools.http.ReadHttpMessageTool;
import treepeater.api.tools.http.ReplaceInHttpRequestBodyTool;
import treepeater.api.tools.http.SearchHttpMessageTool;
import treepeater.api.tools.http.SearchTabsTool;
import treepeater.api.tools.http.SetHttpRequestBodyTool;

/**
 * Business logic behind the HTTP/editor tools (read, search, mutate, semantic ops, tab listing, send). Each
 * public method here backs exactly one tool's {@code invoke()}; the tool classes themselves only resolve the
 * {@link AgentToolContext}/{@link TreepeaterTabAgentBridge} and own their schema, description, and human label.
 */
public final class HttpEditorService {
    private static final ObjectMapper JSON = new ObjectMapper();

    private HttpEditorService() {}


    public static String currentTarget(AgentToolContext ctx) {
        try {
            ObjectNode n = (ObjectNode) JSON.readTree(ctx.target().toJson());
            n.put("request_node_id", ctx.requestNodeId());
            n.set("history", buildHistoryStateObject(ctx));
            return write(n);
        } catch (Exception e) {
            return ctx.target().toJson();
        }
    }

    /** Nested under {@code history} in {@link #currentTarget}. */
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


    public static String readMessage(AgentToolContext ctx, JsonNode args) {
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
        byte[] chunk = len <= 0 ? new byte[0] : Arrays.copyOfRange(full, offset, offset + len);

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

    private static int readMaxBytesForReadMessage(JsonNode args) {
        JsonNode v = argFirst(args, "max_bytes", "maxBytes", "limit", "chunk_size", "chunkSize");
        if (v == null) {
            return ReadHttpMessageTool.DEFAULT_CHUNK_BYTES;
        }
        return Math.min(ReadHttpMessageTool.MAX_CHUNK_BYTES, Math.max(1, jsonToInt(v)));
    }


    public static String searchMessage(AgentToolContext ctx, JsonNode args) {
        JsonNode patNode = argFirst(args, "pattern", "regex", "re");
        if (patNode == null || patNode.isNull()) {
            return errorJson("missing pattern");
        }
        String patternStr = patNode.isTextual() ? patNode.asText() : patNode.toString();
        if (patternStr.length() > SearchHttpMessageTool.MAX_PATTERN_CHARS) {
            return errorJson("pattern too long (max " + SearchHttpMessageTool.MAX_PATTERN_CHARS + " characters)");
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
        if (regionLen > SearchHttpMessageTool.MAX_SCAN_BYTES) {
            scanEnd = rStart + SearchHttpMessageTool.MAX_SCAN_BYTES;
            limited = true;
        }
        int scanLen = scanEnd - rStart;
        String searchSpace =
                new String(Arrays.copyOfRange(full, rStart, rStart + scanLen), StandardCharsets.ISO_8859_1);
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
                        byte[] gSlice = Arrays.copyOfRange(full, gStart, gEnd);
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
            out.put("scan_limited_bytes", SearchHttpMessageTool.MAX_SCAN_BYTES);
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
        byte[] slice = Arrays.copyOfRange(data, start, end);
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
            return SearchHttpMessageTool.DEFAULT_MAX_MATCHES;
        }
        return Math.min(SearchHttpMessageTool.MAX_MAX_MATCHES, Math.max(1, jsonToInt(v)));
    }

    private static int readContextBytesArg(JsonNode args) {
        JsonNode v = argFirst(args, "context_bytes", "contextBytes", "context");
        if (v == null) {
            return SearchHttpMessageTool.DEFAULT_CONTEXT_BYTES;
        }
        return Math.min(SearchHttpMessageTool.MAX_CONTEXT_BYTES, Math.max(0, jsonToInt(v)));
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

        try {
            SwingUtilities.invokeAndWait(
                    () -> applier.accept(updated));
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
            if (maxRep > ReplaceInHttpRequestBodyTool.MAX_REPLACEMENTS) {
                throw new IllegalArgumentException("max_replacements too large");
            }
        }

        byte[] rawBytes = bytesFromByteArray(() -> req.body());
        int before = rawBytes.length;
        String text = Utilities.decodeUtf8Strict(rawBytes);
        if (text == null) {
            throw new IllegalArgumentException(
                    "request body is not valid UTF-8; use SetHttpRequestBodyTool.NAME with body_base64");
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

    public static String replaceInHttpRequestBody(AgentToolContext ctx, JsonNode args) {
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
                    "request body is not valid UTF-8; use SetHttpRequestBodyTool.NAME with body_base64");
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

    public static String patchHttpRequestBodyLines(AgentToolContext ctx, JsonNode args) {
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

    public static String setHttpRequestBody(AgentToolContext ctx, JsonNode args) {
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

    private static SemanticApplyResult applySemanticChangesToRequest0(HttpRequest start, JsonNode args) {
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
        if (n > ApplyHttpRequestSemanticChangesTool.MAX_OPERATIONS) {
            return new SemanticApplyResult(
                    null,
                    semanticOperationsShapeError(
                            "at most " + ApplyHttpRequestSemanticChangesTool.MAX_OPERATIONS + " operations per call",
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
        n.put("example", ApplyHttpRequestSemanticChangesTool.EXAMPLE_ARGS);
        return write(n);
    }

    public static String applyHttpRequestSemanticChanges(AgentToolContext ctx, JsonNode args) {
        SemanticApplyResult r =
                applySemanticChangesToRequest0(requireCurrentRequest(ctx), args);
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
                    "empty JSON Pointer is not allowed for type json; use SetHttpRequestBodyTool.NAME to replace the whole body",
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
                    "use SetHttpRequestBodyTool.NAME with body_base64, or fix encoding first",
                    "read raw bytes with ReadHttpMessageTool.NAME if you need to inspect the body");
        }
        JsonNode root;
        try {
            root = JSON.readTree(raw);
        } catch (JsonProcessingException e) {
            return semanticMutationError(
                    opIndex,
                    "json",
                    "request body is not valid JSON: " + (e.getOriginalMessage() != null ? e.getOriginalMessage() : e.getMessage()),
                    "use SetHttpRequestBodyTool.NAME to replace the body, or ReadHttpMessageTool.NAME to inspect it",
                    "");
        } catch (IOException e) {
            return semanticMutationError(
                    opIndex,
                    "json",
                    "request body is not valid JSON: " + (e.getMessage() != null ? e.getMessage() : e.toString()),
                    "use SetHttpRequestBodyTool.NAME to replace the body, or ReadHttpMessageTool.NAME to inspect it",
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
            throw new IllegalArgumentException("empty JSON Pointer is not allowed; use SetHttpRequestBodyTool.NAME to replace the whole body");
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
                    opIndex, "xml", "request body is not valid UTF-8 for xml mutation", "use SetHttpRequestBodyTool.NAME with body_base64 first", "read the body with ReadHttpMessageTool.NAME if needed");
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
                    "use SetHttpRequestBodyTool.NAME to replace the body, or ReadHttpMessageTool.NAME to inspect it",
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
                    opIndex, "xml", "XPath matched no nodes", "adjust path or set the body so the target exists", "use ReadHttpMessageTool.NAME to inspect the XML");
        }
        if ("set".equals(action)) {
            String v = op.get("value").asText();
            Node n = nl.item(0);
            if (n.getNodeType() == Node.ATTRIBUTE_NODE) {
                return semanticMutationError(
                        opIndex,
                        "xml",
                        "type xml set: attribute nodes are not supported in v1 (matched an attribute with XPath)",
                        "use an XPath to an element and set its text, or SetHttpRequestBodyTool.NAME to rewrite attributes",
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
    public static String sendCurrentHttpRequest(AgentToolContext ctx) throws Exception {
        Callable<Integer> sender = ctx.sendCurrentHttpRequest();
        if (sender == null) {
            return errorJson("send is unavailable in this context");
        }
        int code = sender.call();
        ObjectNode o = JSON.createObjectNode();
        o.put("status_code", code);
        return write(o);
    }


    public static String searchTabs(TreepeaterTabAgentBridge bridge, JsonNode args) {
        int offset = 0;
        JsonNode offN = argFirst(args, "offset");
        if (offN != null && offN.isNumber()) {
            offset = offN.intValue();
        }
        if (offset < 0) {
            offset = 0;
        }
        int pageSize = SearchTabsTool.DEFAULT_PAGE_SIZE;
        JsonNode psN = argFirst(args, "page_size", "pageSize");
        if (psN != null && psN.isNumber()) {
            pageSize = psN.intValue();
        }
        if (pageSize < 1) {
            pageSize = SearchTabsTool.DEFAULT_PAGE_SIZE;
        }
        pageSize = Math.min(pageSize, SearchTabsTool.MAX_PAGE_SIZE);
        String query = argTextAny(args, "query", "q", "search");
        if (query.isEmpty()) {
            query = null;
        }
        return bridge.searchTabs(offset, pageSize, query);
    }

    /** JSON body for {@link TreepeaterTabAgentBridge#searchTabs(int, int, String)}. */
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


    public static String copyTreepeaterNode(TreepeaterTabAgentBridge bridge, JsonNode args) {
        OptionalInt sourceId = parseRequestNodeId(args);
        if (sourceId.isEmpty()) {
            return errorJson("request_node_id required");
        }
        String name = argTextAny(args, "name");
        if (name.isEmpty()) {
            return errorJson("name required");
        }
        SiblingCopyPlacement placement = parseCopySiblingPlacement(args);
        if (placement == null) {
            return errorJson("placement must be after, top, or bottom");
        }
        return bridge.copyTreepeaterNode(sourceId.getAsInt(), name, placement);
    }

    private static SiblingCopyPlacement parseCopySiblingPlacement(JsonNode args) {
        String raw = argTextAny(args, "placement");
        if (raw.isEmpty()) {
            return SiblingCopyPlacement.AFTER_SOURCE;
        }
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "after" -> SiblingCopyPlacement.AFTER_SOURCE;
            case "top" -> SiblingCopyPlacement.PARENT_TOP;
            case "bottom" -> SiblingCopyPlacement.PARENT_BOTTOM;
            default -> null;
        };
    }

    /** JSON body for {@link TreepeaterTabAgentBridge#copyTreepeaterNode(int, String)}. */
    public static String formatCopyTreepeaterNodeResponse(int requestNodeId, String name) {
        ObjectNode root = JSON.createObjectNode();
        root.put("request_node_id", requestNodeId);
        root.put("name", name != null ? name : "");
        return write(root);
    }


    public static OptionalInt parseRequestNodeId(JsonNode args) {
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

    private static String argTextAny(JsonNode args, String... keys) {
        JsonNode v = argFirst(args, keys);
        if (v == null) {
            return "";
        }
        String s = v.isTextual() ? v.asText().trim() : v.asText();
        return s != null ? s.trim() : "";
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
                case ReplaceInHttpRequestBodyTool.NAME -> replaceInHttpRequestBodyOnRequest(current, args).request();
                case PatchHttpRequestBodyLinesTool.NAME -> patchHttpRequestBodyLinesOnRequest(current, args).request();
                case SetHttpRequestBodyTool.NAME -> setHttpRequestBodyOnRequest(current, args);
                case ApplyHttpRequestSemanticChangesTool.NAME -> {
                    SemanticApplyResult s = applySemanticChangesToRequest0(current, args);
                    yield s.errorResultJson() != null ? null : s.request();
                }
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonNode parseArgs(String argumentsJson) throws JsonProcessingException {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return JSON.createObjectNode();
        }
        return JSON.readTree(argumentsJson);
    }
}
