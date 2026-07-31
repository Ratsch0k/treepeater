package treepeater.api.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import treepeater.ImportTestSupport;
import treepeater.TreepeaterModel;
import treepeater.api.TreepeaterService;
import treepeater.api.TreepeaterToolRegistry;
import treepeater.api.server.McpDispatcher.Revision;
import treepeater.api.tools.StatusTools;
import treepeater.api.tools.TreeTools;

/** Wire-level behaviour of the MCP endpoint for both the handshake and stateless spec revisions. */
class McpDispatcherTest extends ImportTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpDispatcher dispatcher;

    @BeforeEach
    void buildDispatcher() {
        TreepeaterModel model = new TreepeaterModel();
        // A null bridge keeps the editor-centric tools out, leaving the headless groups under test.
        this.dispatcher =
                new McpDispatcher(TreepeaterToolRegistry.create(new TreepeaterService(model), null));
    }

    private JsonNode call(String body, Revision revision) throws Exception {
        String response = this.dispatcher.handle(body, revision, null, null);
        assertNotNull(response, "expected a response for " + body);
        return MAPPER.readTree(response);
    }

    private static String request(String method, String params) {
        String paramsPart = params != null ? ",\"params\":" + params : "";
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\"" + paramsPart + "}";
    }

    @Test
    void initializeIsAnsweredOnTheLegacyRevision() throws Exception {
        JsonNode response = call(request("initialize", null), Revision.LEGACY);

        assertEquals("2.0", response.get("jsonrpc").asText());
        assertEquals(1, response.get("id").asInt());
        assertEquals(McpDispatcher.PROTOCOL_2025, response.get("result").get("protocolVersion").asText());
        assertEquals("treepeater", response.get("result").get("serverInfo").get("name").asText());
    }

    @Test
    void initializeIsRejectedOnTheStatelessRevision() throws Exception {
        JsonNode response = call(request("initialize", null), Revision.STATELESS);

        assertEquals(-32601, response.get("error").get("code").asInt());
    }

    @Test
    void discoverAdvertisesBothProtocolVersions() throws Exception {
        JsonNode result = call(request("server/discover", null), Revision.STATELESS).get("result");

        JsonNode versions = result.get("protocolVersions");
        assertEquals(2, versions.size());
        assertEquals(McpDispatcher.PROTOCOL_2026, versions.get(0).asText());
        assertEquals(McpDispatcher.PROTOCOL_2025, versions.get(1).asText());
    }

    @Test
    void toolsListExposesTheRegistryWithParsedSchemas() throws Exception {
        JsonNode result = call(request("tools/list", null), Revision.LEGACY).get("result");

        JsonNode tools = result.get("tools");
        assertTrue(tools.size() >= 2, "expected the headless tool groups");
        boolean sawListTree = false;
        for (JsonNode tool : tools) {
            if (TreeTools.LIST_TREE.equals(tool.get("name").asText())) {
                sawListTree = true;
                // The schema must travel as a real object, not a string, or clients cannot validate calls.
                assertTrue(tool.get("inputSchema").isObject());
                assertEquals("object", tool.get("inputSchema").get("type").asText());
            }
        }
        assertTrue(sawListTree, "list_tree missing from tools/list");
    }

    @Test
    void toolsListAddsCacheHintsOnlyOnTheStatelessRevision() throws Exception {
        JsonNode legacy = call(request("tools/list", null), Revision.LEGACY).get("result");
        assertFalse(legacy.has("ttlMs"));
        assertFalse(legacy.has("cacheScope"));

        JsonNode stateless = call(request("tools/list", null), Revision.STATELESS).get("result");
        assertEquals(60000, stateless.get("ttlMs").asInt());
        assertEquals("session", stateless.get("cacheScope").asText());
    }

    @Test
    void toolsCallWrapsTheToolResultAsTextContent() throws Exception {
        String body =
                request("tools/call", "{\"name\":\"" + StatusTools.LIST_STATUSES + "\",\"arguments\":{}}");

        JsonNode result = call(body, Revision.STATELESS).get("result");

        assertFalse(result.get("isError").asBoolean());
        JsonNode content = result.get("content");
        assertEquals(1, content.size());
        assertEquals("text", content.get(0).get("type").asText());
        JsonNode payload = MAPPER.readTree(content.get(0).get("text").asText());
        assertTrue(payload.get("statuses").size() >= 1);
        assertEquals("DEFAULT", payload.get("default_id").asText());
    }

    @Test
    void toolsCallOmittingArgumentsIsTreatedAsAnEmptyObject() throws Exception {
        String body = request("tools/call", "{\"name\":\"" + StatusTools.LIST_STATUSES + "\"}");

        JsonNode result = call(body, Revision.STATELESS).get("result");

        assertFalse(result.get("isError").asBoolean());
    }

    @Test
    void aFailingToolIsASuccessfulCallWithIsErrorSet() throws Exception {
        String body = request("tools/call", "{\"name\":\"no_such_tool\",\"arguments\":{}}");

        JsonNode response = call(body, Revision.STATELESS);

        // A tool-level failure must not become a JSON-RPC error; only malformed requests do.
        assertFalse(response.has("error"));
        assertTrue(response.get("result").get("isError").asBoolean());
    }

    @Test
    void toolsCallWithoutANameIsInvalidParams() throws Exception {
        JsonNode response = call(request("tools/call", "{\"arguments\":{}}"), Revision.STATELESS);

        assertEquals(-32602, response.get("error").get("code").asInt());
    }

    @Test
    void unknownMethodIsMethodNotFound() throws Exception {
        JsonNode response = call(request("resources/list", null), Revision.STATELESS);

        assertEquals(-32601, response.get("error").get("code").asInt());
    }

    @Test
    void notificationsProduceNoResponse() {
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";

        assertNull(this.dispatcher.handle(body, Revision.LEGACY, null, null));
    }

    @Test
    void headerAndBodyMethodMismatchIsRejected() throws Exception {
        String response =
                this.dispatcher.handle(request("ping", null), Revision.STATELESS, "tools/list", null);

        assertEquals(-32020, MAPPER.readTree(response).get("error").get("code").asInt());
    }

    @Test
    void headerAndBodyToolNameMismatchIsRejected() throws Exception {
        String body = request("tools/call", "{\"name\":\"" + StatusTools.LIST_STATUSES + "\"}");

        String response = this.dispatcher.handle(body, Revision.STATELESS, "tools/call", TreeTools.LIST_TREE);

        assertEquals(-32020, MAPPER.readTree(response).get("error").get("code").asInt());
    }

    @Test
    void matchingHeadersAreAccepted() throws Exception {
        String body = request("tools/call", "{\"name\":\"" + StatusTools.LIST_STATUSES + "\"}");

        String response =
                this.dispatcher.handle(body, Revision.STATELESS, "tools/call", StatusTools.LIST_STATUSES);

        assertFalse(MAPPER.readTree(response).has("error"));
    }

    @Test
    void malformedJsonIsAParseError() throws Exception {
        JsonNode response = MAPPER.readTree(this.dispatcher.handle("{", Revision.LEGACY, null, null));

        assertEquals(-32700, response.get("error").get("code").asInt());
        assertTrue(response.get("id").isNull());
    }

    @Test
    void aBatchAnswersOnlyTheNonNotificationEntries() throws Exception {
        String body =
                "[{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"ping\"},"
                        + "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}]";

        JsonNode response = call(body, Revision.STATELESS);

        assertTrue(response.isArray());
        assertEquals(1, response.size());
        assertEquals(7, response.get(0).get("id").asInt());
    }

    @Test
    void aStringIdKeepsItsJsonType() throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":\"abc-1\",\"method\":\"ping\"}";

        JsonNode response = call(body, Revision.STATELESS);

        assertTrue(response.get("id").isTextual());
        assertEquals("abc-1", response.get("id").asText());
    }

    @Test
    void revisionIsDetectedFromTheHeaderOrTheMetaProtocolVersion() {
        assertEquals(Revision.STATELESS, McpDispatcher.detectRevision(request("ping", null), "ping"));
        assertEquals(Revision.LEGACY, McpDispatcher.detectRevision(request("ping", null), null));

        String withMeta =
                request(
                        "tools/list",
                        "{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2026-07-28\"}}");
        assertEquals(Revision.STATELESS, McpDispatcher.detectRevision(withMeta, null));

        String oldMeta =
                request(
                        "tools/list",
                        "{\"_meta\":{\"io.modelcontextprotocol/protocolVersion\":\"2025-11-25\"}}");
        assertEquals(Revision.LEGACY, McpDispatcher.detectRevision(oldMeta, null));
    }
}
