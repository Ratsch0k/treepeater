package treepeater.api.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import treepeater.ImportTestSupport;
import treepeater.MontoyaFactoryStub;
import treepeater.TreepeaterModel;
import treepeater.api.TreepeaterService;
import treepeater.api.TreepeaterToolRegistry;
import treepeater.api.tools.importing.ImportHttpRequestTool;
import treepeater.api.tools.tree.CreateFolderTool;
import treepeater.api.tools.tree.RenameNodeTool;

/** End-to-end checks over a real loopback socket: routing, authentication and origin handling. */
class TreepeaterHttpServerTest extends ImportTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient CLIENT =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private TreepeaterModel model;
    private TreepeaterHttpServer server;
    private String token;
    private String base;

    @BeforeEach
    void startServer() throws Exception {
        MontoyaFactoryStub.install();
        this.settings.setApiPort(freePort());
        this.settings.setApiAllowWrite(true);
        this.token = this.settings.getOrCreateApiToken();

        this.model = new TreepeaterModel();
        TreepeaterService service = new TreepeaterService(this.model);
        this.server = new TreepeaterHttpServer(TreepeaterToolRegistry.create(service, null), service);
        assertTrue(this.server.start(), "server did not start");
        this.base = "http://127.0.0.1:" + this.server.port();
    }

    @AfterEach
    void stopServer() {
        if (this.server != null) {
            this.server.stop();
        }
        MontoyaFactoryStub.uninstall();
    }

    private static int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    // ------------------------------------------------------------------ helpers

    private HttpRequest.Builder authed(String path) {
        return HttpRequest.newBuilder(URI.create(this.base + path))
                .header("Authorization", "Bearer " + this.token);
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return send(authed(path).GET().build());
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return send(authed(path).POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    private static JsonNode json(HttpResponse<String> response) throws Exception {
        return MAPPER.readTree(response.body());
    }

    // ----------------------------------------------------------------- lifecycle

    @Test
    void theServerBindsAndReportsItsPort() {
        assertTrue(this.server.isRunning());
        assertEquals(this.settings.getApiPort(), this.server.port());
    }

    @Test
    void startingTwiceIsANoOpAndStoppingIsIdempotent() {
        assertTrue(this.server.start());

        this.server.stop();
        this.server.stop();

        assertFalse(this.server.isRunning());
    }

    @Test
    void restartKeepsServingOnTheSamePort() throws Exception {
        this.server.restart();

        assertTrue(this.server.isRunning());
        assertEquals(200, get("/api/health").statusCode());
    }

    @Test
    void aTakenPortIsReportedRatherThanThrown() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0)) {
            this.settings.setApiPort(occupied.getLocalPort());
            TreepeaterService service = new TreepeaterService(this.model);
            TreepeaterHttpServer blocked =
                    new TreepeaterHttpServer(TreepeaterToolRegistry.create(service, null), service);

            assertFalse(blocked.start());
            assertFalse(blocked.isRunning());
        }
    }

    // -------------------------------------------------------------------- auth

    @Test
    void requestsWithoutATokenAreRejected() throws Exception {
        HttpResponse<String> response =
                send(HttpRequest.newBuilder(URI.create(this.base + "/api/health")).GET().build());

        assertEquals(401, response.statusCode());
        assertEquals("unauthorized", json(response).get("error").asText());
    }

    @Test
    void requestsWithTheWrongTokenAreRejected() throws Exception {
        HttpResponse<String> response =
                send(HttpRequest.newBuilder(URI.create(this.base + "/api/health"))
                        .header("Authorization", "Bearer not-the-token")
                        .GET()
                        .build());

        assertEquals(401, response.statusCode());
    }

    @Test
    void theBearerPrefixIsMatchedCaseInsensitively() throws Exception {
        HttpResponse<String> response =
                send(HttpRequest.newBuilder(URI.create(this.base + "/api/health"))
                        .header("Authorization", "bearer " + this.token)
                        .GET()
                        .build());

        assertEquals(200, response.statusCode());
    }

    @Test
    void theMcpEndpointIsAuthenticatedToo() throws Exception {
        HttpResponse<String> response =
                send(HttpRequest.newBuilder(URI.create(this.base + "/mcp"))
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
                        .build());

        assertEquals(401, response.statusCode());
    }

    // ------------------------------------------------------------------ origin

    @Test
    void aCrossSiteOriginIsRefused() throws Exception {
        HttpResponse<String> response =
                send(authed("/api/health").header("Origin", "https://evil.example.com").GET().build());

        assertEquals(403, response.statusCode());
        assertEquals("forbidden origin", json(response).get("error").asText());
    }

    @Test
    void aLoopbackOriginIsAccepted() throws Exception {
        HttpResponse<String> response =
                send(authed("/api/health")
                        .header("Origin", "http://localhost:3000")
                        .GET()
                        .build());

        assertEquals(200, response.statusCode());
    }

    @Test
    void theOriginCheckRunsBeforeAuthentication() throws Exception {
        // A cross-site page must be turned away whether or not it guessed a token.
        HttpResponse<String> response =
                send(HttpRequest.newBuilder(URI.create(this.base + "/api/health"))
                        .header("Origin", "https://evil.example.com")
                        .GET()
                        .build());

        assertEquals(403, response.statusCode());
    }

    // -------------------------------------------------------------- rest routes

    @Test
    void healthDescribesTheServer() throws Exception {
        JsonNode body = json(get("/api/health"));

        assertEquals("Treepeater", body.get("name").asText());
        assertEquals("v1", body.get("api_version").asText());
        assertTrue(body.get("tool_count").asInt() > 0);
        assertTrue(body.get("allow_write").asBoolean());
        assertEquals(2, body.get("mcp_protocol_versions").size());
    }

    @Test
    void theToolCatalogueCarriesSchemasAndActionLevels() throws Exception {
        JsonNode tools = json(get("/api/tools")).get("tools");

        boolean sawCreateFolder = false;
        for (JsonNode tool : tools) {
            if (CreateFolderTool.NAME.equals(tool.get("name").asText())) {
                sawCreateFolder = true;
                assertEquals("WRITE", tool.get("action_level").asText());
                assertTrue(tool.get("input_schema").isObject());
            }
        }
        assertTrue(sawCreateFolder, "create_folder missing from the catalogue");
    }

    @Test
    void aToolCanBeInvokedOverRest() throws Exception {
        HttpResponse<String> response = post("/api/tools/create_folder", "{\"name\":\"API\"}");

        assertEquals(200, response.statusCode());
        assertEquals("API", json(response).get("name").asText());
        assertNotNull(folder(root(this.model), "API"));
    }

    @Test
    void aFailingToolCallAnswersWith400() throws Exception {
        HttpResponse<String> response =
                post("/api/tools/" + RenameNodeTool.NAME, "{\"node_id\":987654,\"name\":\"x\"}");

        assertEquals(400, response.statusCode());
        assertTrue(json(response).get("error").asText().contains("no node with id"));
    }

    @Test
    void anUnknownToolAnswersWith404() throws Exception {
        HttpResponse<String> response = post("/api/tools/no_such_tool", "{}");

        assertEquals(404, response.statusCode());
    }

    @Test
    void aToolDeniedByPolicyIsReported() throws Exception {
        this.settings.setApiAllowWrite(false);

        HttpResponse<String> response = post("/api/tools/create_folder", "{\"name\":\"API\"}");

        assertEquals(400, response.statusCode());
        assertTrue(json(response).get("error").asText().contains("not permitted"));
    }

    @Test
    void theTreeAndStatusEndpointsRead() throws Exception {
        post("/api/tools/create_folder", "{\"name\":\"API\"}");

        JsonNode tree = json(get("/api/tree"));
        assertEquals(2, tree.get("total_nodes").asInt());
        assertEquals("API", tree.get("tree").get("children").get(0).get("name").asText());

        int id = tree.get("tree").get("children").get(0).get("id").asInt();
        assertEquals("API", json(get("/api/tree/nodes/" + id)).get("name").asText());

        assertTrue(json(get("/api/statuses")).get("statuses").size() > 0);
    }

    @Test
    void treeQueryParametersAreHonoured() throws Exception {
        post("/api/tools/create_folder", "{\"name\":\"API\"}");

        JsonNode limited = json(get("/api/tree?max_depth=1&include_requests=false"));

        assertEquals(1, limited.get("max_depth").asInt());
    }

    @Test
    void anUnparseableQueryParameterIsRejected() throws Exception {
        HttpResponse<String> response = get("/api/tree?max_depth=lots");

        assertEquals(400, response.statusCode());
    }

    @Test
    void anUnknownPathIs404AndAWrongMethodIs405() throws Exception {
        assertEquals(404, get("/api/nope").statusCode());
        assertEquals(405, post("/api/health", "{}").statusCode());
    }

    @Test
    void aTrailingSlashResolvesToTheSameRoute() throws Exception {
        assertEquals(200, get("/api/health/").statusCode());
    }

    @Test
    void preflightIsAnsweredWithoutABody() throws Exception {
        HttpResponse<String> response =
                send(HttpRequest.newBuilder(URI.create(this.base + "/api/health"))
                        .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                        .build());

        assertEquals(204, response.statusCode());
    }

    // --------------------------------------------------------------------- mcp

    @Test
    void theMcpEndpointServesToolsList() throws Exception {
        HttpResponse<String> response =
                post("/mcp", "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");

        assertEquals(200, response.statusCode());
        assertTrue(json(response).get("result").get("tools").size() > 0);
    }

    @Test
    void theMcpEndpointRunsAToolAndMutatesTheTree() throws Exception {
        String body =
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":"
                        + "{\"name\":\"create_folder\",\"arguments\":{\"name\":\"FromMcp\"}}}";

        HttpResponse<String> response = post("/mcp", body);

        assertEquals(200, response.statusCode());
        assertFalse(json(response).get("result").get("isError").asBoolean());
        assertNotNull(folder(root(this.model), "FromMcp"));
    }

    @Test
    void theMcpEndpointImportsASuppliedRequest() throws Exception {
        String arguments =
                "{\\\"base_url\\\":\\\"https://api.example.com\\\",\\\"request_utf8\\\":"
                        + "\\\"GET /orders HTTP/1.1\\\\nHost: api.example.com\\\\n\\\\n\\\"}";
        String body =
                "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\""
                        + ImportHttpRequestTool.NAME
                        + "\",\"arguments\":" + arguments.replace("\\\"", "\"") + "}}";

        HttpResponse<String> response = post("/mcp", body);

        assertEquals(200, response.statusCode());
        JsonNode result = json(response).get("result");
        assertFalse(result.get("isError").asBoolean(), result.toString());
        assertEquals(1, root(this.model).getChildCount());
    }

    @Test
    void aNotificationOverHttpIsAcceptedWithNoBody() throws Exception {
        HttpResponse<String> response =
                post("/mcp", "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");

        assertEquals(202, response.statusCode());
        assertTrue(response.body().isEmpty());
    }

    @Test
    void theStatelessRevisionIsSelectedByTheMcpMethodHeader() throws Exception {
        HttpResponse<String> response =
                send(authed("/mcp")
                        .header("Mcp-Method", "tools/list")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/list\"}"))
                        .build());

        assertEquals(200, response.statusCode());
        // The cache hints only appear on the stateless revision, so they prove the header was used.
        assertTrue(json(response).get("result").has("ttlMs"));
    }

    @Test
    void aGetOnTheMcpEndpointIsNotAllowed() throws Exception {
        assertEquals(405, get("/mcp").statusCode());
    }
}
