package treepeater.api.tools;

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
import treepeater.api.ApiPolicy;
import treepeater.api.TreepeaterService;
import treepeater.api.TreepeaterToolRegistry;
import treepeater.importing.ImportOptions;
import treepeater.importing.ImportOptions.DirectNameMode;
import treepeater.importing.ImportOptions.DirectPlacement;
import treepeater.settings.StatusRegistry;
import treepeater.tree.FolderTreeNode;
import treepeater.tree.RequestTreeNode;

/** Behaviour of the tree browsing and restructuring tools as reached through the registry. */
class TreeToolsTest extends ImportTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TreepeaterModel model;
    private TreepeaterToolRegistry registry;

    @BeforeEach
    void buildRegistry() {
        this.model = new TreepeaterModel();
        this.registry = TreepeaterToolRegistry.create(new TreepeaterService(this.model), null);
    }

    /** Runs a tool with full permissions and parses the result. */
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

    private int createFolder(int parentId, String name) throws Exception {
        return ok(CREATE, "{\"parent_id\":" + parentId + ",\"name\":\"" + name + "\"}").get("id").asInt();
    }

    private RequestTreeNode importLeaf(FolderTreeNode parent, String method, String path) {
        ImportOptions options =
                new ImportOptions(
                        StatusRegistry.getDefault().getId(),
                        new DirectPlacement(DirectNameMode.PATH, null));
        return this.model.importRequestManual(parent, rr(method, path), options);
    }

    private static final String CREATE = TreeTools.CREATE_FOLDER;

    // ------------------------------------------------------------------ folders

    @Test
    void createFolderAtTheRootReturnsTheNewNode() throws Exception {
        JsonNode created = ok(CREATE, "{\"name\":\"API\"}");

        assertEquals("folder", created.get("type").asText());
        assertEquals("API", created.get("name").asText());
        assertEquals("API", created.get("path").asText());
        assertEquals(root(this.model).getId(), created.get("parent_id").asInt());
        assertNotNull(folder(root(this.model), "API"));
    }

    @Test
    void createFolderNestsUnderAnExplicitParent() throws Exception {
        int parent = createFolder(0, "API");

        JsonNode child = ok(CREATE, "{\"parent_id\":" + parent + ",\"name\":\"v1\"}");

        assertEquals("API/v1", child.get("path").asText());
        assertEquals(parent, child.get("parent_id").asInt());
    }

    @Test
    void createFolderRejectsARequestNodeAsParent() throws Exception {
        RequestTreeNode leaf = importLeaf(root(this.model), "GET", "/users");

        String message = error(CREATE, "{\"parent_id\":" + leaf.getId() + "}");

        assertTrue(message.contains("not a folder"), message);
    }

    @Test
    void createFolderAcceptsCamelCaseArguments() throws Exception {
        int parent = createFolder(0, "API");

        JsonNode child = ok(CREATE, "{\"parentId\":" + parent + ",\"name\":\"v2\"}");

        assertEquals("API/v2", child.get("path").asText());
    }

    // -------------------------------------------------------------------- names

    @Test
    void renameNodeChangesTheDisplayName() throws Exception {
        int id = createFolder(0, "old");

        JsonNode renamed = ok(TreeTools.RENAME_NODE, "{\"node_id\":" + id + ",\"name\":\"new\"}");

        assertEquals("new", renamed.get("name").asText());
        assertNotNull(folder(root(this.model), "new"));
    }

    @Test
    void renameNodeRequiresANonBlankName() throws Exception {
        int id = createFolder(0, "old");

        assertTrue(error(TreeTools.RENAME_NODE, "{\"node_id\":" + id + "}").contains("name required"));
        assertTrue(error(TreeTools.RENAME_NODE, "{\"node_id\":" + id + ",\"name\":\"  \"}")
                .contains("name required"));
    }

    @Test
    void renameNodeRequiresANodeId() throws Exception {
        assertTrue(error(TreeTools.RENAME_NODE, "{\"name\":\"x\"}").contains("node_id required"));
    }

    // ----------------------------------------------------------------- statuses

    @Test
    void setNodeStatusAppliesAKnownStatus() throws Exception {
        int id = createFolder(0, "API");
        String statusId = StatusRegistry.getDefault().getId();

        JsonNode updated =
                ok(TreeTools.SET_NODE_STATUS, "{\"node_id\":" + id + ",\"status_id\":\"" + statusId + "\"}");

        assertEquals(statusId, updated.get("status_id").asText());
    }

    @Test
    void setNodeStatusRejectsAnUnknownStatusAndPointsAtListStatuses() throws Exception {
        int id = createFolder(0, "API");

        String message =
                error(TreeTools.SET_NODE_STATUS, "{\"node_id\":" + id + ",\"status_id\":\"nope\"}");

        assertTrue(message.contains("unknown status"), message);
        assertTrue(message.contains(StatusTools.LIST_STATUSES), message);
    }

    // -------------------------------------------------------------------- notes

    @Test
    void notesRoundTripOnARequestNode() throws Exception {
        RequestTreeNode leaf = importLeaf(root(this.model), "GET", "/users");

        ok(TreeTools.SET_NODE_NOTES, "{\"node_id\":" + leaf.getId() + ",\"notes\":\"check auth\"}");
        JsonNode fetched = ok(TreeTools.GET_NODE_NOTES, "{\"node_id\":" + leaf.getId() + "}");

        assertEquals("check auth", fetched.get("notes").asText());
        assertEquals("check auth", leaf.getNotes());
    }

    @Test
    void notesOnAFolderAreRejected() throws Exception {
        int id = createFolder(0, "API");

        assertTrue(error(TreeTools.GET_NODE_NOTES, "{\"node_id\":" + id + "}").contains("has no notes"));
        assertTrue(error(TreeTools.SET_NODE_NOTES, "{\"node_id\":" + id + ",\"notes\":\"x\"}")
                .contains("has no notes"));
    }

    // -------------------------------------------------------------------- moves

    @Test
    void moveNodeReparentsUnderTheDestination() throws Exception {
        int source = createFolder(0, "src");
        int destination = createFolder(0, "dst");
        RequestTreeNode leaf = importLeaf(folder(root(this.model), "src"), "GET", "/users");

        JsonNode moved =
                ok(TreeTools.MOVE_NODE, "{\"node_id\":" + leaf.getId() + ",\"parent_id\":" + destination + "}");

        assertEquals(destination, moved.get("parent_id").asInt());
        assertEquals(0, folder(root(this.model), "src").getChildCount());
        assertEquals(1, folder(root(this.model), "dst").getChildCount());
        assertTrue(source != destination);
    }

    @Test
    void moveNodeHonoursAnExplicitIndex() throws Exception {
        int destination = createFolder(0, "dst");
        createFolder(destination, "first");
        int mover = createFolder(0, "mover");

        ok(TreeTools.MOVE_NODE,
                "{\"node_id\":" + mover + ",\"parent_id\":" + destination + ",\"index\":0}");

        FolderTreeNode dst = folder(root(this.model), "dst");
        assertEquals("mover", ((treepeater.tree.TreepeaterNode) dst.getChildAt(0)).getName());
    }

    @Test
    void moveNodeIntoItsOwnSubtreeIsRejected() throws Exception {
        int outer = createFolder(0, "outer");
        int inner = createFolder(outer, "inner");

        String message =
                error(TreeTools.MOVE_NODE, "{\"node_id\":" + outer + ",\"parent_id\":" + inner + "}");

        assertTrue(message.contains("its own subtree"), message);
        assertNotNull(folder(root(this.model), "outer"));
    }

    @Test
    void moveNodeIntoItselfIsRejected() throws Exception {
        int id = createFolder(0, "solo");

        assertTrue(error(TreeTools.MOVE_NODE, "{\"node_id\":" + id + ",\"parent_id\":" + id + "}")
                .contains("itself"));
    }

    @Test
    void moveNodeRequiresBothIds() throws Exception {
        assertTrue(error(TreeTools.MOVE_NODE, "{\"node_id\":1}").contains("parent_id required"));
    }

    @Test
    void theRootCannotBeMovedOrDeleted() throws Exception {
        int rootId = root(this.model).getId();

        assertTrue(error(TreeTools.MOVE_NODE, "{\"node_id\":" + rootId + ",\"parent_id\":0}")
                .contains("root cannot be moved"));
        assertTrue(error(TreeTools.DELETE_NODE, "{\"node_id\":" + rootId + "}")
                .contains("root cannot be deleted"));
    }

    // ------------------------------------------------------------------ deletes

    @Test
    void deleteNodeRemovesTheWholeSubtreeAndReportsTheCount() throws Exception {
        int outer = createFolder(0, "outer");
        createFolder(outer, "inner");
        importLeaf(folder(root(this.model), "outer"), "GET", "/users");

        JsonNode deleted = ok(TreeTools.DELETE_NODE, "{\"node_id\":" + outer + "}");

        assertEquals(outer, deleted.get("deleted_id").asInt());
        assertEquals(3, deleted.get("removed_nodes").asInt());
        assertNull(folder(root(this.model), "outer"));
    }

    // ----------------------------------------------------------------- browsing

    @Test
    void listTreeReturnsTheNestedStructureWithATotal() throws Exception {
        int api = createFolder(0, "API");
        createFolder(api, "v1");

        JsonNode listed = ok(TreeTools.LIST_TREE, "{}");

        assertEquals(3, listed.get("total_nodes").asInt());
        assertEquals(TreepeaterService.MAX_TREE_DEPTH, listed.get("max_depth").asInt());
        JsonNode tree = listed.get("tree");
        assertEquals("folder", tree.get("type").asText());
        assertEquals(1, tree.get("children").size());
        assertEquals("API", tree.get("children").get(0).get("name").asText());
        assertEquals("v1", tree.get("children").get(0).get("children").get(0).get("name").asText());
    }

    @Test
    void listTreeScopesToASubtreeAndClampsTheDepth() throws Exception {
        int api = createFolder(0, "API");
        int v1 = createFolder(api, "v1");
        createFolder(v1, "users");

        JsonNode scoped = ok(TreeTools.LIST_TREE, "{\"node_id\":" + api + ",\"max_depth\":1}");

        assertEquals(1, scoped.get("max_depth").asInt());
        JsonNode tree = scoped.get("tree");
        assertEquals("API", tree.get("name").asText());
        // Depth 1 reaches v1 but must not expand its children.
        assertEquals("v1", tree.get("children").get(0).get("name").asText());
        assertFalse(tree.get("children").get(0).has("children"));

        JsonNode overLarge = ok(TreeTools.LIST_TREE, "{\"max_depth\":9999}");
        assertEquals(TreepeaterService.MAX_TREE_DEPTH, overLarge.get("max_depth").asInt());
    }

    @Test
    void listTreeCanOmitRequestLeaves() throws Exception {
        int api = createFolder(0, "API");
        importLeaf(folder(root(this.model), "API"), "GET", "/users");

        JsonNode withRequests = ok(TreeTools.LIST_TREE, "{\"node_id\":" + api + "}");
        assertEquals(1, withRequests.get("tree").get("children").size());

        JsonNode withoutRequests =
                ok(TreeTools.LIST_TREE, "{\"node_id\":" + api + ",\"include_requests\":false}");
        assertEquals(0, withoutRequests.get("tree").get("children").size());
        // The count is of the real subtree, so it still sees the leaf that was filtered from the output.
        assertEquals(2, withoutRequests.get("total_nodes").asInt());
    }

    @Test
    void getTreeNodeDescribesARequestInDetail() throws Exception {
        RequestTreeNode leaf = importLeaf(root(this.model), "POST", "/users/1");

        JsonNode node = ok(TreeTools.GET_TREE_NODE, "{\"node_id\":" + leaf.getId() + "}");

        assertEquals("request", node.get("type").asText());
        assertEquals("POST", node.get("method").asText());
        assertEquals("/users/1", node.get("url").asText());
        assertTrue(node.has("notes"));
        assertTrue(node.has("history_size"));
        assertTrue(node.get("has_response").asBoolean());
    }

    @Test
    void anUnknownNodeIdIsReported() throws Exception {
        assertTrue(error(TreeTools.GET_TREE_NODE, "{\"node_id\":987654}").contains("no node with id"));
    }

    @Test
    void malformedArgumentsAreReported() throws Exception {
        assertTrue(error(TreeTools.LIST_TREE, "not json").contains("must be a JSON object"));
    }

    // ------------------------------------------------------------------ policy

    @Test
    void readOnlyPolicyPermitsBrowsingButBlocksMutations() throws Exception {
        String listed = this.registry.execute(TreeTools.LIST_TREE, "{}", ApiPolicy.readOnly());
        assertFalse(MAPPER.readTree(listed).has("error"));

        String denied = this.registry.execute(CREATE, "{\"name\":\"API\"}", ApiPolicy.readOnly());
        assertTrue(MAPPER.readTree(denied).get("error").asText().contains("not permitted"));
        assertNull(folder(root(this.model), "API"));
    }

    @Test
    void anAbsentPolicyDefaultsToReadOnly() throws Exception {
        String denied = this.registry.execute(CREATE, "{\"name\":\"API\"}", null);

        assertTrue(MAPPER.readTree(denied).get("error").asText().contains("not permitted"));
    }

    @Test
    void anUnknownToolIsReported() throws Exception {
        String result = this.registry.execute("no_such_tool", "{}", ApiPolicy.full());

        assertTrue(MAPPER.readTree(result).get("error").asText().contains("unknown tool"));
    }

    @Test
    void humanToolUsage_renameNode_showsNewName() {
        HumanToolUsage usage =
                TreeTools.humanToolUsage(
                        TreeTools.RENAME_NODE, "{\"node_id\":5,\"name\":\"Renamed API\"}");
        assertTrue(usage.title().contains("id 5"));
        assertTrue(usage.detail().contains("Renamed API"));
    }

    @Test
    void humanToolUsage_deleteNode_warnsAboutSubtree() {
        HumanToolUsage usage = TreeTools.humanToolUsage(TreeTools.DELETE_NODE, "{\"node_id\":9}");
        assertTrue(usage.title().contains("id 9"));
        assertTrue(usage.detail().contains("cannot be undone"));
    }

    @Test
    void humanToolUsage_unknownTool_returnsNull() {
        assertNull(TreeTools.humanToolUsage("get_current_http_target", "{}"));
    }
}
