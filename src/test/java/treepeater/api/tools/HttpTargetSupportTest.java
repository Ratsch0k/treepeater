package treepeater.api.tools;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Answers.RETURNS_MOCKS;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.OptionalInt;
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

import treepeater.TreepeaterModel.SiblingCopyPlacement;
import treepeater.ai.AgentMode;
import treepeater.ai.AgentToolContext;
import treepeater.ai.ChatToolDefinition;
import treepeater.ai.ChatToolInvokeContext;
import treepeater.ai.HttpTargetSnapshot;
import treepeater.ai.NestedToolInvoker;
import treepeater.ai.TreepeaterTabAgentBridge;
import treepeater.ai.SearchTabRow;
import treepeater.ai.ToolActionLevel;
import treepeater.api.TreepeaterToolRegistry;
import treepeater.ai.AgentModeToolPolicy;
import treepeater.api.tools.core.ToolResults;
import treepeater.api.tools.http.ApplyHttpRequestSemanticChangesTool;
import treepeater.api.tools.http.BatchHttpTargetToolsTool;
import treepeater.api.tools.http.CopyTreepeaterNodeTool;
import treepeater.api.tools.http.GetCurrentHttpTargetTool;
import treepeater.api.tools.http.PatchHttpRequestBodyLinesTool;
import treepeater.api.tools.http.ReadHttpMessageTool;
import treepeater.api.tools.http.ReplaceInHttpRequestBodyTool;
import treepeater.api.tools.http.SearchHttpMessageTool;
import treepeater.api.tools.http.SearchTabsTool;
import treepeater.api.tools.http.SendCurrentHttpRequestTool;
import treepeater.api.tools.http.SetHttpRequestBodyTool;
import treepeater.api.tools.http.support.HttpTargetSupport;
import treepeater.api.tools.http.support.TabListingFormatter;

class HttpTargetSupportTest {

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
                defaultTarget(),
                42,
                0,
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

    private static byte[] bodyBytes(HttpRequest request) {
        ByteArray ba = request.body();
        return ba != null && ba.getBytes() != null ? ba.getBytes() : new byte[0];
    }

    private static String bodyUtf8(HttpRequest request) {
        return new String(bodyBytes(request), StandardCharsets.UTF_8);
    }

    private static TreepeaterToolRegistry editorRegistry(AgentToolContext ctx) {
        return TreepeaterToolRegistry.createEditorOnly(TreepeaterTabAgentBridge.singleTab(ctx));
    }

    // ===== toolActionLevel =====

