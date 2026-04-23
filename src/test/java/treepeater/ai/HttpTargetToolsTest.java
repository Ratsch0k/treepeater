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
        // RETURNS_MOCKS avoids the ClassCastException that arises when Mockito's inline
        // mock maker tries to unpack byte... varargs into individual Byte elements.
        byteArrayMock = mockStatic(ByteArray.class, RETURNS_MOCKS);

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

        stubMutations(req);
        return req;
    }

    /**
     * Stubs all with* mutation methods on req. withBody returns a new mock that
     * exposes the new ByteArray via body(); all other with* methods return a shared
     * "mutated" mock that self-chains further with* calls.
     */
    private static void stubMutations(HttpRequest req) {
        HttpRequest mutated = mock(HttpRequest.class);
        lenient().when(mutated.withHeader(anyString(), anyString())).thenReturn(mutated);
        lenient().when(mutated.withRemovedHeader(anyString())).thenReturn(mutated);
        lenient().when(mutated.withMethod(anyString())).thenReturn(mutated);
        lenient().when(mutated.withService(any(HttpService.class))).thenReturn(mutated);
        lenient().when(mutated.withPath(anyString())).thenReturn(mutated);
        lenient().when(mutated.withBody(any(ByteArray.class))).thenReturn(mutated);

        lenient().when(req.withHeader(anyString(), anyString())).thenReturn(mutated);
        lenient().when(req.withRemovedHeader(anyString())).thenReturn(mutated);
        lenient().when(req.withMethod(anyString())).thenReturn(mutated);
        lenient().when(req.withService(any(HttpService.class))).thenReturn(mutated);
        lenient().when(req.withPath(anyString())).thenReturn(mutated);
        lenient().when(req.withBody(any(ByteArray.class))).thenAnswer(inv -> {
            ByteArray newBa = inv.getArgument(0);
            HttpRequest updated = mock(HttpRequest.class);
            lenient().when(updated.body()).thenReturn(newBa);
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
        assertEquals(ToolActionLevel.READ_ONLY, HttpTargetTools.toolActionLevel(HttpTargetTools.GET_HTTP_HISTORY_STATE));
        assertEquals(ToolActionLevel.READ_ONLY, HttpTargetTools.toolActionLevel(HttpTargetTools.GET_HTTP_REQUEST_LINE));
        assertEquals(ToolActionLevel.READ_ONLY, HttpTargetTools.toolActionLevel(HttpTargetTools.LIST_HTTP_HEADER_NAMES));
        assertEquals(ToolActionLevel.READ_ONLY, HttpTargetTools.toolActionLevel(HttpTargetTools.GET_HTTP_HEADER));
        assertEquals(ToolActionLevel.READ_ONLY, HttpTargetTools.toolActionLevel(HttpTargetTools.LIST_HTTP_COOKIES));
        assertEquals(ToolActionLevel.READ_ONLY, HttpTargetTools.toolActionLevel(HttpTargetTools.GET_HTTP_COOKIE));
        assertEquals(ToolActionLevel.READ_ONLY, HttpTargetTools.toolActionLevel(HttpTargetTools.READ_HTTP_BODY));
    }

    @Test
    void toolActionLevel_writeTools() {
        assertEquals(ToolActionLevel.WRITE, HttpTargetTools.toolActionLevel(HttpTargetTools.REPLACE_IN_HTTP_REQUEST_BODY));
        assertEquals(ToolActionLevel.WRITE, HttpTargetTools.toolActionLevel(HttpTargetTools.PATCH_HTTP_REQUEST_BODY_LINES));
        assertEquals(ToolActionLevel.WRITE, HttpTargetTools.toolActionLevel(HttpTargetTools.SET_HTTP_REQUEST_BODY));
        assertEquals(ToolActionLevel.WRITE, HttpTargetTools.toolActionLevel(HttpTargetTools.SET_HTTP_REQUEST_HEADER));
        assertEquals(ToolActionLevel.WRITE, HttpTargetTools.toolActionLevel(HttpTargetTools.REMOVE_HTTP_REQUEST_HEADER));
        assertEquals(ToolActionLevel.WRITE, HttpTargetTools.toolActionLevel(HttpTargetTools.SET_HTTP_REQUEST_COOKIE));
        assertEquals(ToolActionLevel.WRITE, HttpTargetTools.toolActionLevel(HttpTargetTools.SET_HTTP_REQUEST_METHOD));
        assertEquals(ToolActionLevel.WRITE, HttpTargetTools.toolActionLevel(HttpTargetTools.SET_HTTP_REQUEST_URL));
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
        assertFalse(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.GET_HTTP_HISTORY_STATE, AgentMode.ASK));
        assertFalse(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.READ_HTTP_BODY, AgentMode.ASK));
    }

    @Test
    void approval_askMode_writeAndExecuteNeedApproval() {
        assertTrue(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.SET_HTTP_REQUEST_BODY, AgentMode.ASK));
        assertTrue(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.SEND_CURRENT_HTTP_REQUEST, AgentMode.ASK));
    }

    @Test
    void approval_helperMode_onlyExecuteNeedsApproval() {
        assertFalse(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.GET_HTTP_HISTORY_STATE, AgentMode.HELPER));
        assertFalse(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.SET_HTTP_REQUEST_BODY, AgentMode.HELPER));
        assertTrue(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.SEND_CURRENT_HTTP_REQUEST, AgentMode.HELPER));
    }

    @Test
    void approval_autonomousMode_nothingNeedsApproval() {
        assertFalse(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.GET_HTTP_HISTORY_STATE, AgentMode.AUTONOMOUS));
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
        assertFalse(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.GET_HTTP_HISTORY_STATE, null));
        assertTrue(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.SET_HTTP_REQUEST_BODY, null));
        assertTrue(HttpTargetTools.requiresUserApprovalInAgentMode(HttpTargetTools.SEND_CURRENT_HTTP_REQUEST, null));
    }

    // ===== definitions =====

    @Test
    void definitions_returnsAll17Tools() {
        assertEquals(17, HttpTargetTools.definitions().size());
    }

    // ===== result cap =====

    @Test
    void execute_cappedWhenResultExceedsLimit() throws Exception {
        // A single header value large enough that the surrounding JSON for get_http_header
        // exceeds MAX_TOOL_RESULT_CHARS (96k). Use 120k chars to leave no doubt.
        String huge = "x".repeat(120_000);
        List<HttpHeader> headers = List.of(hdr("X-Big", huge));
        HttpRequest request = req("GET", "https://x.com/", "/", headers, new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.GET_HTTP_HEADER,
                "{\"history_index\":0,\"side\":\"request\",\"name\":\"X-Big\"}", ctx));

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
        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.GET_HTTP_HISTORY_STATE, "{}", null));
        assertTrue(result.has("error"), "expected error field");
    }

    @Test
    void execute_invalidJsonArgs_returnsError() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.GET_HTTP_HISTORY_STATE, "NOT{JSON", ctx));
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

    // ===== get_http_history_state =====

    @Test
    void getHttpHistoryState_singleEntry_noPrevOrNext() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.GET_HTTP_HISTORY_STATE, "{}", ctx));

        assertEquals(0, result.get("current_history_index").asInt());
        assertEquals(1, result.get("entry_count").asInt());
        assertFalse(result.get("has_previous_history").asBoolean());
        assertFalse(result.get("has_next_history").asBoolean());
        assertEquals(1, result.get("entries").size());
        assertEquals("12:00:00", result.get("entries").get(0).get("time").asText());
    }

    @Test
    void getHttpHistoryState_middleEntry_hasPrevAndNext() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        List<AgentToolContext.HistoryEntryInfo> entries = List.of(
                new AgentToolContext.HistoryEntryInfo(0, "10:00", "GET /a"),
                new AgentToolContext.HistoryEntryInfo(1, "10:01", "POST /b"),
                new AgentToolContext.HistoryEntryInfo(2, "10:02", "GET /c"));
        AgentToolContext ctx = new AgentToolContext(defaultTarget(), 1, entries,
                idx -> request, idx -> null, req -> {}, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.GET_HTTP_HISTORY_STATE, "{}", ctx));

        assertEquals(1, result.get("current_history_index").asInt());
        assertEquals(3, result.get("entry_count").asInt());
        assertTrue(result.get("has_previous_history").asBoolean());
        assertTrue(result.get("has_next_history").asBoolean());
        assertEquals(3, result.get("entries").size());
    }

    // ===== get_http_request_line =====

    @Test
    void getHttpRequestLine_returnsMethodUrlPathVersionAndService() throws Exception {
        HttpService svc = mock(HttpService.class);
        when(svc.host()).thenReturn("example.com");
        when(svc.port()).thenReturn(443);
        when(svc.secure()).thenReturn(true);

        HttpRequest request = req("POST", "https://example.com/api", "/api", List.of(), new byte[0]);
        when(request.httpService()).thenReturn(svc);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.GET_HTTP_REQUEST_LINE,
                "{\"history_index\":0}", ctx));

        assertEquals("POST", result.get("method").asText());
        assertEquals("https://example.com/api", result.get("url").asText());
        assertEquals("/api", result.get("path").asText());
        assertEquals("HTTP/1.1", result.get("http_version").asText());
        assertTrue(result.has("http_service"));
        assertEquals("https", result.get("http_service").get("scheme").asText());
        assertEquals("example.com", result.get("http_service").get("host").asText());
        assertEquals(443, result.get("http_service").get("port").asInt());
        assertTrue(result.get("http_service").get("secure").asBoolean());
    }

    @Test
    void getHttpRequestLine_omittedHistoryIndex_usesCurrentEntry() throws Exception {
        HttpRequest request = req("DELETE", "https://x.com/resource/1", "/resource/1", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.GET_HTTP_REQUEST_LINE, "{}", ctx));
        assertEquals("DELETE", result.get("method").asText());
    }

    @Test
    void getHttpRequestLine_camelCaseHistoryIndexAlias() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.GET_HTTP_REQUEST_LINE,
                "{\"historyIndex\":0}", ctx));
        assertFalse(result.has("error"));
        assertEquals("GET", result.get("method").asText());
    }

    // ===== list_http_header_names =====

    @Test
    void listHttpHeaderNames_requestSide_deduplicatesCaseInsensitively() throws Exception {
        List<HttpHeader> headers = List.of(
                hdr("Content-Type", "application/json"),
                hdr("X-Custom", "a"),
                hdr("content-type", "text/plain"));  // duplicate of Content-Type
        HttpRequest request = req("POST", "https://x.com/", "/", headers, new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.LIST_HTTP_HEADER_NAMES,
                "{\"history_index\":0,\"side\":\"request\"}", ctx));

        JsonNode names = result.get("header_names");
        assertEquals(2, names.size(), "duplicate should be removed");
        assertEquals("Content-Type", names.get(0).asText());
        assertEquals("X-Custom", names.get(1).asText());
        assertEquals("request", result.get("side").asText());
    }

    @Test
    void listHttpHeaderNames_responseSide() throws Exception {
        List<HttpHeader> headers = List.of(
                hdr("Content-Length", "42"),
                hdr("Set-Cookie", "session=abc"));
        HttpResponse response = res(headers, new byte[0]);
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, response);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.LIST_HTTP_HEADER_NAMES,
                "{\"history_index\":0,\"side\":\"response\"}", ctx));

        assertEquals(2, result.get("header_names").size());
        assertEquals("response", result.get("side").asText());
    }

    // ===== get_http_header =====

    @Test
    void getHttpHeader_caseInsensitiveMatch_returnsAllValues() throws Exception {
        List<HttpHeader> headers = List.of(
                hdr("Accept", "text/html"),
                hdr("accept", "application/json"));
        HttpRequest request = req("GET", "https://x.com/", "/", headers, new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.GET_HTTP_HEADER,
                "{\"history_index\":0,\"side\":\"request\",\"name\":\"ACCEPT\"}", ctx));

        assertTrue(result.get("found").asBoolean());
        assertEquals(2, result.get("values").size());
        assertEquals("text/html", result.get("values").get(0).asText());
        assertEquals("application/json", result.get("values").get(1).asText());
    }

    @Test
    void getHttpHeader_notFound_returnsFalse() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.GET_HTTP_HEADER,
                "{\"history_index\":0,\"side\":\"request\",\"name\":\"X-Missing\"}", ctx));

        assertFalse(result.get("found").asBoolean());
        assertEquals(0, result.get("values").size());
    }

    // ===== list_http_cookies =====

    @Test
    void listHttpCookies_requestSide_parsesMultipleCookies() throws Exception {
        List<HttpHeader> headers = List.of(hdr("Cookie", "session=abc; token=xyz"));
        HttpRequest request = req("GET", "https://x.com/", "/", headers, new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.LIST_HTTP_COOKIES,
                "{\"history_index\":0,\"side\":\"request\"}", ctx));

        JsonNode names = result.get("cookie_names");
        assertEquals(2, names.size());
        assertEquals("session", names.get(0).asText());
        assertEquals("token", names.get(1).asText());
    }

    @Test
    void listHttpCookies_responseSide_parsesSetCookieHeaders() throws Exception {
        List<HttpHeader> headers = List.of(
                hdr("Set-Cookie", "session=abc; Path=/"),
                hdr("Set-Cookie", "prefs=dark; HttpOnly"));
        HttpResponse response = res(headers, new byte[0]);
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, response);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.LIST_HTTP_COOKIES,
                "{\"history_index\":0,\"side\":\"response\"}", ctx));

        JsonNode names = result.get("cookie_names");
        assertEquals(2, names.size());
        assertEquals("session", names.get(0).asText());
        assertEquals("prefs", names.get(1).asText());
    }

    @Test
    void listHttpCookies_noCookieHeader_returnsEmptyList() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.LIST_HTTP_COOKIES,
                "{\"history_index\":0,\"side\":\"request\"}", ctx));

        assertEquals(0, result.get("cookie_names").size());
    }

    // ===== get_http_cookie =====

    @Test
    void getHttpCookie_requestSide_found() throws Exception {
        List<HttpHeader> headers = List.of(hdr("Cookie", "session=abc123; token=xyz"));
        HttpRequest request = req("GET", "https://x.com/", "/", headers, new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.GET_HTTP_COOKIE,
                "{\"history_index\":0,\"side\":\"request\",\"name\":\"session\"}", ctx));

        assertTrue(result.get("found").asBoolean());
        assertEquals("abc123", result.get("value").asText());
    }

    @Test
    void getHttpCookie_requestSide_notFound() throws Exception {
        List<HttpHeader> headers = List.of(hdr("Cookie", "session=abc123"));
        HttpRequest request = req("GET", "https://x.com/", "/", headers, new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.GET_HTTP_COOKIE,
                "{\"history_index\":0,\"side\":\"request\",\"name\":\"token\"}", ctx));

        assertFalse(result.get("found").asBoolean());
        assertFalse(result.has("value"));
    }

    @Test
    void getHttpCookie_responseSide_found_returnsRawSetCookieHeader() throws Exception {
        List<HttpHeader> headers = List.of(hdr("Set-Cookie", "session=abc123; Path=/; Secure"));
        HttpResponse response = res(headers, new byte[0]);
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, response);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.GET_HTTP_COOKIE,
                "{\"history_index\":0,\"side\":\"response\",\"name\":\"session\"}", ctx));

        assertTrue(result.get("found").asBoolean());
        assertEquals("session=abc123; Path=/; Secure", result.get("set_cookie_header").asText());
    }

    @Test
    void getHttpCookie_responseSide_notFound() throws Exception {
        List<HttpHeader> headers = List.of(hdr("Set-Cookie", "other=value"));
        HttpResponse response = res(headers, new byte[0]);
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, response);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.GET_HTTP_COOKIE,
                "{\"history_index\":0,\"side\":\"response\",\"name\":\"session\"}", ctx));

        assertFalse(result.get("found").asBoolean());
    }

    // ===== read_http_body =====

    @Test
    void readHttpBody_utf8Body_returnsTextEncoding() throws Exception {
        byte[] body = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.READ_HTTP_BODY,
                "{\"history_index\":0,\"side\":\"request\"}", ctx));

        assertEquals(13, result.get("total_bytes").asInt());
        assertEquals("utf-8", result.get("encoding").asText());
        assertEquals("Hello, World!", result.get("text").asText());
        assertEquals(0, result.get("offset").asInt());
        assertFalse(result.get("has_more").asBoolean());
        assertEquals(13, result.get("next_offset").asInt());
    }

    @Test
    void readHttpBody_binaryBody_returnsBase64Encoding() throws Exception {
        byte[] body = new byte[]{(byte) 0xFF, (byte) 0xFE, 0x00, 0x01};
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.READ_HTTP_BODY,
                "{\"history_index\":0,\"side\":\"request\"}", ctx));

        assertEquals("base64", result.get("encoding").asText());
        assertTrue(result.get("is_binary_chunk").asBoolean());
        byte[] decoded = Base64.getDecoder().decode(result.get("base64").asText());
        assertArrayEquals(body, decoded);
    }

    @Test
    void readHttpBody_withOffsetAndMaxBytes_returnsCorrectChunk() throws Exception {
        byte[] body = "0123456789".getBytes(StandardCharsets.UTF_8);
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.READ_HTTP_BODY,
                "{\"history_index\":0,\"side\":\"request\",\"offset\":4,\"max_bytes\":3}", ctx));

        assertEquals(10, result.get("total_bytes").asInt());
        assertEquals(4, result.get("offset").asInt());
        assertEquals(3, result.get("returned_bytes").asInt());
        assertEquals("456", result.get("text").asText());
        assertTrue(result.get("has_more").asBoolean());
        assertEquals(7, result.get("next_offset").asInt());
    }

    @Test
    void readHttpBody_maxBytesTooLarge_returnsError() throws Exception {
        final int maxBytes = 65_536;

        byte[] body = "A".repeat(2 * maxBytes).getBytes(StandardCharsets.UTF_8);
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.READ_HTTP_BODY,
                "{\"history_index\":0,\"side\":\"request\",\"offset\":0,\"max_bytes\":9999999999}", ctx));

        assertEquals(maxBytes * 2, result.get("total_bytes").asInt());
        assertEquals(0, result.get("offset").asInt());
        assertEquals(maxBytes, result.get("returned_bytes").asInt());
        assertEquals("A".repeat(maxBytes), result.get("text").asText());
        assertTrue(result.get("has_more").asBoolean());
        assertEquals(maxBytes, result.get("next_offset").asInt());    }

    @Test
    void readHttpBody_responseSide() throws Exception {
        byte[] body = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        HttpResponse response = res(List.of(), body);
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, response);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.READ_HTTP_BODY,
                "{\"history_index\":0,\"side\":\"response\"}", ctx));

        assertEquals("utf-8", result.get("encoding").asText());
        assertEquals("{\"status\":\"ok\"}", result.get("text").asText());
    }

    @Test
    void readHttpBody_emptyBody() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.READ_HTTP_BODY,
                "{\"history_index\":0,\"side\":\"request\"}", ctx));

        assertEquals(0, result.get("total_bytes").asInt());
        assertEquals(0, result.get("returned_bytes").asInt());
        assertFalse(result.get("has_more").asBoolean());
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

    // ===== set_http_request_header =====

    @Test
    void setHttpRequestHeader_invokesWithHeaderAndCommits() throws Exception {
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.SET_HTTP_REQUEST_HEADER,
                "{\"name\":\"X-Custom\",\"value\":\"hello\"}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertNotNull(applied.get());
        verify(request).withHeader("X-Custom", "hello");
    }

    @Test
    void setHttpRequestHeader_missingName_returnsError() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.SET_HTTP_REQUEST_HEADER,
                "{\"value\":\"hello\"}", ctx));

        assertTrue(result.has("error"));
    }

    // ===== remove_http_request_header =====

    @Test
    void removeHttpRequestHeader_invokesWithRemovedHeaderAndCommits() throws Exception {
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        List<HttpHeader> headers = List.of(hdr("X-Remove-Me", "value"));
        HttpRequest request = req("GET", "https://x.com/", "/", headers, new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.REMOVE_HTTP_REQUEST_HEADER,
                "{\"name\":\"X-Remove-Me\"}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertNotNull(applied.get());
        verify(request).withRemovedHeader("X-Remove-Me");
    }

    // ===== set_http_request_cookie =====

    @Test
    void setHttpRequestCookie_addsCookieAlongsideExisting() throws Exception {
        List<HttpHeader> headers = List.of(hdr("Cookie", "session=abc"));
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("GET", "https://x.com/", "/", headers, new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.SET_HTTP_REQUEST_COOKIE,
                "{\"name\":\"token\",\"value\":\"xyz\"}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertNotNull(applied.get());
        // The old Cookie header must be removed first
        verify(request).withRemovedHeader("Cookie");
    }

    @Test
    void setHttpRequestCookie_removeCookie() throws Exception {
        List<HttpHeader> headers = List.of(hdr("Cookie", "session=abc; token=xyz"));
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("GET", "https://x.com/", "/", headers, new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.SET_HTTP_REQUEST_COOKIE,
                "{\"name\":\"session\",\"remove\":true}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertNotNull(applied.get());
    }

    // ===== set_http_request_method =====

    @Test
    void setHttpRequestMethod_invokesWithMethodAndCommits() throws Exception {
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.SET_HTTP_REQUEST_METHOD,
                "{\"method\":\"POST\"}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertNotNull(applied.get());
        verify(request).withMethod("POST");
    }

    // ===== set_http_request_url =====

    @Test
    void setHttpRequestUrl_parsesHostPortPathAndCommits() throws Exception {
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("GET", "https://old.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.SET_HTTP_REQUEST_URL,
                "{\"url\":\"https://new.com:8443/api/v1?q=test\"}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertNotNull(applied.get());
        httpServiceMock.verify(() -> HttpService.httpService("new.com", 8443, true));
        verify(request).withService(any(HttpService.class));
    }

    @Test
    void setHttpRequestUrl_httpDefaultPort80() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null, req -> {});

        parse(HttpTargetTools.execute(HttpTargetTools.SET_HTTP_REQUEST_URL,
                "{\"url\":\"http://example.com/path\"}", ctx));

        httpServiceMock.verify(() -> HttpService.httpService("example.com", 80, false));
    }

    @Test
    void setHttpRequestUrl_noScheme_returnsError() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.SET_HTTP_REQUEST_URL,
                "{\"url\":\"//example.com/path\"}", ctx));

        assertTrue(result.has("error"));
    }

    @Test
    void setHttpRequestUrl_nonHttpScheme_returnsError() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.SET_HTTP_REQUEST_URL,
                "{\"url\":\"ftp://example.com/file\"}", ctx));

        assertTrue(result.has("error"));
    }

    @Test
    void setHttpRequestUrl_invalidUrl_returnsError() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetTools.execute(HttpTargetTools.SET_HTTP_REQUEST_URL,
                "{\"url\":\"https://\"}", ctx));

        assertTrue(result.has("error"));
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
    void humanToolUsage_readOnlyTools_emptyDetail() {
        for (String tool : new String[]{
                HttpTargetTools.GET_CURRENT_HTTP_TARGET,
                HttpTargetTools.GET_HTTP_HISTORY_STATE,
                HttpTargetTools.LIST_HTTP_HEADER_NAMES,
                HttpTargetTools.READ_HTTP_BODY}) {
            HttpTargetTools.HumanToolUsage usage = HttpTargetTools.humanToolUsage(tool, "{}", 0);
            assertFalse(usage.title().isBlank(), "title should not be blank for: " + tool);
            // Read-only tools produce no detail
            assertTrue(usage.detail().isEmpty(), "detail should be empty for read tool: " + tool);
        }
    }

    @Test
    void humanToolUsage_setHeader_includesHeaderNameAndValue() {
        HttpTargetTools.HumanToolUsage usage = HttpTargetTools.humanToolUsage(
                HttpTargetTools.SET_HTTP_REQUEST_HEADER,
                "{\"name\":\"X-Custom\",\"value\":\"hello\"}", 0);

        assertEquals("Set request header", usage.title());
        assertEquals("X-Custom: hello", usage.detail());
    }

    @Test
    void humanToolUsage_removeHeader_includesHeaderName() {
        HttpTargetTools.HumanToolUsage usage = HttpTargetTools.humanToolUsage(
                HttpTargetTools.REMOVE_HTTP_REQUEST_HEADER,
                "{\"name\":\"Authorization\"}", 0);

        assertEquals("Remove request header", usage.title());
        assertTrue(usage.detail().contains("Authorization"));
    }

    @Test
    void humanToolUsage_setMethod_includesNewMethod() {
        HttpTargetTools.HumanToolUsage usage = HttpTargetTools.humanToolUsage(
                HttpTargetTools.SET_HTTP_REQUEST_METHOD, "{\"method\":\"DELETE\"}", 0);

        assertTrue(usage.detail().contains("DELETE"));
    }

    @Test
    void humanToolUsage_setUrl_includesUrl() {
        HttpTargetTools.HumanToolUsage usage = HttpTargetTools.humanToolUsage(
                HttpTargetTools.SET_HTTP_REQUEST_URL, "{\"url\":\"https://example.com/api\"}", 0);

        assertTrue(usage.detail().contains("https://example.com/api"));
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
    void humanToolUsage_setCookie_setMode() {
        HttpTargetTools.HumanToolUsage usage = HttpTargetTools.humanToolUsage(
                HttpTargetTools.SET_HTTP_REQUEST_COOKIE,
                "{\"name\":\"session\",\"value\":\"abc123\"}", 0);

        assertEquals("Set cookie", usage.title());
        assertTrue(usage.detail().contains("session"));
        assertTrue(usage.detail().contains("abc123"));
    }

    @Test
    void humanToolUsage_setCookie_removeMode() {
        HttpTargetTools.HumanToolUsage usage = HttpTargetTools.humanToolUsage(
                HttpTargetTools.SET_HTTP_REQUEST_COOKIE,
                "{\"name\":\"session\",\"remove\":true}", 0);

        assertEquals("Remove cookie", usage.title());
        assertTrue(usage.detail().contains("session"));
    }

    @Test
    void humanToolUsage_historyIndexSuffix_omittedWhenSameAsViewer() {
        HttpTargetTools.HumanToolUsage withSuffix = HttpTargetTools.humanToolUsage(
                HttpTargetTools.GET_HTTP_REQUEST_LINE, "{\"history_index\":3}", 0);
        HttpTargetTools.HumanToolUsage withoutSuffix = HttpTargetTools.humanToolUsage(
                HttpTargetTools.GET_HTTP_REQUEST_LINE, "{\"history_index\":3}", 3);

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
                HttpTargetTools.humanToolUsage(HttpTargetTools.SET_HTTP_REQUEST_HEADER, "INVALID{JSON", 0));
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
