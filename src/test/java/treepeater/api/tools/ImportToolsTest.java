package treepeater.api.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import treepeater.ImportTestSupport;
import treepeater.MontoyaFactoryStub;
import treepeater.TreepeaterModel;
import treepeater.api.ApiPolicy;
import treepeater.api.TreepeaterService;
import treepeater.api.TreepeaterToolRegistry;
import treepeater.settings.StatusRegistry;
import treepeater.tree.FolderTreeNode;

/** Importing caller-supplied requests through the direct, path-aware and explicit-folder tools. */
class ImportToolsTest extends ImportTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String GET_USERS = "GET /api/v1/users/42 HTTP/1.1\nHost: api.example.com\n\n";

    private TreepeaterModel model;
    private TreepeaterToolRegistry registry;

    @BeforeEach
    void buildRegistry() {
        MontoyaFactoryStub.install();
        this.model = new TreepeaterModel();
        this.registry = TreepeaterToolRegistry.create(new TreepeaterService(this.model), null);
    }

    @AfterEach
    void removeFactory() {
        MontoyaFactoryStub.uninstall();
    }

    private JsonNode call(String tool, String argumentsJson) throws Exception {
        String result = this.registry.execute(tool, argumentsJson, ApiPolicy.full());
        assertNotNull(result);
        return MAPPER.readTree(result);
    }

    private JsonNode ok(String tool, String argumentsJson) throws Exception {
        JsonNode node = call(tool, argumentsJson);
        assertFalse(node.has("error"), () -> tool + " failed: " + node);
        return node;
    }

    private String error(String tool, String argumentsJson) throws Exception {
        JsonNode node = call(tool, argumentsJson);
        assertTrue(node.has("error"), () -> tool + " unexpectedly succeeded: " + node);
        return node.get("error").asText();
    }

    /** Request arguments for the standard sample request. */
    private static String args(String extra) {
        String base =
                "\"base_url\":\"https://api.example.com\",\"request_utf8\":"
                        + quote(GET_USERS);
        return "{" + base + (extra.isEmpty() ? "" : "," + extra) + "}";
    }

    private static String quote(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    // ------------------------------------------------------------------- direct

    @Test
    void directImportPlacesASingleLeafAtTheRoot() throws Exception {
        JsonNode created = ok(ImportTools.IMPORT_HTTP_REQUEST, args("\"name_mode\":\"PATH\""));

        assertEquals("request", created.get("type").asText());
        assertEquals("GET", created.get("method").asText());
        assertEquals("https://api.example.com/api/v1/users/42", created.get("url").asText());
        assertEquals(root(this.model).getId(), created.get("parent_id").asInt());
        assertEquals(1, root(this.model).getChildCount());
        assertNotNull(leaf(root(this.model), created.get("name").asText()));
    }

    @Test
    void directImportHonoursAManualName() throws Exception {
        JsonNode created =
                ok(ImportTools.IMPORT_HTTP_REQUEST,
                        args("\"name_mode\":\"MANUAL\",\"manual_name\":\"Fetch user\""));

        assertEquals("Fetch user", created.get("name").asText());
    }

    @Test
    void manualNameModeRequiresAName() throws Exception {
        String message = error(ImportTools.IMPORT_HTTP_REQUEST, args("\"name_mode\":\"MANUAL\""));

        assertTrue(message.contains("manual_name is required"), message);
    }

    @Test
    void anUnknownNameModeIsRejected() throws Exception {
        String message = error(ImportTools.IMPORT_HTTP_REQUEST, args("\"name_mode\":\"SIDEWAYS\""));

        assertTrue(message.contains("name_mode must be one of"), message);
    }

    @Test
    void directImportCanTargetAnExistingFolder() throws Exception {
        FolderTreeNode destination = this.model.createFolder(root(this.model));
        destination.setName("Saved");

        JsonNode created =
                ok(ImportTools.IMPORT_HTTP_REQUEST, args("\"folder_id\":" + destination.getId()));

        assertEquals(destination.getId(), created.get("parent_id").asInt());
        assertEquals(1, destination.getChildCount());
    }

    // --------------------------------------------------------------- path aware

    @Test
    void pathAwareImportBuildsFoldersFromTheUrlPath() throws Exception {
        JsonNode created =
                ok(ImportTools.IMPORT_HTTP_REQUEST_PATH_AWARE,
                        args("\"leaf_mode\":\"DIRECT\",\"normalize_dynamic_segments_enabled\":false"));

        assertTrue(created.has("folder_path"));
        FolderTreeNode api = folder(root(this.model), "api");
        assertNotNull(api, "expected an 'api' folder");
        FolderTreeNode v1 = folder(api, "v1");
        assertNotNull(v1, "expected a 'v1' folder");
        FolderTreeNode users = folder(v1, "users");
        assertNotNull(users, "expected a 'users' folder");
        assertNotNull(leaf(users, "42"));
    }

    @Test
    void pathAwareImportCanNormalizeDynamicSegments() throws Exception {
        ok(ImportTools.IMPORT_HTTP_REQUEST_PATH_AWARE,
                args("\"leaf_mode\":\"DIRECT\",\"normalize_dynamic_segments_enabled\":true"));

        FolderTreeNode users =
                folder(folder(folder(root(this.model), "api"), "v1"), "users");
        assertNotNull(users, "expected a 'users' folder");
        // The numeric id becomes a placeholder so sibling requests group together.
        assertNotNull(leaf(users, ":id"), "expected the id segment to be normalized");
    }

    @Test
    void pathAwareImportCanNestTheLeafUnderAMethodFolder() throws Exception {
        ok(ImportTools.IMPORT_HTTP_REQUEST_PATH_AWARE,
                args("\"leaf_mode\":\"METHOD_FOLDER\",\"base_leaf_name\":\"base\","
                        + "\"normalize_dynamic_segments_enabled\":false"));

        FolderTreeNode users =
                folder(folder(folder(root(this.model), "api"), "v1"), "users");
        FolderTreeNode id = folder(users, "42");
        assertNotNull(id, "expected a folder for the id segment");
        FolderTreeNode method = folder(id, "[GET]");
        assertNotNull(method, "expected a [GET] method folder");
        assertNotNull(leaf(method, "base"));
    }

    @Test
    void pathAwareImportReusesFoldersAcrossCalls() throws Exception {
        String options = "\"leaf_mode\":\"DIRECT\",\"normalize_dynamic_segments_enabled\":false";
        ok(ImportTools.IMPORT_HTTP_REQUEST_PATH_AWARE, args(options));

        String second =
                "{\"base_url\":\"https://api.example.com\",\"request_utf8\":"
                        + quote("GET /api/v1/orders HTTP/1.1\nHost: api.example.com\n\n")
                        + "," + options + "}";
        ok(ImportTools.IMPORT_HTTP_REQUEST_PATH_AWARE, second);

        FolderTreeNode v1 = folder(folder(root(this.model), "api"), "v1");
        assertNotNull(v1);
        assertEquals(1, countFolders(root(this.model), "api"), "the api folder must be reused");
        assertNotNull(folder(v1, "users"));
        assertNotNull(leaf(v1, "orders"));
    }

    @Test
    void anUnknownLeafModeIsRejected() throws Exception {
        String message =
                error(ImportTools.IMPORT_HTTP_REQUEST_PATH_AWARE, args("\"leaf_mode\":\"CHAOS\""));

        assertTrue(message.contains("leaf_mode must be DIRECT or METHOD_FOLDER"), message);
    }

    // ------------------------------------------------------------ into a folder

    @Test
    void importIntoFolderRequiresAFolderId() throws Exception {
        assertTrue(error(ImportTools.IMPORT_HTTP_REQUEST_INTO_FOLDER, args(""))
                .contains("folder_id required"));
    }

    @Test
    void importIntoFolderDefaultsToDirectPlacement() throws Exception {
        FolderTreeNode destination = this.model.createFolder(root(this.model));
        destination.setName("Saved");

        JsonNode created =
                ok(ImportTools.IMPORT_HTTP_REQUEST_INTO_FOLDER,
                        args("\"folder_id\":" + destination.getId()));

        assertEquals(destination.getId(), created.get("parent_id").asInt());
        assertEquals(1, destination.getChildCount());
        assertNull(folder(destination, "api"), "direct placement must not build path folders");
    }

    @Test
    void importIntoFolderCanUsePathAwarePlacement() throws Exception {
        FolderTreeNode destination = this.model.createFolder(root(this.model));
        destination.setName("Saved");

        ok(ImportTools.IMPORT_HTTP_REQUEST_INTO_FOLDER,
                args("\"folder_id\":" + destination.getId()
                        + ",\"placement\":\"path_aware\",\"leaf_mode\":\"DIRECT\","
                        + "\"normalize_dynamic_segments_enabled\":false"));

        assertNotNull(folder(destination, "api"), "path-aware placement must build folders");
    }

    @Test
    void importIntoAFolderIdThatIsARequestIsRejected() throws Exception {
        JsonNode leaf = ok(ImportTools.IMPORT_HTTP_REQUEST, args(""));

        String message =
                error(ImportTools.IMPORT_HTTP_REQUEST_INTO_FOLDER,
                        args("\"folder_id\":" + leaf.get("id").asInt()));

        assertTrue(message.contains("not a folder"), message);
    }

    // -------------------------------------------------------- request arguments

    @Test
    void theTargetCanBeGivenAsHostPortAndSecure() throws Exception {
        String body =
                "{\"host\":\"api.example.com\",\"port\":8443,\"secure\":true,\"request_utf8\":"
                        + quote(GET_USERS) + "}";

        JsonNode created = ok(ImportTools.IMPORT_HTTP_REQUEST, body);

        assertEquals("https://api.example.com:8443/api/v1/users/42", created.get("url").asText());
    }

    @Test
    void theRequestCanBeGivenAsBase64() throws Exception {
        String encoded = Base64.getEncoder().encodeToString(GET_USERS.replace("\n", "\r\n").getBytes());
        String body =
                "{\"base_url\":\"https://api.example.com\",\"request_base64\":\"" + encoded + "\"}";

        JsonNode created = ok(ImportTools.IMPORT_HTTP_REQUEST, body);

        assertEquals("GET", created.get("method").asText());
        assertEquals("https://api.example.com/api/v1/users/42", created.get("url").asText());
    }

    @Test
    void aResponseCanBeStoredAlongsideTheRequest() throws Exception {
        JsonNode created =
                ok(ImportTools.IMPORT_HTTP_REQUEST,
                        args("\"response_utf8\":" + quote("HTTP/1.1 200 OK\nContent-Length: 0\n\n")));

        assertTrue(created.get("has_response").asBoolean());
    }

    @Test
    void aRequestWithoutAResponseIsMarkedAsSuch() throws Exception {
        JsonNode created = ok(ImportTools.IMPORT_HTTP_REQUEST, args(""));

        assertFalse(created.get("has_response").asBoolean());
    }

    @Test
    void supplyingBothTextAndBase64IsRejected() throws Exception {
        String body =
                "{\"base_url\":\"https://api.example.com\",\"request_utf8\":" + quote(GET_USERS)
                        + ",\"request_base64\":\"" + Base64.getEncoder().encodeToString("x".getBytes())
                        + "\"}";

        assertTrue(error(ImportTools.IMPORT_HTTP_REQUEST, body).contains("not both"));
    }

    @Test
    void invalidBase64IsRejected() throws Exception {
        String body =
                "{\"base_url\":\"https://api.example.com\",\"request_base64\":\"not base64 !!\"}";

        assertTrue(error(ImportTools.IMPORT_HTTP_REQUEST, body).contains("invalid request_base64"));
    }

    @Test
    void aMissingRequestBodyIsRejected() throws Exception {
        String message =
                error(ImportTools.IMPORT_HTTP_REQUEST, "{\"base_url\":\"https://api.example.com\"}");

        assertTrue(message.contains("request_utf8"), message);
    }

    @Test
    void aMissingTargetIsRejected() throws Exception {
        String message =
                error(ImportTools.IMPORT_HTTP_REQUEST, "{\"request_utf8\":" + quote(GET_USERS) + "}");

        assertTrue(message.contains("provide base_url"), message);
    }

    @Test
    void aNonHttpBaseUrlIsRejected() throws Exception {
        String body =
                "{\"base_url\":\"ftp://api.example.com\",\"request_utf8\":" + quote(GET_USERS) + "}";

        assertTrue(error(ImportTools.IMPORT_HTTP_REQUEST, body).contains("http:// or https://"));
    }

    @Test
    void anOutOfRangePortIsRejected() throws Exception {
        String body =
                "{\"host\":\"api.example.com\",\"port\":70000,\"request_utf8\":" + quote(GET_USERS) + "}";

        assertTrue(error(ImportTools.IMPORT_HTTP_REQUEST, body).contains("port must be between"));
    }

    // -------------------------------------------------------------- status ids

    @Test
    void animportedRequestTakesTheDefaultStatusWhenNoneIsGiven() throws Exception {
        JsonNode created = ok(ImportTools.IMPORT_HTTP_REQUEST, args(""));

        assertEquals(StatusRegistry.getDefault().getId(), created.get("status_id").asText());
    }

    @Test
    void anExplicitStatusIdIsApplied() throws Exception {
        String statusId = StatusRegistry.getDefault().getId();

        JsonNode created =
                ok(ImportTools.IMPORT_HTTP_REQUEST, args("\"status_id\":\"" + statusId + "\""));

        assertEquals(statusId, created.get("status_id").asText());
    }

    // ------------------------------------------------------------------ policy

    @Test
    void importingIsBlockedWithoutWritePermission() throws Exception {
        String denied =
                this.registry.execute(ImportTools.IMPORT_HTTP_REQUEST, args(""), ApiPolicy.readOnly());

        assertTrue(MAPPER.readTree(denied).get("error").asText().contains("not permitted"));
        assertEquals(0, root(this.model).getChildCount());
    }

    @Test
    void humanToolUsage_directImport_showsTargetUrl() {
        HumanToolUsage usage =
                ImportTools.humanToolUsage(
                        ImportTools.IMPORT_HTTP_REQUEST,
                        "{\"base_url\":\"https://api.example.com/users\"}");
        assertEquals("Import HTTP request (direct)", usage.title());
        assertTrue(usage.detail().contains("api.example.com"));
    }

    @Test
    void humanToolUsage_intoFolder_showsFolderAndPlacement() {
        HumanToolUsage usage =
                ImportTools.humanToolUsage(
                        ImportTools.IMPORT_HTTP_REQUEST_INTO_FOLDER,
                        "{\"folder_id\":3,\"placement\":\"path_aware\",\"host\":\"example.com\"}");
        assertEquals("Import HTTP request into folder", usage.title());
        assertTrue(usage.detail().contains("folder id 3"));
        assertTrue(usage.detail().contains("path_aware"));
    }

    private static int countFolders(treepeater.tree.TreepeaterNode parent, String name) {
        int total = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChildAt(i) instanceof FolderTreeNode child && name.equals(child.getName())) {
                total++;
            }
        }
        return total;
    }
}