    @Test
    void toolActionLevel_readOnlyTools() {
        assertEquals(ToolActionLevel.READ_ONLY, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null)).actionLevelFor(GetCurrentHttpTargetTool.NAME));
        assertEquals(ToolActionLevel.READ_ONLY, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null)).actionLevelFor(ReadHttpMessageTool.NAME));
        assertEquals(ToolActionLevel.READ_ONLY, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null)).actionLevelFor(SearchHttpMessageTool.NAME));
        assertEquals(ToolActionLevel.READ_ONLY, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null)).actionLevelFor(BatchHttpTargetToolsTool.NAME));
    }

    @Test
    void toolActionLevel_writeTools() {
        assertEquals(ToolActionLevel.WRITE, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null)).actionLevelFor(ReplaceInHttpRequestBodyTool.NAME));
        assertEquals(ToolActionLevel.WRITE, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null)).actionLevelFor(PatchHttpRequestBodyLinesTool.NAME));
        assertEquals(ToolActionLevel.WRITE, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null)).actionLevelFor(SetHttpRequestBodyTool.NAME));
        assertEquals(ToolActionLevel.WRITE, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null)).actionLevelFor(ApplyHttpRequestSemanticChangesTool.NAME));
        assertEquals(ToolActionLevel.WRITE, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null)).actionLevelFor(CopyTreepeaterNodeTool.NAME));
    }

    @Test
    void toolActionLevel_executeTools() {
        assertEquals(ToolActionLevel.EXECUTE, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null)).actionLevelFor(SendCurrentHttpRequestTool.NAME));
    }

    @Test
    void toolActionLevel_nullAndUnknown() {
        assertNull(editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null)).actionLevelFor(null));
        assertNull(editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null)).actionLevelFor("unknown_tool_xyz"));
    }

    // ===== requiresUserApprovalInAgentMode =====

    @Test
    void approval_askMode_readToolsNeedNoApproval() {
        assertFalse(new AgentModeToolPolicy(AgentMode.ASK, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null))).requiresApproval(ReadHttpMessageTool.NAME));
        assertFalse(new AgentModeToolPolicy(AgentMode.ASK, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null))).requiresApproval(SearchHttpMessageTool.NAME));
    }

    @Test
    void approval_askMode_writeAndExecuteNeedApproval() {
        assertTrue(new AgentModeToolPolicy(AgentMode.ASK, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null))).requiresApproval(SetHttpRequestBodyTool.NAME));
        assertTrue(new AgentModeToolPolicy(AgentMode.ASK, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null))).requiresApproval(SendCurrentHttpRequestTool.NAME));
    }

    @Test
    void approval_helperMode_onlyExecuteNeedsApproval() {
        assertFalse(new AgentModeToolPolicy(AgentMode.HELPER, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null))).requiresApproval(GetCurrentHttpTargetTool.NAME));
        assertFalse(new AgentModeToolPolicy(AgentMode.HELPER, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null))).requiresApproval(SetHttpRequestBodyTool.NAME));
        assertTrue(new AgentModeToolPolicy(AgentMode.HELPER, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null))).requiresApproval(SendCurrentHttpRequestTool.NAME));
    }

    @Test
    void approval_autonomousMode_nothingNeedsApproval() {
        assertFalse(new AgentModeToolPolicy(AgentMode.AUTONOMOUS, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null))).requiresApproval(SearchHttpMessageTool.NAME));
        assertFalse(new AgentModeToolPolicy(AgentMode.AUTONOMOUS, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null))).requiresApproval(SetHttpRequestBodyTool.NAME));
        assertFalse(new AgentModeToolPolicy(AgentMode.AUTONOMOUS, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null))).requiresApproval(SendCurrentHttpRequestTool.NAME));
    }

    @Test
    void approval_unknownTool_requiresApprovalExceptAutonomous() {
        assertTrue(new AgentModeToolPolicy(AgentMode.ASK, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null))).requiresApproval("no_such_tool"));
        assertTrue(new AgentModeToolPolicy(AgentMode.HELPER, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null))).requiresApproval("no_such_tool"));
        assertFalse(new AgentModeToolPolicy(AgentMode.AUTONOMOUS, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null))).requiresApproval("no_such_tool"));
    }

    @Test
    void approval_nullMode_treatedAsAsk() {
        assertFalse(new AgentModeToolPolicy(null, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null))).requiresApproval(GetCurrentHttpTargetTool.NAME));
        assertTrue(new AgentModeToolPolicy(null, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null))).requiresApproval(SetHttpRequestBodyTool.NAME));
        assertTrue(new AgentModeToolPolicy(null, editorRegistry(singleEntryCtx(req("GET", "https://example.com/", "/", List.of(), new byte[0]), null))).requiresApproval(SendCurrentHttpRequestTool.NAME));
    }

    // ===== register =====

    @Test
    void register_exposesAllBuiltInTools() {
        HttpRequest request = req("GET", "https://example.com/", "/", List.of(), new byte[0]);
        TreepeaterToolRegistry registry = editorRegistry(singleEntryCtx(request, null));
        assertEquals(11, registry.tools().size());
        assertTrue(registry.find(CopyTreepeaterNodeTool.NAME) != null);
    }

    @Test
    void register_allHaveNonEmptyNamesAndDescriptions() throws Exception {
        HttpRequest request = req("GET", "https://example.com/", "/", List.of(), new byte[0]);
        for (ChatToolDefinition def : editorRegistry(singleEntryCtx(request, null)).chatToolDefinitions()) {
            assertFalse(def.name().isBlank(), "tool name should not be blank");
            assertFalse(def.description().isBlank(), "description should not be blank for: " + def.name());
            assertFalse(def.parametersJsonSchema().isBlank(), "schema should not be blank for: " + def.name());
            JsonNode schema = JSON.readTree(def.parametersJsonSchema());
            assertTrue(schema.isObject(), "schema must parse as JSON object for: " + def.name());
        }
    }

    @Test
    void batch_http_target_tools_returnsOrderedResults() throws Exception {
        HttpRequest request = req("GET", "https://example.com/path", "/path", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        String batch =
                "{\"tools\":["
                        + "{\"tool_name\":\"get_current_http_target\",\"arguments\":{}},"
                        + "{\"tool_name\":\"read_http_message\",\"arguments\":{\"side\":\"request\",\"offset\":0,\"max_bytes\":64}}"
                        + "]}";
        JsonNode result =
                parse(HttpTargetSupport.execute(BatchHttpTargetToolsTool.NAME, batch, ctx));
        JsonNode results = result.get("results");
        assertEquals(2, results.size());
        assertEquals("get_current_http_target", results.get(0).get("tool_name").asText());
        assertEquals("example.com", results.get(0).get("result").get("host").asText());
        assertEquals("read_http_message", results.get(1).get("tool_name").asText());
        assertTrue(results.get(1).get("result").has("total_bytes"));
    }

    @Test
    void batch_http_target_tools_unknownInnerTool_surfacesInResult() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        String batch =
                "{\"tools\":[{\"tool_name\":\"not_a_real_tool\",\"arguments\":{}}]}";
        JsonNode result =
                parse(HttpTargetSupport.execute(BatchHttpTargetToolsTool.NAME, batch, ctx));
        JsonNode row = result.get("results").get(0);
        assertTrue(row.get("result").get("error").asText().contains("unknown tool"));
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
                HttpTargetSupport.execute(
                        SearchHttpMessageTool.NAME,
                        "{\"side\":\"request\",\"scope\":\"body\",\"pattern\":\"(A{200000})\"}", ctx));

        assertEquals("tool_result_too_large", result.get("error").asText());
        assertTrue(result.has("result_chars"));
        assertTrue(result.has("max_result_chars"));
        assertTrue(result.get("result_chars").asInt() > result.get("max_result_chars").asInt());
        assertTrue(result.has("hint"), "cap response should point the model at paginated alternatives");
    }

    // ===== execute – error cases =====

    @Test
    void execute_nullContext_returnsError() throws Exception {
        JsonNode result =
                parse(HttpTargetSupport.execute(GetCurrentHttpTargetTool.NAME, "{}", (AgentToolContext) null));
        assertTrue(result.has("error"), "expected error field");
    }

    @Test
    void execute_invalidJsonArgs_returnsError() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        JsonNode result = parse(HttpTargetSupport.execute(GetCurrentHttpTargetTool.NAME, "NOT{JSON", ctx));
        assertTrue(result.has("error"));
    }

    @Test
    void execute_unknownTool_returnsErrorWithToolName() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        JsonNode result = parse(HttpTargetSupport.execute("no_such_tool", "{}", ctx));
        assertTrue(result.has("error"));
        assertTrue(result.get("error").asText().contains("no_such_tool"));
    }

    // ===== get_current_http_target =====

    @Test
    void getCurrentHttpTarget_returnsTargetFieldsAndHistoryObject() throws Exception {
        HttpRequest request = req("GET", "https://example.com/path", "/path", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        JsonNode result = parse(HttpTargetSupport.execute(GetCurrentHttpTargetTool.NAME, "{}", ctx));

        assertEquals("https", result.get("scheme").asText());
        assertEquals("example.com", result.get("host").asText());
        assertEquals(443, result.get("port").asInt());
        assertTrue(result.get("sniEnabled").asBoolean());
        assertTrue(result.has("history"));
        assertEquals(1, result.get("history").get("entry_count").asInt());
        assertEquals(0, result.get("history").get("current_history_index").asInt());
        assertEquals(42, result.get("request_node_id").asInt());
    }

    // ===== read_http_message & search_http_message =====

    @Test
    void readHttpMessage_missingSide_returnsError() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        stubToByteArray(request, "GET /x HTTP/1.1\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
        AgentToolContext ctx = singleEntryCtx(request, null);
        JsonNode result = parse(HttpTargetSupport.execute(ReadHttpMessageTool.NAME, "{}", ctx));
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
                HttpTargetSupport.execute(
                        ReadHttpMessageTool.NAME, "{\"side\":\"request\",\"max_bytes\":" + full.length + "}", ctx));
        assertEquals("request", start.get("side").asText());
        assertEquals(full.length, start.get("total_bytes").asInt());
        assertEquals(hb, start.get("header_bytes").asInt());
        assertTrue(start.get("text").asText().startsWith("GET /p"));
        assertEquals(fullStr, start.get("text").asText());

        JsonNode body = parse(
                HttpTargetSupport.execute(
                        ReadHttpMessageTool.NAME,
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
        JsonNode result = parse(HttpTargetSupport.execute(ReadHttpMessageTool.NAME, "{\"side\":\"res\"}", ctx));
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
                HttpTargetSupport.execute(
                        ReadHttpMessageTool.NAME,
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
                HttpTargetSupport.execute(
                        ReadHttpMessageTool.NAME,
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
        String raw = HttpTargetSupport.execute(
                SearchHttpMessageTool.NAME,
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
                HttpTargetSupport.execute(
                        SearchHttpMessageTool.NAME, "{\"side\":\"request\",\"pattern\":\"Host:\",\"scope\":\"all\"}", ctx));
        assertTrue(all.get("match_count").asInt() > 1);
        JsonNode hdr = parse(
                HttpTargetSupport.execute(
                        SearchHttpMessageTool.NAME, "{\"side\":\"request\",\"pattern\":\"Host:\",\"scope\":\"headers\"}", ctx));
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
                HttpTargetSupport.execute(
                        SearchHttpMessageTool.NAME,
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
                HttpTargetSupport.execute(
                        SearchHttpMessageTool.NAME,
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
                HttpTargetSupport.execute(
                        SearchHttpMessageTool.NAME,
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
                HttpTargetSupport.execute(
                        SearchHttpMessageTool.NAME, "{\"side\":\"request\",\"pattern\":\"(\"}", ctx));
        assertTrue(r.get("error").asText().contains("regex") || r.get("error").asText().contains("Unclosed"));
    }

    @Test
    void searchPatternTooLong() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        stubToByteArray(request, "A".getBytes(StandardCharsets.ISO_8859_1));
        AgentToolContext ctx = singleEntryCtx(request, null);
        String p = "x".repeat(1025);
        String args = "{\"side\":\"request\",\"pattern\":" + JSON.writeValueAsString(p) + "}";
        JsonNode r = parse(HttpTargetSupport.execute(SearchHttpMessageTool.NAME, args, ctx));
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
                HttpTargetSupport.execute(
                        SearchHttpMessageTool.NAME, "{\"side\":\"request\",\"scope\":\"body\",\"pattern\":\"NOMATCH\"}", ctx));
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
                HttpTargetSupport.execute(
                        SearchHttpMessageTool.NAME, "{\"side\":\"request\",\"scope\":\"body\",\"pattern\":\".\"}", ctx));
        assertTrue(r.get("matches").get(0).has("match_base64"));
    }

    // ===== replace_in_http_request_body =====

    @Test
    void replaceInHttpRequestBody_singleReplacement() throws Exception {
        byte[] body = "hello world".getBytes(StandardCharsets.UTF_8);
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetSupport.execute(ReplaceInHttpRequestBodyTool.NAME,
                "{\"old_text\":\"hello\",\"new_text\":\"goodbye\"}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertEquals(11, result.get("bytes_before").asInt());
        assertEquals("goodbye world".getBytes(StandardCharsets.UTF_8).length,
                result.get("bytes_after").asInt());
        assertEquals(1, result.get("replacements").asInt());
        assertNotNull(applied.get(), "applyLiveRequest should have been called");
        assertEquals("goodbye world", bodyUtf8(applied.get()));
    }

    @Test
    void replaceInHttpRequestBody_oldTextNotFound_returnsError() throws Exception {
        byte[] body = "hello world".getBytes(StandardCharsets.UTF_8);
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetSupport.execute(ReplaceInHttpRequestBodyTool.NAME,
                "{\"old_text\":\"notpresent\",\"new_text\":\"x\"}", ctx));

        assertTrue(result.has("error"));
        assertTrue(result.get("error").asText().contains("not found"));
        assertNull(applied.get(), "request must not be committed when replacement fails");
    }

    @Test
    void replaceInHttpRequestBody_notUnique_returnsError() throws Exception {
        byte[] body = "aa bb aa".getBytes(StandardCharsets.UTF_8);
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetSupport.execute(ReplaceInHttpRequestBodyTool.NAME,
                "{\"old_text\":\"aa\",\"new_text\":\"cc\"}", ctx));

        assertTrue(result.has("error"));
        assertTrue(result.get("error").asText().contains("not unique"));
        assertNull(applied.get(), "request must not be committed when replacement is ambiguous");
    }

    @Test
    void replaceInHttpRequestBody_replaceAll_replacesEveryOccurrence() throws Exception {
        byte[] body = "aa bb aa cc aa".getBytes(StandardCharsets.UTF_8);
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetSupport.execute(ReplaceInHttpRequestBodyTool.NAME,
                "{\"old_text\":\"aa\",\"new_text\":\"zz\",\"replace_all\":true}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertEquals(3, result.get("replacements").asInt());
        assertNotNull(applied.get());
        assertEquals("zz bb zz cc zz", bodyUtf8(applied.get()));
    }

    @Test
    void replaceInHttpRequestBody_maxReplacements_limitsCount() throws Exception {
        byte[] body = "aa bb aa cc aa".getBytes(StandardCharsets.UTF_8);
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetSupport.execute(ReplaceInHttpRequestBodyTool.NAME,
                "{\"old_text\":\"aa\",\"new_text\":\"zz\",\"max_replacements\":2}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertEquals(2, result.get("replacements").asInt());
    }

    @Test
    void replaceInHttpRequestBody_binaryBody_returnsError() throws Exception {
        byte[] body = new byte[]{(byte) 0xFF, (byte) 0xFE, 0x00};
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetSupport.execute(ReplaceInHttpRequestBodyTool.NAME,
                "{\"old_text\":\"x\",\"new_text\":\"y\"}", ctx));

        assertTrue(result.has("error"));
    }

    @Test
    void replaceInHttpRequestBody_deleteOccurrence_newTextEmpty() throws Exception {
        byte[] body = "remove this text".getBytes(StandardCharsets.UTF_8);
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetSupport.execute(ReplaceInHttpRequestBodyTool.NAME,
                "{\"old_text\":\" this\",\"new_text\":\"\"}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertEquals(1, result.get("replacements").asInt());
        assertTrue(result.get("bytes_after").asInt() < result.get("bytes_before").asInt());
        assertEquals("remove  text", bodyUtf8(applied.get()));
    }

    // ===== patch_http_request_body_lines =====

    @Test
    void patchHttpRequestBodyLines_replacesMiddleLines() throws Exception {
        byte[] body = "line1\nline2\nline3".getBytes(StandardCharsets.UTF_8);
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetSupport.execute(PatchHttpRequestBodyLinesTool.NAME,
                "{\"start_line\":2,\"end_line\":2,\"content\":\"replaced line\"}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertEquals(3, result.get("lines_total_before").asInt());
        assertEquals(1, result.get("lines_replaced_span").asInt());
        assertEquals(1, result.get("lines_patched_in").asInt());
        assertNotNull(applied.get());
        assertEquals("line1\nreplaced line\nline3", bodyUtf8(applied.get()));
    }

    @Test
    void patchHttpRequestBodyLines_replacesMultipleLines() throws Exception {
        byte[] body = "a\nb\nc\nd".getBytes(StandardCharsets.UTF_8);
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetSupport.execute(PatchHttpRequestBodyLinesTool.NAME,
                "{\"start_line\":2,\"end_line\":3,\"content\":\"x\\ny\\nz\"}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertEquals(2, result.get("lines_replaced_span").asInt());
        assertEquals(3, result.get("lines_patched_in").asInt());
        assertNotNull(applied.get());
        assertEquals("a\nx\ny\nz\nd", bodyUtf8(applied.get()));
    }

    @Test
    void patchHttpRequestBodyLines_outOfBounds_returnsError() throws Exception {
        byte[] body = "line1\nline2".getBytes(StandardCharsets.UTF_8);
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetSupport.execute(PatchHttpRequestBodyLinesTool.NAME,
                "{\"start_line\":1,\"end_line\":5,\"content\":\"x\"}", ctx));

        assertTrue(result.has("error"));
        assertTrue(result.get("error").asText().contains("out of bounds"));
    }

    @Test
    void patchHttpRequestBodyLines_endBeforeStart_returnsError() throws Exception {
        byte[] body = "line1\nline2".getBytes(StandardCharsets.UTF_8);
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), body);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetSupport.execute(PatchHttpRequestBodyLinesTool.NAME,
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

        JsonNode result = parse(HttpTargetSupport.execute(SetHttpRequestBodyTool.NAME,
                "{\"body_utf8\":\"new body\"}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertEquals(8, result.get("bytes_before").asInt());
        assertEquals(8, result.get("bytes_after").asInt());
        assertNotNull(applied.get());
        assertEquals("new body", bodyUtf8(applied.get()));
    }

    @Test
    void setHttpRequestBody_base64() throws Exception {
        byte[] original = new byte[0];
        byte[] newBody = new byte[]{1, 2, 3, 4, 5};
        String encoded = Base64.getEncoder().encodeToString(newBody);
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), original);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetSupport.execute(SetHttpRequestBodyTool.NAME,
                "{\"body_base64\":\"" + encoded + "\"}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertEquals(0, result.get("bytes_before").asInt());
        assertEquals(5, result.get("bytes_after").asInt());
        assertNotNull(applied.get());
        assertArrayEquals(newBody, bodyBytes(applied.get()));
    }

    @Test
    void setHttpRequestBody_jsonObjectBody_serializedToCompactJson() throws Exception {
        byte[] original = new byte[0];
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), original);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);

        JsonNode result = parse(HttpTargetSupport.execute(SetHttpRequestBodyTool.NAME,
                "{\"body_utf8\":{\"key\":\"value\"}}", ctx));

        assertTrue(result.get("ok").asBoolean());
        assertTrue(result.get("bytes_after").asInt() > 0);
        assertNotNull(applied.get());
        assertEquals("{\"key\":\"value\"}", bodyUtf8(applied.get()));
    }

    @Test
    void setHttpRequestBody_bothPresent_returnsError() throws Exception {
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetSupport.execute(SetHttpRequestBodyTool.NAME,
                "{\"body_utf8\":\"text\",\"body_base64\":\"AA==\"}", ctx));

        assertTrue(result.has("error"));
    }

    @Test
    void setHttpRequestBody_neitherPresent_returnsError() throws Exception {
        HttpRequest request = req("POST", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);

        JsonNode result = parse(HttpTargetSupport.execute(SetHttpRequestBodyTool.NAME, "{}", ctx));

        assertTrue(result.has("error"));
    }

    // ===== apply_http_request_semantic_changes =====

    @Test
    void applySemantic_missingOperations_returnsStructuredHintAndExample() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        JsonNode result =
                parse(HttpTargetSupport.execute(ApplyHttpRequestSemanticChangesTool.NAME, "{}", ctx));

        assertTrue(result.has("error"));
        assertEquals(-1, result.get("op_index").asInt());
        assertTrue(result.has("hint"));
        assertTrue(result.has("example"));
        assertEquals(
                ApplyHttpRequestSemanticChangesTool.EXAMPLE_ARGS, result.get("example").asText());
    }

    @Test
    void applySemantic_setHeader_invokesWithHeaderAndCommits() throws Exception {
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);
        String args =
                "{\"operations\":[{\"type\":\"header\",\"action\":\"set\",\"key\":\"X-Custom\",\"value\":\"hello\"}]}";

        JsonNode result = parse(HttpTargetSupport.execute(ApplyHttpRequestSemanticChangesTool.NAME, args, ctx));

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

        JsonNode result = parse(HttpTargetSupport.execute(ApplyHttpRequestSemanticChangesTool.NAME, args, ctx));

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

        JsonNode result = parse(HttpTargetSupport.execute(ApplyHttpRequestSemanticChangesTool.NAME, args, ctx));

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

        JsonNode result = parse(HttpTargetSupport.execute(ApplyHttpRequestSemanticChangesTool.NAME, args, ctx));

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

        JsonNode result = parse(HttpTargetSupport.execute(ApplyHttpRequestSemanticChangesTool.NAME, args, ctx));

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

        JsonNode result = parse(HttpTargetSupport.execute(ApplyHttpRequestSemanticChangesTool.NAME, args, ctx));

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

        JsonNode result = parse(HttpTargetSupport.execute(ApplyHttpRequestSemanticChangesTool.NAME, args, ctx));

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

        parse(HttpTargetSupport.execute(ApplyHttpRequestSemanticChangesTool.NAME, args, ctx));

        httpServiceMock.verify(() -> HttpService.httpService("example.com", 80, false));
    }

    @Test
    void applySemantic_url_noScheme_returnsError() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        String args =
                "{\"operations\":[{\"type\":\"url\",\"action\":\"set\",\"key\":\"\",\"value\":\"//example.com/path\"}]}";

        JsonNode result = parse(HttpTargetSupport.execute(ApplyHttpRequestSemanticChangesTool.NAME, args, ctx));

        assertTrue(result.has("error"));
    }

    @Test
    void applySemantic_url_nonHttpScheme_returnsError() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        String args =
                "{\"operations\":[{\"type\":\"url\",\"action\":\"set\",\"key\":\"\",\"value\":\"ftp://example.com/file\"}]}";

        JsonNode result = parse(HttpTargetSupport.execute(ApplyHttpRequestSemanticChangesTool.NAME, args, ctx));

        assertTrue(result.has("error"));
    }

    @Test
    void applySemantic_url_invalidUrl_returnsError() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        String args =
                "{\"operations\":[{\"type\":\"url\",\"action\":\"set\",\"key\":\"\",\"value\":\"https://\"}]}";

        JsonNode result = parse(HttpTargetSupport.execute(ApplyHttpRequestSemanticChangesTool.NAME, args, ctx));

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

        String raw = HttpTargetSupport.execute(ApplyHttpRequestSemanticChangesTool.NAME, args, ctx);
        JsonNode result = parse(raw);
        assertFalse(result.has("error"), "unexpected error: " + raw);

        assertTrue(result.get("ok").asBoolean());
        assertEquals(3, result.get("operations_applied").asInt());
        assertNotNull(applied.get());
        verify(request).withHeader("X-Test", "1");
        assertTrue(bodyUtf8(applied.get()).contains("\"a\":null"), bodyUtf8(applied.get()));
        assertTrue(bodyUtf8(applied.get()).contains("\"b\":2"), bodyUtf8(applied.get()));
    }

    @Test
    void applySemantic_jsonRemoveField() throws Exception {
        AtomicReference<HttpRequest> applied = new AtomicReference<>();
        byte[] startBody = "{\"a\":1,\"b\":2}".getBytes(StandardCharsets.UTF_8);
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), startBody);
        AgentToolContext ctx = singleEntryCtx(request, null, applied::set);
        String args = "{\"operations\":[{\"type\":\"json\",\"action\":\"remove\",\"path\":\"/a\"}]}";

        String raw2 = HttpTargetSupport.execute(ApplyHttpRequestSemanticChangesTool.NAME, args, ctx);
        JsonNode result = parse(raw2);
        assertFalse(result.has("error"), "unexpected error: " + raw2);
        assertTrue(result.get("ok").asBoolean());
        assertNotNull(applied.get());
        assertEquals("{\"b\":2}", bodyUtf8(applied.get()));
    }

    @Test
    void applySemantic_jsonOnNonJsonBody_includesOpIndex() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), "not json".getBytes(StandardCharsets.UTF_8));
        AgentToolContext ctx = singleEntryCtx(request, null);
        String args = "{\"operations\":[{\"type\":\"json\",\"action\":\"set\",\"path\":\"/x\",\"value\":1}]}";

        JsonNode result = parse(HttpTargetSupport.execute(ApplyHttpRequestSemanticChangesTool.NAME, args, ctx));
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

        JsonNode result = parse(HttpTargetSupport.execute(ApplyHttpRequestSemanticChangesTool.NAME, args, ctx));
        assertTrue(result.has("error"));
        assertTrue(result.get("error").asText().toLowerCase().contains("xml")
                || result.get("error").asText().toLowerCase().contains("form"), result.toString());
    }

    @Test
    void applySemantic_methodWithNonEmptyKey_rejects() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        String args = "{\"operations\":[{\"type\":\"method\",\"action\":\"set\",\"key\":\"m\",\"value\":\"GET\"}]}";

        JsonNode result = parse(HttpTargetSupport.execute(ApplyHttpRequestSemanticChangesTool.NAME, args, ctx));
        assertTrue(result.has("error"));
        assertTrue(result.get("error").asText().contains("key must be empty") || result.get("error").asText().contains("empty"));
    }

    @Test
    void applySemantic_methodRemove_rejects() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        String args = "{\"operations\":[{\"type\":\"method\",\"action\":\"remove\"}]}";

        JsonNode result = parse(HttpTargetSupport.execute(ApplyHttpRequestSemanticChangesTool.NAME, args, ctx));
        assertTrue(result.has("error"));
    }

    @Test
    void applySemantic_removeWithExplicitValue_rejects() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);
        String args = "{\"operations\":[{\"type\":\"header\",\"action\":\"remove\",\"key\":\"A\",\"value\":\"x\"}]}";

        JsonNode result = parse(HttpTargetSupport.execute(ApplyHttpRequestSemanticChangesTool.NAME, args, ctx));
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

        JsonNode result = parse(HttpTargetSupport.execute(ApplyHttpRequestSemanticChangesTool.NAME, args, ctx));
        assertTrue(result.has("error"));
        assertEquals(0, applierCount.get());
    }

    // ===== send_current_http_request =====

    @Test
    void sendCurrentHttpRequest_returnsStatusCode() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AtomicReference<HttpRequest> sent = new AtomicReference<>();
        Callable<Integer> sender =
                () -> {
                    sent.set(request);
                    return 200;
                };
        AgentToolContext ctx = new AgentToolContext(defaultTarget(), 42, 0,
                List.of(new AgentToolContext.HistoryEntryInfo(0, "now", "GET x.com")),
                idx -> request, idx -> null, req -> {}, sender);

        JsonNode result = parse(HttpTargetSupport.execute(SendCurrentHttpRequestTool.NAME, "{}", ctx));

        assertEquals(200, result.get("status_code").asInt());
        assertFalse(result.has("error"));
        assertNotNull(sent.get(), "sender must actually run the live request");
    }

    @Test
    void sendCurrentHttpRequest_nullSender_returnsError() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        AgentToolContext ctx = singleEntryCtx(request, null);  // sender is null

        JsonNode result = parse(HttpTargetSupport.execute(SendCurrentHttpRequestTool.NAME, "{}", ctx));

        assertTrue(result.has("error"));
    }

    @Test
    void sendCurrentHttpRequest_senderReturns404() throws Exception {
        HttpRequest request = req("GET", "https://x.com/", "/", List.of(), new byte[0]);
        Callable<Integer> sender = () -> 404;
        AgentToolContext ctx = new AgentToolContext(defaultTarget(), 42, 0,
                List.of(new AgentToolContext.HistoryEntryInfo(0, "now", "GET x.com")),
                idx -> request, idx -> null, req -> {}, sender);

        JsonNode result = parse(HttpTargetSupport.execute(SendCurrentHttpRequestTool.NAME, "{}", ctx));

        assertEquals(404, result.get("status_code").asInt());
    }

    // ===== humanToolUsage =====

    @Test
    void humanToolUsage_getCurrentAndReadMessages() {
        HumanToolUsage t1 = HttpTargetSupport.humanToolUsage(GetCurrentHttpTargetTool.NAME, "{}", 0);
        assertTrue(t1.detail().isEmpty());
        assertFalse(t1.title().isBlank());
        HumanToolUsage t2 =
                HttpTargetSupport.humanToolUsage(ReadHttpMessageTool.NAME, "{\"side\":\"request\"}", 0);
        assertTrue(t2.title().contains("offset 0, max 4096"));
        assertTrue(t2.detail().isEmpty());
        HumanToolUsage t3 = HttpTargetSupport.humanToolUsage(
                SearchHttpMessageTool.NAME, "{\"side\":\"request\",\"pattern\":\"^Host:\"}", 0);
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
        HumanToolUsage usage =
                HttpTargetSupport.humanToolUsage(ApplyHttpRequestSemanticChangesTool.NAME, json, 0);

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
        HumanToolUsage usage = HttpTargetSupport.humanToolUsage(
                ReplaceInHttpRequestBodyTool.NAME,
                "{\"old_text\":\"foo\",\"new_text\":\"bar\"}", 0);

        assertEquals("Replace text in request body", usage.title());
        assertTrue(usage.detail().contains("foo"));
        assertTrue(usage.detail().contains("bar"));
    }

    @Test
    void humanToolUsage_replaceBodyWithReplaceAll_showsAllOccurrencesNote() {
        HumanToolUsage usage = HttpTargetSupport.humanToolUsage(
                ReplaceInHttpRequestBodyTool.NAME,
                "{\"old_text\":\"x\",\"new_text\":\"y\",\"replace_all\":true}", 0);

        assertTrue(usage.detail().contains("all"));
    }

    @Test
    void humanToolUsage_patchLines_showsLineRange() {
        HumanToolUsage usage = HttpTargetSupport.humanToolUsage(
                PatchHttpRequestBodyLinesTool.NAME,
                "{\"start_line\":3,\"end_line\":5,\"content\":\"new content\"}", 0);

        assertEquals("Patch request body line range", usage.title());
        assertTrue(usage.detail().contains("3"));
        assertTrue(usage.detail().contains("5"));
    }

    @Test
    void humanToolUsage_historyIndexSuffix_omittedWhenSameAsViewer() {
        String args = "{\"side\":\"request\",\"history_index\":3}";
        HumanToolUsage withSuffix = HttpTargetSupport.humanToolUsage(
                ReadHttpMessageTool.NAME, args, 0);
        HumanToolUsage withoutSuffix = HttpTargetSupport.humanToolUsage(
                ReadHttpMessageTool.NAME, args, 3);

        assertTrue(withSuffix.title().contains("#3"),
                "should include history index when different from viewer: " + withSuffix.title());
        assertFalse(withoutSuffix.title().contains("#3"),
                "should omit history index when same as viewer: " + withoutSuffix.title());
    }

    @Test
    void humanToolUsage_sendRequest_hasTitleAndDetail() {
        HumanToolUsage usage = HttpTargetSupport.humanToolUsage(
                SendCurrentHttpRequestTool.NAME, "{}", 0);

        assertFalse(usage.title().isBlank());
        assertFalse(usage.detail().isBlank());
    }

    @Test
    void humanToolUsage_setBody_noDetailToAvoidDuplicatingLargeBody() {
        HumanToolUsage usage = HttpTargetSupport.humanToolUsage(
                SetHttpRequestBodyTool.NAME, "{\"body_utf8\":\"large body content\"}", 0);

        assertFalse(usage.title().isBlank());
        assertTrue(usage.detail().isEmpty(), "set_http_request_body detail should be empty");
    }

    @Test
    void humanToolUsage_unknownTool_returnsNull() {
        assertNull(HttpTargetSupport.humanToolUsage("no_such_tool", "{}", 0));
    }

    @Test
    void humanToolUsage_unknownTool_returnsDefaultTitleFromDispatcher() {
        HumanToolUsage usage = ToolHumanUsage.forTool("no_such_tool", "{}", 0);
        assertEquals("Working…", usage.title());
    }

    @Test
    void humanToolUsage_invalidArgs_doesNotThrow() {
        assertDoesNotThrow(() ->
                HttpTargetSupport.humanToolUsage(ApplyHttpRequestSemanticChangesTool.NAME, "INVALID{JSON", 0));
    }

    @Test
    void humanToolUsage_nodeSuffix_whenUiSelected_appendsId() {
        HumanToolUsage u =
                HttpTargetSupport.humanToolUsage(GetCurrentHttpTargetTool.NAME, "{}", 0, 99);
        assertTrue(u.title().contains("node id 99"), u.title());
    }

    @Test
    void humanToolUsage_nodeSuffix_omittedWhenRequestNodeIdMatchesUi() {
        HumanToolUsage u =
                HttpTargetSupport.humanToolUsage(
                        ReadHttpMessageTool.NAME,
                        "{\"side\":\"request\",\"request_node_id\":12}",
                        0,
                        12);
        assertFalse(u.title().contains("node id"), u.title());
    }

    @Test
    void humanToolUsage_nodeSuffix_whenNoArg_usesUiSelectedId() {
        HumanToolUsage u =
                HttpTargetSupport.humanToolUsage(
                        ReadHttpMessageTool.NAME, "{\"side\":\"request\"}", 0, 12);
        assertTrue(u.title().contains("node id 12"), u.title());
    }

    @Test
    void humanToolUsage_nodeSuffix_showsExplicitWhenDifferentFromUi() {
        HumanToolUsage u =
                HttpTargetSupport.humanToolUsage(
                        ReadHttpMessageTool.NAME,
                        "{\"side\":\"request\",\"request_node_id\":99}",
                        0,
                        12);
        assertTrue(u.title().contains("node id 99"), u.title());
    }

    @Test
    void humanToolUsage_searchTabs_hasNoNodeSuffix() {
        HumanToolUsage u =
                HttpTargetSupport.humanToolUsage(SearchTabsTool.NAME, "{}", 0, 12);
        assertFalse(u.title().contains("node id"), u.title());
    }

    // ===== tryPreviewRequestMutation (in-memory, no editor commit) =====

    @Test
    void tryPreview_replace_changesBody() {
        byte[] body = "a foo c".getBytes(StandardCharsets.UTF_8);
        HttpRequest request = req("GET", "https://a/x", "/x", null, body);
        HttpRequest out =
                HttpTargetSupport.tryPreviewRequestMutation(
                        ReplaceInHttpRequestBodyTool.NAME,
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
                HttpTargetSupport.tryPreviewRequestMutation(
                        ReadHttpMessageTool.NAME, "{\"side\":\"request\"}", req("GET", "https://a/x", "/x", null, new byte[0])));
    }

    @Test
    void search_tabs_invokesBridge() throws Exception {
        TreepeaterTabAgentBridge bridge =
                new TreepeaterTabAgentBridge() {
                    @Override
                    public AgentToolContext contextForAgent(OptionalInt requestNodeId) {
                        return null;
                    }

                    @Override
                    public String searchTabs(int offset, int pageSize, String queryOrNull) {
                        assertEquals(0, offset);
                        assertEquals(5, pageSize);
                        assertEquals("q1", queryOrNull);
                        return TabListingFormatter.formatSearchTabsResponse(
                                2,
                                0,
                                5,
                                true,
                                List.of(new SearchTabRow(7, "t", true, "GET", "http://x", false)));
                    }
                };
        JsonNode r =
                parse(
                        HttpTargetSupport.execute(
                                SearchTabsTool.NAME, "{\"offset\":0,\"page_size\":5,\"query\":\"q1\"}", bridge));
        assertEquals(2, r.get("total").asInt());
        assertTrue(r.get("has_more").asBoolean());
        assertEquals(1, r.get("next_offset").asInt());
        assertEquals(7, r.get("tabs").get(0).get("request_node_id").asInt());
    }

    @Test
    void copy_treepeater_node_invokesBridge() throws Exception {
        TreepeaterTabAgentBridge bridge =
                new TreepeaterTabAgentBridge() {
                    @Override
                    public AgentToolContext contextForAgent(OptionalInt requestNodeId) {
                        return null;
                    }

                    @Override
                    public String searchTabs(int offset, int pageSize, String queryOrNull) {
                        return "{}";
                    }

                    @Override
                    public String copyTreepeaterNode(
                            int sourceRequestNodeId, String name, SiblingCopyPlacement placement) {
                        assertEquals(5, sourceRequestNodeId);
                        assertEquals("Variant A", name);
                        assertEquals(SiblingCopyPlacement.AFTER_SOURCE, placement);
                        return TabListingFormatter.formatCopyTreepeaterNodeResponse(42, name);
                    }
                };
        JsonNode r =
                parse(
                        HttpTargetSupport.execute(
                                CopyTreepeaterNodeTool.NAME,
                                "{\"request_node_id\":5,\"name\":\"Variant A\"}",
                                bridge));
        assertEquals(42, r.get("request_node_id").asInt());
        assertEquals("Variant A", r.get("name").asText());
    }

    @Test
    void copy_treepeater_node_requiresRequestNodeId() throws Exception {
        TreepeaterTabAgentBridge bridge =
                new TreepeaterTabAgentBridge() {
                    @Override
                    public AgentToolContext contextForAgent(OptionalInt requestNodeId) {
                        return null;
                    }

                    @Override
                    public String searchTabs(int offset, int pageSize, String queryOrNull) {
                        return "{}";
                    }
                };
        JsonNode r =
                parse(
                        HttpTargetSupport.execute(
                                CopyTreepeaterNodeTool.NAME, "{\"name\":\"x\"}", bridge));
        assertEquals("request_node_id required", r.get("error").asText());
    }

    @Test
    void copy_treepeater_node_requiresName() throws Exception {
        TreepeaterTabAgentBridge bridge =
                new TreepeaterTabAgentBridge() {
                    @Override
                    public AgentToolContext contextForAgent(OptionalInt requestNodeId) {
                        return null;
                    }

                    @Override
                    public String searchTabs(int offset, int pageSize, String queryOrNull) {
                        return "{}";
                    }
                };
        JsonNode r =
                parse(
                        HttpTargetSupport.execute(
                                CopyTreepeaterNodeTool.NAME, "{\"request_node_id\":5}", bridge));
        assertEquals("name required", r.get("error").asText());
    }

    @Test
    void copy_treepeater_node_blankNameRejected() throws Exception {
        TreepeaterTabAgentBridge bridge =
                new TreepeaterTabAgentBridge() {
                    @Override
                    public AgentToolContext contextForAgent(OptionalInt requestNodeId) {
                        return null;
                    }

                    @Override
                    public String searchTabs(int offset, int pageSize, String queryOrNull) {
                        return "{}";
                    }
                };
        JsonNode r =
                parse(
                        HttpTargetSupport.execute(
                                CopyTreepeaterNodeTool.NAME,
                                "{\"request_node_id\":5,\"name\":\"   \"}",
                                bridge));
        assertEquals("name required", r.get("error").asText());
    }

    @Test
    void copy_treepeater_node_forwardsPlacement() throws Exception {
        TreepeaterTabAgentBridge bridge =
                new TreepeaterTabAgentBridge() {
                    @Override
                    public AgentToolContext contextForAgent(OptionalInt requestNodeId) {
                        return null;
                    }

                    @Override
                    public String searchTabs(int offset, int pageSize, String queryOrNull) {
                        return "{}";
                    }

                    @Override
                    public String copyTreepeaterNode(
                            int sourceRequestNodeId, String name, SiblingCopyPlacement placement) {
                        assertEquals(SiblingCopyPlacement.PARENT_BOTTOM, placement);
                        return TabListingFormatter.formatCopyTreepeaterNodeResponse(99, name);
                    }
                };
        JsonNode r =
                parse(
                        HttpTargetSupport.execute(
                                CopyTreepeaterNodeTool.NAME,
                                "{\"request_node_id\":5,\"name\":\"x\",\"placement\":\"bottom\"}",
                                bridge));
        assertEquals(99, r.get("request_node_id").asInt());
    }

    @Test
    void copy_treepeater_node_rejectsInvalidPlacement() throws Exception {
        TreepeaterTabAgentBridge bridge =
                new TreepeaterTabAgentBridge() {
                    @Override
                    public AgentToolContext contextForAgent(OptionalInt requestNodeId) {
                        return null;
                    }

                    @Override
                    public String searchTabs(int offset, int pageSize, String queryOrNull) {
                        return "{}";
                    }
                };
        JsonNode r =
                parse(
                        HttpTargetSupport.execute(
                                CopyTreepeaterNodeTool.NAME,
                                "{\"request_node_id\":5,\"name\":\"x\",\"placement\":\"middle\"}",
                                bridge));
        assertEquals("placement must be after, top, or bottom", r.get("error").asText());
    }

    @Test
    void execute_requestNodeId_selectsContext() throws Exception {
        AgentToolContext ctxGet =
                new AgentToolContext(
                        new HttpTargetSnapshot("https", "a.com", 443, true, "GET", "https://a.com/", "/"),
                        1,
                        0,
                        List.of(new AgentToolContext.HistoryEntryInfo(0, "", "")),
                        idx -> req("GET", "https://a.com/", "/", List.of(), new byte[0]),
                        idx -> null,
                        r -> {},
                        null);
        AgentToolContext ctxPost =
                new AgentToolContext(
                        new HttpTargetSnapshot("https", "b.com", 443, true, "POST", "https://b.com/p", "/p"),
                        2,
                        0,
                        List.of(new AgentToolContext.HistoryEntryInfo(0, "", "")),
                        idx -> req("POST", "https://b.com/p", "/p", List.of(), new byte[0]),
                        idx -> null,
                        r -> {},
                        null);
        TreepeaterTabAgentBridge bridge =
                new TreepeaterTabAgentBridge() {
                    @Override
                    public AgentToolContext contextForAgent(OptionalInt requestNodeId) {
                        if (requestNodeId.isPresent() && requestNodeId.getAsInt() == 2) {
                            return ctxPost;
                        }
                        return ctxGet;
                    }

                    @Override
                    public String searchTabs(int offset, int pageSize, String queryOrNull) {
                        return "{}";
                    }
                };
        JsonNode g2 =
                parse(HttpTargetSupport.execute(GetCurrentHttpTargetTool.NAME, "{\"request_node_id\":2}", bridge));
        assertEquals("POST", g2.get("method").asText());
        JsonNode gD = parse(HttpTargetSupport.execute(GetCurrentHttpTargetTool.NAME, "{}", bridge));
        assertEquals("GET", gD.get("method").asText());
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
        JsonNode result = parse(ToolResults.permissionDenied());
        assertTrue(result.has("error"));
        assertEquals("permission denied", result.get("error").asText());
    }
}
