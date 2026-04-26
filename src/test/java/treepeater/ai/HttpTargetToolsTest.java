package treepeater.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Answers.RETURNS_MOCKS;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

class HttpTargetToolsTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private MockedStatic<ByteArray> byteArrayMock;
    private MockedStatic<HttpService> httpServiceMock;

    // ===== mock lifecycle =====

    @BeforeEach
    void setUpMocks() {
        // Custom answer for static ByteArray.byteArray: Mockito may pass varargs as many
        // Byte args or a single byte[]; RETURNS_MOCKS also leaves getBytes() unmapped.
        byteArrayMock = mockStatic(ByteArray.class, inv -> {
            if (!"byteArray".equals(inv.getMethod().getName())) {
                return RETURNS_MOCKS.answer(inv);
            }
            Object[] r = inv.getArguments();
            byte[] data;
            if (r.length == 1 && r[0] instanceof byte[] arr) {
                data = arr;
            } else {
                data = new byte[r.length];
                for (int i = 0; i < r.length; i++) {
                    data[i] = ((Number) r[i]).byteValue();
                }
            }
            ByteArray ba = mock(ByteArray.class);
            lenient().when(ba.getBytes()).thenReturn(data.clone());
            return ba;
        });

        httpServiceMock = mockStatic(HttpService.class);
        httpServiceMock.when(() -> HttpService.httpService(anyString(), anyInt(), anyBoolean()))
                .thenAnswer(inv -> {
                    HttpService svc = mock(HttpService.class);
                    when(svc.host()).thenReturn((String) inv.getArgument(0));
                    when(svc.port()).thenReturn((int) inv.getArgument(1));
                    when(svc.secure()).thenReturn((boolean) inv.getArgument(2));
                    return svc;
                });
    }

    @AfterEach
    void tearDownMocks() {
        byteArrayMock.close();
        httpServiceMock.close();
    }

    // ===== builder helpers =====

    private static HttpHeader hdr(String name, String value) {
        HttpHeader h = mock(HttpHeader.class);
        lenient().when(h.name()).thenReturn(name);
        lenient().when(h.value()).thenReturn(value);
        return h;
    }

    /**
     * Creates an HttpRequest mock. All with* mutation methods return a shared
     * "mutated" mock that itself chains all with* back to itself, except withBody
     * which returns a fresh mock carrying the new body.
     */
    private static HttpRequest req(String method, String url, String path,
            List<HttpHeader> headers, byte[] body) {
        HttpRequest req = mock(HttpRequest.class);
        lenient().when(req.method()).thenReturn(method);
        lenient().when(req.url()).thenReturn(url);
        lenient().when(req.path()).thenReturn(path);
        lenient().when(req.httpVersion()).thenReturn("HTTP/1.1");
        lenient().when(req.headers()).thenReturn(headers != null ? headers : List.of());

        byte[] safeBody = body != null ? body : new byte[0];
        ByteArray ba = mock(ByteArray.class);
        lenient().when(ba.getBytes()).thenReturn(safeBody.clone());
        lenient().when(req.body()).thenReturn(ba);

        stubMutations(req, ba);
        return req;
    }

    /**
     * Stubs all with* mutation methods on req. withBody returns a new mock that
     * exposes the new ByteArray via body(); all other with* methods return a shared
     * "mutated" mock that self-chains further with* calls.
     */
    private static void stubMutations(HttpRequest req, ByteArray sameBody) {
        HttpRequest mutated = mock(HttpRequest.class);
        if (sameBody != null) {
            lenient().when(mutated.body()).thenReturn(sameBody);
        }
        lenient().when(mutated.withHeader(anyString(), anyString())).thenReturn(mutated);
        lenient().when(mutated.withRemovedHeader(anyString())).thenReturn(mutated);
        lenient().when(mutated.withMethod(anyString())).thenReturn(mutated);
        lenient().when(mutated.withService(any(HttpService.class))).thenReturn(mutated);
        lenient().when(mutated.withPath(anyString())).thenReturn(mutated);
        lenient().when(mutated.withBody(any(ByteArray.class)))
                .thenAnswer(inv -> {
                    ByteArray newBa = inv.getArgument(0);
                    lenient().when(mutated.body()).thenReturn(newBa);
                    return mutated;
                });

        lenient().when(req.withHeader(anyString(), anyString())).thenReturn(mutated);
        lenient().when(req.withRemovedHeader(anyString())).thenReturn(mutated);
        lenient().when(req.withMethod(anyString())).thenReturn(mutated);
        lenient().when(req.withService(any(HttpService.class))).thenReturn(mutated);
        lenient().when(req.withPath(anyString())).thenReturn(mutated);
        lenient().when(req.withBody(any(ByteArray.class))).thenAnswer(inv -> {
            ByteArray newBa = inv.getArgument(0);
            HttpRequest updated = mock(HttpRequest.class);
            lenient().when(updated.body()).thenReturn(newBa);
            lenient().when(updated.withMethod(anyString())).thenReturn(updated);
            lenient().when(updated.withHeader(anyString(), anyString())).thenReturn(updated);
            lenient().when(updated.withRemovedHeader(anyString())).thenReturn(updated);
            lenient().when(updated.withService(any(HttpService.class))).thenReturn(updated);
            lenient().when(updated.withPath(anyString())).thenReturn(updated);
            lenient().when(updated.withBody(any(ByteArray.class)))
                    .thenAnswer(inv2 -> {
                        ByteArray b2 = inv2.getArgument(0);
                        lenient().when(updated.body()).thenReturn(b2);
                        return updated;
                    });
            return updated;
        });
    }

    private static HttpResponse res(List<HttpHeader> headers, byte[] body) {
        HttpResponse response = mock(HttpResponse.class);
        lenient().when(response.headers()).thenReturn(headers != null ? headers : List.of());
        byte[] safeBody = body != null ? body : new byte[0];
        ByteArray ba = mock(ByteArray.class);
        lenient().when(ba.getBytes()).thenReturn(safeBody.clone());
        lenient().when(response.body()).thenReturn(ba);
        return response;
    }

    private static void stubToByteArray(HttpRequest request, byte[] wire) {
        ByteArray w = mock(ByteArray.class);
        lenient().when(w.getBytes()).thenReturn(wire.clone());
        lenient().when(request.toByteArray()).thenReturn(w);
    }

    private static void stubToByteArray(HttpResponse response, byte[] wire) {
        ByteArray w = mock(ByteArray.class);
        lenient().when(w.getBytes()).thenReturn(wire.clone());
        lenient().when(response.toByteArray()).thenReturn(w);
    }

    private static HttpTargetSnapshot defaultTarget() {
        return new HttpTargetSnapshot("https", "example.com", 443, true,
                "GET", "https://example.com/path", "/path");
    }

    private static AgentToolContext singleEntryCtx(HttpRequest request, HttpResponse response,
            Consumer<HttpRequest> applier) {
        return new AgentToolContext(
                defaultTarget(), 0,
                List.of(new AgentToolContext.HistoryEntryInfo(0, "12:00:00", "GET example.com")),
                idx -> request,
                idx -> response,
                applier,
                null);
    }

    private static AgentToolContext singleEntryCtx(HttpRequest request, HttpResponse response) {
        return singleEntryCtx(request, response, req -> {});
    }

    private static JsonNode parse(String json) throws Exception {
        return JSON.readTree(json);
    }

    // ===== toolActionLevel =====

    @Test
    void toolActionLevel_readOnlyTools() {
        assertEquals(ToolActionLevel.READ_ONLY, HttpTargetTools.toolActionLevel(HttpTargetTools.GET_CURRENT_HTTP_TARGET));
        assertEquals(ToolActionLevel.READ_ONLY, HttpTargetTools.toolActionLevel(HttpTargetTools.READ_HTTP_MESSAGE));
        assertEquals(ToolActionLevel.READ_ONLY, HttpTargetTools.toolActionLevel(HttpTargetTools.SEARCH_HTTP_MESSAGE));
    }

    @Test
    void toolActionLevel_writeTools() {
        assertEquals(ToolActionLevel.WRITE, HttpTargetTools.toolActionLevel(HttpTargetTools.REPLACE_IN_HTTP_REQUEST_BODY));
        assertEquals(ToolActionLevel.WRITE, HttpTargetTools.toolActionLevel(HttpTargetTools.PATCH_HTTP_REQUEST_BODY_LINES));
        assertEquals(ToolActionLevel.WRITE, HttpTargetTools.toolActionLevel(HttpTargetTools.SET_HTTP_REQUEST_BODY));
        assertEquals(ToolActionLevel.WRITE, HttpTargetTools.toolActionLevel(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES));
    }

    @Test
    void toolActionLevel_executeTools() {
        assertEquals(ToolActionLevel.EXECUTE, HttpTargetTools.toolActionLevel(HttpTargetTools.SEND_CURRENT_HTTP_REQUEST));
    }

    @Test
    void toolActionLevel_nullAndUnknown() {
        assertNull(HttpTargetTools.toolActionLevel(null));
        assertNull(HttpTargetTools.toolActionLevel("unknown_tool_xyz"));
    }

    // ===== requiresUserApprovalInAgentMode =====

    @Test
    void approval_askMode_readToolsNeedNoApproval() {
        assertFalse(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.READ_HTTP_MESSAGE, AgentMode.ASK));
        assertFalse(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.SEARCH_HTTP_MESSAGE, AgentMode.ASK));
    }

    @Test
    void approval_askMode_writeAndExecuteNeedApproval() {
        assertTrue(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.SET_HTTP_REQUEST_BODY, AgentMode.ASK));
        assertTrue(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.SEND_CURRENT_HTTP_REQUEST, AgentMode.ASK));
    }

    @Test
    void approval_helperMode_onlyExecuteNeedsApproval() {
        assertFalse(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.GET_CURRENT_HTTP_TARGET, AgentMode.HELPER));
        assertFalse(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.SET_HTTP_REQUEST_BODY, AgentMode.HELPER));
        assertTrue(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.SEND_CURRENT_HTTP_REQUEST, AgentMode.HELPER));
    }

    @Test
    void approval_autonomousMode_nothingNeedsApproval() {
        assertFalse(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.SEARCH_HTTP_MESSAGE, AgentMode.AUTONOMOUS));
        assertFalse(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.SET_HTTP_REQUEST_BODY, AgentMode.AUTONOMOUS));
        assertFalse(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.SEND_CURRENT_HTTP_REQUEST, AgentMode.AUTONOMOUS));
    }

    @Test
    void approval_unknownTool_requiresApprovalExceptAutonomous() {
        assertTrue(HttpTargetTools.requiresUserApprovalInAgentMode("no_such_tool", AgentMode.ASK));
        assertTrue(HttpTargetTools.requiresUserApprovalInAgentMode("no_such_tool", AgentMode.HELPER));
        assertFalse(HttpTargetTools.requiresUserApprovalInAgentMode("no_such_tool", AgentMode.AUTONOMOUS));
    }

    @Test
    void approval_nullMode_treatedAsAsk() {
        assertFalse(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.GET_CURRENT_HTTP_TARGET, null));
        assertTrue(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.SET_HTTP_REQUEST_BODY, null));
        assertTrue(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.SEND_CURRENT_HTTP_REQUEST, null));
    }

    // ===== definitions =====

    @Test
    void definitions_returnsAllBuiltInTools() {
        assertEquals(8, HttpTargetTools.definitions().size());
    }

    // ===== result cap =====

    @Test
    void execute_cappedWhenResultExceedsLimit() throws Exception {
        String prefix = "GET / HTTP/1.1\r\nHost: x\r\n\r\n";
        String body = "A".repeat(200_000);
        byte[] wire = (prefix + body).getBytes(StandardCharsets.ISO_8859_1);
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), body.getBytes(StandardCharsets.UTF_8));
        stubToByteArray(request, wire);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(
                HttpTargetTools.execute(
                        HttpTargetTools.SEARCH_HTTP_MESSAGE,
                        "{\"side\":\"request\",\"scope\":\"body\",\"pattern\":\"(A{200000})\"}", ctx));

        assertEquals("tool_result_too_large", result.get("error").asText());
        assertTrue(result.has("result_chars"));
        assertTrue(result.has("max_result_chars"));
        assertTrue(result.get("result_chars").asInt() > result.get("max_result_chars").asInt());
        assertTrue(result.has("hint"), "cap response should point the model at paginated alternatives");
    }

    @Test
    void definitions_allHaveNonEmptyNamesAndDescriptions() {
        for (ChatToolDefinition def : HttpTargetTools.definitions()) {
            assertFalse(def.name().isBlank(), "tool name should not be blank");
            assertFalse(def.description().isBlank(), "description should not be blank for: " + def.name());
            assertFalse(def.parametersJsonSchema().isBlank(), "schema should not be blank for: " + def.name());
        }
    }

    // ===== execute – error cases =====

    @Test
    void execute_nullContext_returnsError() throws Exception {
        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.GET_CURRENT_HTTP_TARGET, "{}", null));
        assertTrue(result.has("error"), "expected error field");
    }

    @Test
    void execute_invalidJsonArgs_returnsError() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.GET_CURRENT_HTTP_TARGET, "NOT{JSON", ctx));
        assertTrue(result.has("error"));
    }

    @Test
    void execute_unknownTool_returnsErrorWithToolName() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        JsonNode result = parse(HttpTargetTools.execute("no_such_tool", "{}", ctx));
        assertTrue(result.has("error"));
        assertTrue(result.get("error").asText().contains("no_such_tool"));
    }

    // ===== get_current_http_target =====

    @Test
    void getCurrentHttpTarget_returnsTargetFieldsAndHistoryObject() throws Exception {
        HttpRequest request = req("GET", "https://example.com/path", "/path", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.GET_CURRENT_HTTP_TARGET, "{}", ctx));

        assertEquals("https", result.get("scheme").asText());
        assertEquals("example.com", result.get("host").asText());
        assertEquals(443, result.get("port").asInt());
        assertTrue(result.get("sniEnabled").asBoolean());
        assertTrue(result.has("history"));
        assertEquals(1, result.get("history").get("entry_count").asInt());
        assertEquals(0, result.get("history").get("current_history_index").asInt());
    }

    // ===== read_http_message & search_http_message =====

    @Test
    void readHttpMessage_missingSide_returnsError() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        stubToByteArray(request, "GET /x HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
        AgentToolContext ctx = singleEntryCtx(request, null);
        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.READ_HTTP_MESSAGE, "{}", ctx));
        assertTrue(result.has("error"));
    }

    @Test
    void readHttpMessage_requestHeaderBytesAndBodySlice() throws Exception {
        String head = "GET /p HTTP/1.1\r\nHost: z\r\nContent-Length: 5\r\n\r\n";
        String fullStr = head + "HELLO";
        byte[] full = fullStr.getBytes(StandardCharsets.ISO_8859_1);
        HttpRequest request = req("GET", "https://x.com/p", "/p", List.of(), "HELLO".getBytes(StandardCharsets.UTF_8));
        stubToByteArray(request, full);
        AgentToolContext ctx = singleEntryCtx(request, null);
        int hb = head.length();

        JsonNode start = parse(
                HttpTargetTools.execute(
                        HttpTargetTools.READ_HTTP_MESSAGE, "{\"side\":\"request\",\"max_bytes\":" + full.length + "}", ctx));
        assertEquals("request", start.get("side").asText());
        assertEquals(full.length, start.get("total_bytes").asInt());
        assertEquals(hb, start.get("header_bytes").asInt());
        assertTrue(start.get("text").asText().startsWith("GET /p"));
        assertEquals(fullStr, start.get("text").asText());

        JsonNode body = parse(
                HttpTargetTools.execute(
                        HttpTargetTools.READ_HTTP_MESSAGE,
                        "{\"side\":\"request\",\"offset\":" + hb + "}",
                        ctx));
        assertEquals("HELLO", body.get("text").asText());
        assertEquals(hb, body.get("offset").asInt());
    }

    @Test
    void readHttpMessage_resAlias() throws Exception {
        String s = "HTTP/1.1 200 OK\r\nX:1\r\n\r\n";
        byte[] w = s.getBytes(StandardCharsets.ISO_8859_1);
        HttpResponse res = res(List.of(), new byte[0]);
        HttpRequest reqR = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        stubToByteArray(res, w);
        AgentToolContext ctx = singleEntryCtx(reqR, res);
        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.READ_HTTP_MESSAGE, "{\"side\":\"res\"}", ctx));
        assertFalse(result.has("error"));
        assertTrue(result.get("text").asText().startsWith("HTTP/1.1 200"));
    }

    @Test
    void readHttpMessage_clampsMaxBytes() throws Exception {
        final int maxB = 65_536;
        byte[] body = "A".repeat(2 * maxB).getBytes(StandardCharsets.ISO_8859_1);
        String p = "GET / HTTP/1.1\r\nHost: a\r\n\r\n";
        byte[] full = (p + new String(body, StandardCharsets.ISO_8859_1)).getBytes(StandardCharsets.ISO_8859_1);
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), body);
        stubToByteArray(request, full);
        AgentToolContext ctx = singleEntryCtx(request, null);
        JsonNode r = parse(
                HttpTargetTools.execute(
                        HttpTargetTools.READ_HTTP_MESSAGE,
                        "{\"side\":\"request\",\"offset\":" + p.length() + ",\"max_bytes\":9999999999}",
                        ctx));
        assertEquals(p.length() + 2 * maxB, r.get("total_bytes").asInt());
        assertEquals(maxB, r.get("returned_bytes").asInt());
    }

    @Test
    void readHttpMessage_binaryChunkUsesBase64() throws Exception {
        String head = "POST /b HTTP/1.1\r\nX: y\r\n\r\n";
        byte[] body = new byte[] {(byte) 0xFF, 0x00, 0x01};
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        bos.writeBytes(head.getBytes(StandardCharsets.ISO_8859_1));
        bos.writeBytes(body);
        byte[] full = bos.toByteArray();
        HttpRequest request = req("POST", "https://x.com/b", "/b", List.of(), body);
        stubToByteArray(request, full);
        AgentToolContext ctx = singleEntryCtx(request, null);
        int hb = firstDoubleCrlfEnd(full);
        JsonNode r = parse(
                HttpTargetTools.execute(
                        HttpTargetTools.READ_HTTP_MESSAGE,
                        "{\"side\":\"request\",\"offset\":" + hb + "}",
                        ctx));
        assertEquals("base64", r.get("encoding").asText());
        assertArrayEquals(body, Base64.getDecoder().decode(r.get("base64").asText()));
    }

    private static int firstDoubleCrlfEnd(byte[] d) {
        for (int i = 0; i + 3 < d.length; i++) {
            if (d[i] == '\r' && d[i + 1] == '\n' && d[i + 2] == '\r' && d[i + 3] == '\n') {
                return i + 4;
            }
        }
        return d.length;
    }

    @Test
    void searchHostHeaderCapturesValueAndOffsets() throws Exception {
        String wStr = "GET / HTTP/1.1\r\nHost: example.org\r\n\r\n";
        byte[] w = wStr.getBytes(StandardCharsets.ISO_8859_1);
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        stubToByteArray(request, w);
        AgentToolContext ctx = singleEntryCtx(request, null);
        // JSON needs \\ before s so the pattern string contains the regex \\s (whitespace), not invalid JSON \\s
        String raw = HttpTargetTools.execute(
                HttpTargetTools.SEARCH_HTTP_MESSAGE,
                "{\"side\":\"request\",\"pattern\":\"(?im)^Host:\\\\s*(.+)$\"}",
                ctx);
        JsonNode r = parse(raw);
        if (r.has("error")) {
            fail("Unexpected tool error: " + r.get("error").asText() + " raw=" + raw);
        }
        assertEquals(1, r.get("match_count").asInt());
        JsonNode m0 = r.get("matches").get(0);
        assertEquals(1, m0.get("groups").size());
        assertEquals("example.org", m0.get("groups").get(0).asText());
        int matchStart = m0.get("start").asInt();
        assertTrue(matchStart >= 0);
        assertTrue(wStr.substring(matchStart, m0.get("end").asInt()).contains("Host:"));
    }

    @Test
    void searchScopeHeadersExcludesDecoyInBody() throws Exception {
        String wStr = "GET / HTTP/1.1\r\nHost: real\r\n\r\nThis line has Host: decoy in it\r\n";
        byte[] w = wStr.getBytes(StandardCharsets.ISO_8859_1);
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        stubToByteArray(request, w);
        AgentToolContext ctx = singleEntryCtx(request, null);
        JsonNode all = parse(
                HttpTargetTools.execute(
                        HttpTargetTools.SEARCH_HTTP_MESSAGE, "{\"side\":\"request\",\"pattern\":\"Host:\",\"scope\":\"all\"}", ctx));
        assertTrue(all.get("match_count").asInt() > 1);
        JsonNode hdr = parse(
                HttpTargetTools.execute(
                        HttpTargetTools.SEARCH_HTTP_MESSAGE, "{\"side\":\"request\",\"pattern\":\"Host:\",\"scope\":\"headers\"}", ctx));
        assertEquals(1, hdr.get("match_count").asInt());
    }

    @Test
    void searchScopeBodyFindsTokenLiteral() throws Exception {
        String wStr = "HTTP/1.1 200 OK\r\n\r\n{\"csrf\":\"tok456\"}";
        byte[] w = wStr.getBytes(StandardCharsets.UTF_8);
        HttpResponse res = res(List.of(), new byte[0]);
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        stubToByteArray(res, w);
        AgentToolContext ctx = singleEntryCtx(request, res);
        JsonNode r = parse(
                HttpTargetTools.execute(
                        HttpTargetTools.SEARCH_HTTP_MESSAGE,
                        "{\"side\":\"response\",\"scope\":\"body\",\"pattern\":\"tok456\"}",
                        ctx));
        assertEquals(1, r.get("match_count").asInt());
    }

    @Test
    void searchMaxMatchesTruncates() throws Exception {
        String wStr = "GET / HTTP/1.1\r\n\r\n" + "a".repeat(8);
        byte[] w = wStr.getBytes(StandardCharsets.ISO_8859_1);
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), "aaaaaaaa".getBytes(StandardCharsets.ISO_8859_1));
        stubToByteArray(request, w);
        AgentToolContext ctx = singleEntryCtx(request, null);
        JsonNode r = parse(
                HttpTargetTools.execute(
                        HttpTargetTools.SEARCH_HTTP_MESSAGE,
                        "{\"side\":\"request\",\"scope\":\"body\",\"pattern\":\"a\",\"max_matches\":2}",
                        ctx));
        assertEquals(2, r.get("match_count").asInt());
        assertTrue(r.get("truncated").asBoolean());
        assertTrue(r.get("total_matches_in_scan").asInt() > 2);
    }

    @Test
    void searchContextBytesPresent() throws Exception {
        String wStr = "GET /x HTTP/1.1\r\n\r\n0123456789abcdefghij";
        byte[] w = wStr.getBytes(StandardCharsets.ISO_8859_1);
        HttpRequest request = req("GET", "https://x.com/x", "/x", List.of(), "0123456789abcdefghij".getBytes(StandardCharsets.ISO_8859_1));
        stubToByteArray(request, w);
        AgentToolContext ctx = singleEntryCtx(request, null);
        JsonNode r = parse(
                HttpTargetTools.execute(
                        HttpTargetTools.SEARCH_HTTP_MESSAGE,
                        "{\"side\":\"request\",\"scope\":\"body\",\"pattern\":\"ghij\",\"context_bytes\":3}",
                        ctx));
        String ctxBefore = r.get("matches").get(0).get("context_before").asText();
        String ctxAfter = r.get("matches").get(0).get("context_after").asText();
        assertTrue(ctxBefore.contains("def"));
        assertTrue(ctxAfter.isEmpty() || !ctxAfter.contains("Z"));
    }

    @Test
    void searchInvalidRegex() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        stubToByteArray(request, "GET / HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
        AgentToolContext ctx = singleEntryCtx(request, null);
        JsonNode r = parse(
                HttpTargetTools.execute(
                        HttpTargetTools.SEARCH_HTTP_MESSAGE, "{\"side\":\"request\",\"pattern\":\"(\"}", ctx));
        assertTrue(r.get("error").asText().contains("regex") || r.get("error").asText().contains("Unclosed"));
    }

    @Test
    void searchPatternTooLong() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        stubToByteArray(request, "A".getBytes(StandardCharsets.ISO_8859_1));
        AgentToolContext ctx = singleEntryCtx(request, null);
        String p = "x".repeat(1025);
        String args = "{\"side\":\"request\",\"pattern\":" + JSON.writeValueAsString(p) + "}";
        JsonNode r = parse(HttpTargetTools.execute(HttpTargetTools.SEARCH_HTTP_MESSAGE, args, ctx));
        assertTrue(r.get("error").asText().toLowerCase().contains("pattern"));
    }

    @Test
    void searchHugeBodyIsScanLimited() throws Exception {
        String twoMbBody = "B".repeat(2 * 1024 * 1024);
        String head = "GET / HTTP/1.1\r\nHost: x\r\n\r\n";
        byte[] w = (head + twoMbBody).getBytes(StandardCharsets.ISO_8859_1);
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), twoMbBody.getBytes(StandardCharsets.ISO_8859_1));
        stubToByteArray(request, w);
        AgentToolContext ctx = singleEntryCtx(request, null);
        long t0 = System.nanoTime();
        JsonNode r = parse(
                HttpTargetTools.execute(
                        HttpTargetTools.SEARCH_HTTP_MESSAGE, "{\"side\":\"request\",\"scope\":\"body\",\"pattern\":\"NOMATCH\"}", ctx));
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        assertTrue(ms < 10_000, "search should not hang, took " + ms + "ms");
        assertEquals(0, r.get("match_count").asInt());
        assertTrue(r.has("scan_limited_bytes"), "2MB body scope should be capped per scan_limited_bytes");
    }

    @Test
    void searchBinaryMatchReturnsBase64InMatch() throws Exception {
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        b.write("GET / HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
        b.write(0xFF);
        b.write(0xFF);
        byte[] w = b.toByteArray();
        byte[] onlyBody = new byte[] {(byte) 0xFF, (byte) 0xFF};
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), onlyBody);
        stubToByteArray(request, w);
        AgentToolContext ctx = singleEntryCtx(request, null);
        JsonNode r = parse(
                HttpTargetTools.execute(
                        HttpTargetTools.SEARCH_HTTP_MESSAGE, "{\"side\":\"request\",\"scope\":\"body\",\"pattern\":\".\"}", ctx));
        assertTrue(r.get("matches").get(0).has("match_base64"));
    }

    // ===== replace_in_http_request_body =====

    @Test
    void replaceInHttpRequestBody_singleReplacement() throws Exception {
        byte[] body = "hello world".getBytes(StandardCharsets.UTF_8);
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.REPLACE_IN_HTTP_REQUEST_BODY,
                "{\"old_text\":\"hello\",\"new_text\":\"goodbye\"}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertEquals(11, result.get("bytes_before").asInt());
        assertEquals("goodbye world".getBytes(StandardCharsets.UTF_8).length,
                result.get("bytes_after").asInt());
        assertEquals(1, result.get("replacements").asInt());
        assertNotNull(applied.get(), "applyLiveRequest should have been called");
    }

    @Test
    void replaceInHttpRequestBody_oldTextNotFound_returnsError() throws Exception {
        byte[] body = "hello world".getBytes(StandardCharsets.UTF_8);
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.REPLACE_IN_HTTP_REQUEST_BODY,
                "{\"old_text\":\"notpresent\",\"new_text\":\"x\"}", ctx));

        assertTrue(result.has("error"));
        assertTrue(result.get("error").asText().contains("not found"));
    }

    @Test
    void replaceInHttpRequestBody_notUnique_returnsError() throws Exception {
        byte[] body = "aa bb aa".getBytes(StandardCharsets.UTF_8);
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.REPLACE_IN_HTTP_REQUEST_BODY,
                "{\"old_text\":\"aa\",\"new_text\":\"cc\"}", ctx));

        assertTrue(result.has("error"));
        assertTrue(result.get("error").asText().contains("not unique"));
    }

    @Test
    void replaceInHttpRequestBody_replaceAll_replacesEveryOccurrence() throws Exception {
        byte[] body = "aa bb aa cc aa".getBytes(StandardCharsets.UTF_8);
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.REPLACE_IN_HTTP_REQUEST_BODY,
                "{\"old_text\":\"aa\",\"new_text\":\"zz\",\"replace_all\":true}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertEquals(3, result.get("replacements").asInt());
        assertNotNull(applied.get());
    }

    @Test
    void replaceInHttpRequestBody_maxReplacements_limitsCount() throws Exception {
        byte[] body = "aa bb aa cc aa".getBytes(StandardCharsets.UTF_8);
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.REPLACE_IN_HTTP_REQUEST_BODY,
                "{\"old_text\":\"aa\",\"new_text\":\"zz\",\"max_replacements\":2}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertEquals(2, result.get("replacements").asInt());
    }

    @Test
    void replaceInHttpRequestBody_binaryBody_returnsError() throws Exception {
        byte[] body = new byte[]{(byte) 0xFF, (byte) 0xFE, 0x00};
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.REPLACE_IN_HTTP_REQUEST_BODY,
                "{\"old_text\":\"x\",\"new_text\":\"y\"}", ctx));

        assertTrue(result.has("error"));
    }

    @Test
    void replaceInHttpRequestBody_deleteOccurrence_newTextEmpty() throws Exception {
        byte[] body = "remove this text".getBytes(StandardCharsets.UTF_8);
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.REPLACE_IN_HTTP_REQUEST_BODY,
                "{\"old_text\":\" this\",\"new_text\":\"\"}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertEquals(1, result.get("replacements").asInt());
        assertTrue(result.get("bytes_after").asInt() < result.get("bytes_before").asInt());
    }

    // ===== patch_http_request_body_lines =====

    @Test
    void patchHttpRequestBodyLines_replacesMiddleLines() throws Exception {
        byte[] body = "line1\nline2\nline3".getBytes(StandardCharsets.UTF_8);
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.PATCH_HTTP_REQUEST_BODY_LINES,
                "{\"start_line\":2,\"end_line\":2,\"content\":\"replaced line\"}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertEquals(3, result.get("lines_total_before").asInt());
        assertEquals(1, result.get("lines_replaced_span").asInt());
        assertEquals(1, result.get("lines_patched_in").asInt());
        assertNotNull(applied.get());
    }

    @Test
    void patchHttpRequestBodyLines_replacesMultipleLines() throws Exception {
        byte[] body = "a\nb\nc\nd".getBytes(StandardCharsets.UTF_8);
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.PATCH_HTTP_REQUEST_BODY_LINES,
                "{\"start_line\":2,\"end_line\":3,\"content\":\"x\\ny\\nz\"}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertEquals(2, result.get("lines_replaced_span").asInt());
        assertEquals(3, result.get("lines_patched_in").asInt());
    }

    @Test
    void patchHttpRequestBodyLines_outOfBounds_returnsError() throws Exception {
        byte[] body = "line1\nline2".getBytes(StandardCharsets.UTF_8);
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.PATCH_HTTP_REQUEST_BODY_LINES,
                "{\"start_line\":1,\"end_line\":5,\"content\":\"x\"}", ctx));

        assertTrue(result.has("error"));
        assertTrue(result.get("error").asText().contains("out of bounds"));
    }

    @Test
    void patchHttpRequestBodyLines_endBeforeStart_returnsError() throws Exception {
        byte[] body = "line1\nline2".getBytes(StandardCharsets.UTF_8);
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.PATCH_HTTP_REQUEST_BODY_LINES,
                "{\"start_line\":3,\"end_line\":1,\"content\":\"x\"}", ctx));

        assertTrue(result.has("error"));
    }

    // ===== set_http_request_body =====

    @Test
    void setHttpRequestBody_utf8Text() throws Exception {
        byte[] original = "old body".getBytes(StandardCharsets.UTF_8);
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), original);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.SET_HTTP_REQUEST_BODY,
                "{\"body_utf8\":\"new body\"}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertEquals(8, result.get("bytes_before").asInt());
        assertEquals(8, result.get("bytes_after").asInt());
        assertNotNull(applied.get());
    }

    @Test
    void setHttpRequestBody_base64() throws Exception {
        byte[] original = new byte[0];
        byte[] newBody = new byte[]{1, 2, 3, 4, 5};
        String encoded = Base64.getEncoder().encodeToString(newBody);
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), original);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.SET_HTTP_REQUEST_BODY,
                "{\"body_base64\":\"" + encoded + "\"}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertEquals(0, result.get("bytes_before").asInt());
        assertEquals(5, result.get("bytes_after").asInt());
        assertNotNull(applied.get());
    }

    @Test
    void setHttpRequestBody_jsonObjectBody_serializedToCompactJson() throws Exception {
        byte[] original = new byte[0];
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), original);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.SET_HTTP_REQUEST_BODY,
                "{\"body_utf8\":{\"key\":\"value\"}}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertTrue(result.get("bytes_after").asInt() > 0);
    }

    @Test
    void setHttpRequestBody_bothPresent_returnsError() throws Exception {
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.SET_HTTP_REQUEST_BODY,
                "{\"body_utf8\":\"text\",\"body_base64\":\"AA==\"}", ctx));

        assertTrue(result.has("error"));
    }

    @Test
    void setHttpRequestBody_neitherPresent_returnsError() throws Exception {
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.SET_HTTP_REQUEST_BODY, "{}", ctx));

        assertTrue(result.has("error"));
    }

    // ===== apply_http_request_semantic_changes =====

    @Test
    void applySemantic_setHeader_invokesWithHeaderAndCommits() throws Exception {
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);
        String args =
                "{\"operations\":[{\"type\":\"header\",\"action\":\"set\",\"key\":\"X-Custom\",\"value\":\"hello\"}]}";

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES, args, ctx));

        assertTrue(result.get("ok").asBoolean());
        assertEquals(1, result.get("operations_applied").asInt());
        assertNotNull(applied.get());
        verify(request).withHeader("X-Custom", "hello");
    }

    @Test
    void applySemantic_setHeader_emptyKey_returnsError() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        String args = "{\"operations\":[{\"type\":\"header\",\"action\":\"set\",\"value\":\"hello\"}]}";

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES, args, ctx));

        assertTrue(result.has("error"));
        assertEquals(0, result.get("op_index").asInt());
    }

    @Test
    void applySemantic_removeHeader_invokesWithRemovedHeaderAndCommits() throws Exception {
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        List<HttpHeader> headers = List.of(hdr("X-Remove-Me", "value"));
        HttpRequest request = req("GET", "https://x.com/", "/", headers, new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);
        String args = "{\"operations\":[{\"type\":\"header\",\"action\":\"remove\",\"key\":\"X-Remove-Me\"}]}";

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES, args, ctx));

        assertTrue(result.get("ok").asBoolean());
        assertNotNull(applied.get());
        verify(request).withRemovedHeader("X-Remove-Me");
    }

    @Test
    void applySemantic_cookie_addsAlongsideExisting() throws Exception {
        List<HttpHeader> headers = List.of(hdr("Cookie", "session=abc"));
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("GET", "https://x.com/", "/", headers, new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);
        String args = "{\"operations\":[{\"type\":\"cookie\",\"action\":\"set\",\"key\":\"token\",\"value\":\"xyz\"}]}";

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES, args, ctx));

        assertTrue(result.get("ok").asBoolean());
        assertNotNull(applied.get());
        verify(request).withRemovedHeader("Cookie");
    }

    @Test
    void applySemantic_cookie_removeOne() throws Exception {
        List<HttpHeader> headers = List.of(hdr("Cookie", "session=abc; token=xyz"));
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("GET", "https://x.com/", "/", headers, new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);
        String args = "{\"operations\":[{\"type\":\"cookie\",\"action\":\"remove\",\"key\":\"session\"}]}";

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES, args, ctx));

        assertTrue(result.get("ok").asBoolean());
        assertNotNull(applied.get());
    }

    @Test
    void applySemantic_method_setCommits() throws Exception {
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);
        String args =
                "{\"operations\":[{\"type\":\"method\",\"action\":\"set\",\"key\":\"\",\"value\":\"POST\"}]}";

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES, args, ctx));

        assertTrue(result.get("ok").asBoolean());
        assertNotNull(applied.get());
        verify(request).withMethod("POST");
    }

    @Test
    void applySemantic_url_parsesHostPortPathAndCommits() throws Exception {
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("GET", "https://old.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);
        String args = "{\"operations\":[{\"type\":\"url\",\"action\":\"set\",\"key\":\"\","
                + "\"value\":\"https://new.com:8443/api/v1?q=test\"}]}";

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES, args, ctx));

        assertTrue(result.get("ok").asBoolean());
        assertNotNull(applied.get());
        httpServiceMock.verify(() -> HttpService.httpService("new.com", 8443, true));
        verify(request).withService(any(HttpService.class));
    }

    @Test
    void applySemantic_url_httpDefaultPort80() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null, req -> {});
        String args =
                "{\"operations\":[{\"type\":\"url\",\"action\":\"set\",\"key\":\"\",\"value\":\"http://example.com/path\"}]}";

        parse(HttpTargetTools.execute(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES, args, ctx));

        httpServiceMock.verify(() -> HttpService.httpService("example.com", 80, false));
    }

    @Test
    void applySemantic_url_noScheme_returnsError() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        String args =
                "{\"operations\":[{\"type\":\"url\",\"action\":\"set\",\"key\":\"\",\"value\":\"//example.com/path\"}]}";

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES, args, ctx));

        assertTrue(result.has("error"));
    }

    @Test
    void applySemantic_url_nonHttpScheme_returnsError() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        String args =
                "{\"operations\":[{\"type\":\"url\",\"action\":\"set\",\"key\":\"\",\"value\":\"ftp://example.com/file\"}]}";

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES, args, ctx));

        assertTrue(result.has("error"));
    }

    @Test
    void applySemantic_url_invalidUrl_returnsError() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        String args =
                "{\"operations\":[{\"type\":\"url\",\"action\":\"set\",\"key\":\"\",\"value\":\"https://\"}]}";

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES, args, ctx));

        assertTrue(result.has("error"));
    }

    @Test
    void applySemantic_happyPath_headerJsonMethod() throws Exception {
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        byte[] startBody = "{\"a\":1,\"b\":2}".getBytes(StandardCharsets.UTF_8);
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), startBody);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);
        String args = "{"
                + "\"operations\": ["
                + "  {\"type\": \"header\", \"action\": \"set\", \"key\": \"X-Test\", \"value\": \"1\"},"
                + "  {\"type\": \"json\", \"action\": \"set\", \"path\": \"/a\", \"value\": null},"
                + "  {\"type\": \"method\", \"action\": \"set\", \"key\": \"\", \"value\": \"POST\"}"
                + "]}";

        String raw = HttpTargetTools.execute(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES, args, ctx);
        JsonNode result = parse(raw);
        assertFalse(result.has("error"), "unexpected error: " + raw);

        assertTrue(result.get("ok").asBoolean());
        assertEquals(3, result.get("operations_applied").asInt());
        assertNotNull(applied.get());
        verify(request).withHeader("X-Test", "1");
    }

    @Test
    void applySemantic_jsonRemoveField() throws Exception {
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        byte[] startBody = "{\"a\":1,\"b\":2}".getBytes(StandardCharsets.UTF_8);
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), startBody);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);
        String args = "{\"operations\":[{\"type\":\"json\",\"action\":\"remove\",\"path\":\"/a\"}]}";

        String raw2 = HttpTargetTools.execute(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES, args, ctx);
        JsonNode result = parse(raw2);
        assertFalse(result.has("error"), "unexpected error: " + raw2);
        assertTrue(result.get("ok").asBoolean());
        assertNotNull(applied.get());
    }

    @Test
    void applySemantic_jsonOnNonJsonBody_includesOpIndex() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), "not json".getBytes(StandardCharsets.UTF_8));
        AgentToolContext ctx = singleEntryCtx(request, null);
        String args = "{\"operations\":[{\"type\":\"json\",\"action\":\"set\",\"path\":\"/x\",\"value\":1}]}";

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES, args, ctx));
        assertTrue(result.has("error"));
        assertTrue(result.get("error").asText().toLowerCase().contains("not valid json")
                || result.get("error").asText().toLowerCase().contains("not valid"), result.toString());
        assertEquals(0, result.get("op_index").asInt());
        assertEquals("json", result.get("op_type").asText());
    }

    @Test
    void applySemantic_xmlOnInvalidXml_returnsError() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), "<nope".getBytes(StandardCharsets.UTF_8));
        AgentToolContext ctx = singleEntryCtx(request, null);
        String args = "{\"operations\":[{\"type\":\"xml\",\"action\":\"set\",\"path\":\"/a\",\"value\":\"v\"}]}";

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES, args, ctx));
        assertTrue(result.has("error"));
        assertTrue(result.get("error").asText().toLowerCase().contains("xml")
                || result.get("error").asText().toLowerCase().contains("form"), result.toString());
    }

    @Test
    void applySemantic_methodWithNonEmptyKey_rejects() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        String args = "{\"operations\":[{\"type\":\"method\",\"action\":\"set\",\"key\":\"m\",\"value\":\"GET\"}]}";

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES, args, ctx));
        assertTrue(result.has("error"));
        assertTrue(result.get("error").asText().contains("key must be empty") || result.get("error").asText().contains("empty"));
    }

    @Test
    void applySemantic_methodRemove_rejects() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        String args = "{\"operations\":[{\"type\":\"method\",\"action\":\"remove\"}]}";

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES, args, ctx));
        assertTrue(result.has("error"));
    }

    @Test
    void applySemantic_removeWithExplicitValue_rejects() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        String args = "{\"operations\":[{\"type\":\"header\",\"action\":\"remove\",\"key\":\"A\",\"value\":\"x\"}]}";

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES, args, ctx));
        assertTrue(result.has("error"));
        assertTrue(result.get("error").asText().contains("omit") || result.get("error").asText().contains("value"));
    }

    @Test
    void applySemantic_batchFailureDoesNotCommit() throws Exception {
        java.util.concurrent.atomic.AtomicInteger applierCount = new java.util.concurrent.atomic.AtomicInteger(0);
        List<HttpHeader> headers = List.of();
        byte[] startBody = "not json".getBytes(StandardCharsets.UTF_8);
        HttpRequest request = req("GET", "https://x.com/", "/", headers, startBody);
        AgentToolContext ctx = singleEntryCtx(request, null, r -> applierCount.incrementAndGet());
        // First: header ok; second: json on invalid body
        String args = "{"
                + "\"operations\": ["
                + "  {\"type\": \"header\", \"action\": \"set\", \"key\": \"X-Ok\", \"value\": \"1\"},"
                + "  {\"type\": \"json\", \"action\": \"set\", \"path\": \"/a\", \"value\": 1}"
                + "]}";

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES, args, ctx));
        assertTrue(result.has("error"));
        assertEquals(0, applierCount.get());
    }

    // ===== send_current_http_request =====

    @Test
    void sendCurrentHttpRequest_returnsStatusCode() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        Callable<Integer> sender = () -> 200;
        AgentToolContext ctx = new AgentToolContext(defaultTarget(), 0,
                List.of(new AgentToolContext.HistoryEntryInfo(0, "now", "GET x.com")),
                idx -> request, idx -> null, req -> {}, sender);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.SEND_CURRENT_HTTP_REQUEST, "{}", ctx));

        assertEquals(200, result.get("status_code").asInt());
        assertFalse(result.has("error"));
    }

    @Test
    void sendCurrentHttpRequest_nullSender_returnsError() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);  // sender is null

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.SEND_CURRENT_HTTP_REQUEST, "{}", ctx));

        assertTrue(result.has("error"));
    }

    @Test
    void sendCurrentHttpRequest_senderReturns404() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        Callable<Integer> sender = () -> 404;
        AgentToolContext ctx = new AgentToolContext(defaultTarget(), 0,
                List.of(new AgentToolContext.HistoryEntryInfo(0, "now", "GET x.com")),
                idx -> request, idx -> null, req -> {}, sender);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.SEND_CURRENT_HTTP_REQUEST, "{}", ctx));

        assertEquals(404, result.get("status_code").asInt());
    }

    // ===== humanToolUsage =====

    @Test
    void humanToolUsage_getCurrentAndReadMessages() {
        HttpTargetTools.HumanToolUsage t1 = HttpTargetTools.humanToolUsage(HttpTargetTools.GET_CURRENT_HTTP_TARGET, "{}", 0);
        assertTrue(t1.detail().isEmpty());
        assertFalse(t1.title().isBlank());
        HttpTargetTools.HumanToolUsage t2 =
                HttpTargetTools.humanToolUsage(HttpTargetTools.READ_HTTP_MESSAGE, "{\"side\":\"request\"}", 0);
        assertTrue(t2.title().contains("offset 0, max 4096"));
        assertTrue(t2.detail().isEmpty());
        HttpTargetTools.HumanToolUsage t3 = HttpTargetTools.humanToolUsage(
                HttpTargetTools.SEARCH_HTTP_MESSAGE, "{\"side\":\"request\",\"pattern\":\"^Host:\"}", 0);
        assertTrue(t3.title().contains("Searching request"));
    }

    @Test
    void humanToolUsage_applySemantic_listsEachOperation() {
        String json =
                "{\"operations\":["
                        + "{\"type\":\"header\",\"action\":\"set\",\"key\":\"X-Test\",\"value\":\"1\"},"
                        + "{\"type\":\"method\",\"action\":\"set\",\"key\":\"\",\"value\":\"POST\"},"
                        + "{\"type\":\"json\",\"action\":\"remove\",\"path\":\"/a\"}"
                        + "]}";
        HttpTargetTools.HumanToolUsage usage =
                HttpTargetTools.humanToolUsage(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES, json, 0);

        assertEquals("Apply semantic request changes", usage.title());
        String d = usage.detail();
        assertTrue(d.contains("1."), d);
        assertTrue(d.contains("Set header"), d);
        assertTrue(d.contains("X-Test"), d);
        assertTrue(d.contains("2."), d);
        assertTrue(d.contains("POST"), d);
        assertTrue(d.contains("3."), d);
        assertTrue(d.contains("JSON remove") && d.contains("/a"), d);
    }

    @Test
    void humanToolUsage_replaceBody_showsOldAndNewText() {
        HttpTargetTools.HumanToolUsage usage = HttpTargetTools.humanToolUsage(
                HttpTargetTools.REPLACE_IN_HTTP_REQUEST_BODY,
                "{\"old_text\":\"foo\",\"new_text\":\"bar\"}", 0);

        assertEquals("Replace text in request body", usage.title());
        assertTrue(usage.detail().contains("foo"));
        assertTrue(usage.detail().contains("bar"));
    }

    @Test
    void humanToolUsage_replaceBodyWithReplaceAll_showsAllOccurrencesNote() {
        HttpTargetTools.HumanToolUsage usage = HttpTargetTools.humanToolUsage(
                HttpTargetTools.REPLACE_IN_HTTP_REQUEST_BODY,
                "{\"old_text\":\"x\",\"new_text\":\"y\",\"replace_all\":true}", 0);

        assertTrue(usage.detail().contains("all"));
    }

    @Test
    void humanToolUsage_patchLines_showsLineRange() {
        HttpTargetTools.HumanToolUsage usage = HttpTargetTools.humanToolUsage(
                HttpTargetTools.PATCH_HTTP_REQUEST_BODY_LINES,
                "{\"start_line\":3,\"end_line\":5,\"content\":\"new content\"}", 0);

        assertEquals("Patch request body line range", usage.title());
        assertTrue(usage.detail().contains("3"));
        assertTrue(usage.detail().contains("5"));
    }

    @Test
    void humanToolUsage_historyIndexSuffix_omittedWhenSameAsViewer() {
        String args = "{\"side\":\"request\",\"history_index\":3}";
        HttpTargetTools.HumanToolUsage withSuffix = HttpTargetTools.humanToolUsage(
                HttpTargetTools.READ_HTTP_MESSAGE, args, 0);
        HttpTargetTools.HumanToolUsage withoutSuffix = HttpTargetTools.humanToolUsage(
                HttpTargetTools.READ_HTTP_MESSAGE, args, 3);

        assertTrue(withSuffix.title().contains("#3"),
                "should include history index when different from viewer: " + withSuffix.title());
        assertFalse(withoutSuffix.title().contains("#3"),
                "should omit history index when same as viewer: " + withoutSuffix.title());
    }

    @Test
    void humanToolUsage_sendRequest_hasTitleAndDetail() {
        HttpTargetTools.HumanToolUsage usage = HttpTargetTools.humanToolUsage(
                HttpTargetTools.SEND_CURRENT_HTTP_REQUEST, "{}", 0);

        assertFalse(usage.title().isBlank());
        assertFalse(usage.detail().isBlank());
    }

    @Test
    void humanToolUsage_setBody_noDetailToAvoidDuplicatingLargeBody() {
        HttpTargetTools.HumanToolUsage usage = HttpTargetTools.humanToolUsage(
                HttpTargetTools.SET_HTTP_REQUEST_BODY, "{\"body_utf8\":\"large body content\"}", 0);

        assertFalse(usage.title().isBlank());
        assertTrue(usage.detail().isEmpty(), "set_http_request_body detail should be empty");
    }

    @Test
    void humanToolUsage_unknownTool_returnsDefaultTitle() {
        HttpTargetTools.HumanToolUsage usage = HttpTargetTools.humanToolUsage("no_such_tool", "{}", 0);
        assertFalse(usage.title().isBlank());
    }

    @Test
    void humanToolUsage_invalidArgs_doesNotThrow() {
        assertDoesNotThrow(() ->
                HttpTargetTools.humanToolUsage(HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES, "INVALID{JSON", 0));
    }

    // ===== tryPreviewRequestMutation (in-memory, no editor commit) =====

    @Test
    void tryPreview_replace_changesBody() {
        byte[] body = "a foo c".getBytes(StandardCharsets.UTF_8);
        HttpRequest request = req("GET", "https://a/x", "/x", null, body);
        HttpRequest out =
                HttpTargetTools.tryPreviewRequestMutation(
                        HttpTargetTools.REPLACE_IN_HTTP_REQUEST_BODY,
                        "{\"old_text\":\"foo\",\"new_text\":\"bar\"}",
                        request);
        assertNotNull(out);
        String text = new String(
                out.body().getBytes() != null ? out.body().getBytes() : new byte[0], StandardCharsets.UTF_8);
        assertTrue(text.contains("bar"), text);
        assertFalse(text.contains("foo"), text);
    }

    @Test
    void tryPreview_readTool_returnsNull() {
        assertNull(
                HttpTargetTools.tryPreviewRequestMutation(
                        HttpTargetTools.READ_HTTP_MESSAGE, "{\"side\":\"request\"}", req("GET", "https://a/x", "/x", null, new byte[0])));
    }

    // ===== diagnostic =====

    @Test
    void diagnostic_byteArrayStaticMockIsActive() {
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        ByteArray result = ByteArray.byteArray(data);
        assertNotNull(result, "ByteArray.byteArray() should return a mock, not null");
    }

    // ===== permissionDeniedResult =====

    @Test
    void permissionDeniedResult_returnsErrorJson() throws Exception {
        JsonNode result = parse(HttpTargetTools.permissionDeniedResult());
        assertTrue(result.has("error"));
        assertEquals("permission denied", result.get("error").asText());
    }
}
