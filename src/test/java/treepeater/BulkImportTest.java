package treepeater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Answers.RETURNS_MOCKS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import javax.swing.UIManager;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.persistence.Preferences;

import treepeater.settings.StatusRegistry;
import treepeater.settings.TreepeaterSettings;
import treepeater.tree.FolderTreeNode;
import treepeater.tree.RequestTreeNode;
import treepeater.tree.TreepeaterNode;

/**
 * Unit tests for {@link TreepeaterModel#importRequestSorted(HttpRequestResponse)} covering the
 * bulk-path sorted import behavior: direct mode, endpoints that also have nested children,
 * method-folder mode, and the first-layer-skip leniency.
 */
class BulkImportTest {

    private TreepeaterSettings settings;

    @BeforeAll
    static void installLookAndFeel() throws Exception {
        // The tree cell editor embeds a FlatLaf combo box; without an installed LAF its UI defaults
        // (e.g. cell padding) are null and computing the node's preferred size on insert NPEs.
        // FlatLaf is only on the runtime classpath here, so install it by class name.
        UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
    }

    @BeforeEach
    void resetSettings() throws Exception {
        // Reset the settings singleton so we control it with a map-backed Preferences mock,
        // regardless of any other test class having initialized it first.
        Field instance = TreepeaterSettings.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);

        Map<String, String> store = new HashMap<>();
        Map<String, Integer> intStore = new HashMap<>();
        Preferences prefs = mock(Preferences.class);
        lenient().when(prefs.getString(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(i -> store.get(i.<String>getArgument(0)));
        lenient().doAnswer(i -> {
            store.put(i.getArgument(0), i.getArgument(1));
            return null;
        }).when(prefs).setString(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        lenient().doAnswer(i -> {
            store.remove(i.<String>getArgument(0));
            return null;
        }).when(prefs).deleteString(org.mockito.ArgumentMatchers.anyString());
        lenient().when(prefs.getInteger(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(i -> intStore.get(i.<String>getArgument(0)));
        lenient().doAnswer(i -> {
            intStore.put(i.getArgument(0), i.getArgument(1));
            return null;
        }).when(prefs).setInteger(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());

        TreepeaterSettings.init(prefs);
        this.settings = TreepeaterSettings.getInstance();

        // The JTree layout cache invokes the cell renderer on node insertion, which reads
        // Treepeater.getStatusRegistry() and resolves status colors via Treepeater.api's theme.
        // Provide both so headless inserts don't NPE.
        Field registry = Treepeater.class.getDeclaredField("statusRegistry");
        registry.setAccessible(true);
        registry.set(null, new StatusRegistry());

        Field api = Treepeater.class.getDeclaredField("api");
        api.setAccessible(true);
        api.set(null, mock(MontoyaApi.class, RETURNS_MOCKS));
    }

    // ===== helpers =====

    private static HttpRequestResponse rr(String method, String path) {
        HttpRequest req = mock(HttpRequest.class);
        lenient().when(req.pathWithoutQuery()).thenReturn(path);
        lenient().when(req.path()).thenReturn(path);
        lenient().when(req.method()).thenReturn(method);
        HttpResponse resp = mock(HttpResponse.class);
        HttpRequestResponse rr = mock(HttpRequestResponse.class);
        lenient().when(rr.request()).thenReturn(req);
        lenient().when(rr.response()).thenReturn(resp);
        return rr;
    }

    private static FolderTreeNode root(TreepeaterModel model) {
        return (FolderTreeNode) model.getTree().getTreeModel().getRoot();
    }

    private static FolderTreeNode folder(TreepeaterNode parent, String name) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            TreepeaterNode child = (TreepeaterNode) parent.getChildAt(i);
            if (child instanceof FolderTreeNode f && f.getName().equals(name)) {
                return f;
            }
        }
        return null;
    }

    private static RequestTreeNode leaf(TreepeaterNode parent, String name) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            TreepeaterNode child = (TreepeaterNode) parent.getChildAt(i);
            if (child instanceof RequestTreeNode r && r.getName().equals(name)) {
                return r;
            }
        }
        return null;
    }

    // ===== tests =====

    @Test
    void directModeSortsTheIssueExample() {
        this.settings.setImportLeafMode(TreepeaterSettings.IMPORT_LEAF_MODE_DIRECT);
        TreepeaterModel model = new TreepeaterModel();

        model.importRequestSorted(rr("GET", "/first/second/test"));
        model.importRequestSorted(rr("GET", "/first/third/test"));
        model.importRequestSorted(rr("GET", "/first/third"));
        model.importRequestSorted(rr("GET", "/second/second/test"));

        FolderTreeNode root = root(model);

        FolderTreeNode first = folder(root, "first");
        assertNotNull(first, "top-level 'first' folder");
        FolderTreeNode second = folder(root, "second");
        assertNotNull(second, "top-level 'second' folder");
        assertEquals(2, root.getChildCount(), "only two top-level folders");

        // /first/second/test
        FolderTreeNode firstSecond = folder(first, "second");
        assertNotNull(firstSecond);
        assertNotNull(leaf(firstSecond, "test"));

        // /first/third/test and /first/third: folder "third" and leaf "third" are siblings under "first"
        FolderTreeNode firstThird = folder(first, "third");
        assertNotNull(firstThird, "'third' folder under 'first'");
        assertNotNull(leaf(firstThird, "test"));
        assertNotNull(leaf(first, "third"), "'third' endpoint leaf under 'first'");

        // /second/second/test
        FolderTreeNode secondSecond = folder(second, "second");
        assertNotNull(secondSecond);
        assertNotNull(leaf(secondSecond, "test"));
    }

    @Test
    void endpointWithNestedChildrenCoexist() {
        this.settings.setImportLeafMode(TreepeaterSettings.IMPORT_LEAF_MODE_DIRECT);
        TreepeaterModel model = new TreepeaterModel();

        // The endpoint itself, then a deeper path sharing the same prefix.
        model.importRequestSorted(rr("GET", "/first/third"));
        model.importRequestSorted(rr("GET", "/first/third/test"));

        FolderTreeNode first = folder(root(model), "first");
        assertNotNull(first);

        RequestTreeNode endpointLeaf = leaf(first, "third");
        assertNotNull(endpointLeaf, "endpoint leaf 'third'");

        FolderTreeNode nestingFolder = folder(first, "third");
        assertNotNull(nestingFolder, "nesting folder 'third'");
        assertNotNull(leaf(nestingFolder, "test"), "'test' leaf under nesting folder");
    }

    @Test
    void methodFolderModePlacesLeafUnderMethodFolder() {
        this.settings.setImportLeafMode(TreepeaterSettings.IMPORT_LEAF_MODE_METHOD_FOLDER);
        this.settings.setImportBaseLeafName("base");
        TreepeaterModel model = new TreepeaterModel();

        model.importRequestSorted(rr("GET", "/first"));
        model.importRequestSorted(rr("POST", "/first/test"));

        FolderTreeNode first = folder(root(model), "first");
        assertNotNull(first);

        // /first (GET) -> first > [GET] > base
        FolderTreeNode getFolder = folder(first, "[GET]");
        assertNotNull(getFolder, "[GET] method folder under 'first'");
        assertNotNull(leaf(getFolder, "base"), "base leaf under [GET]");

        // /first/test (POST) -> first > test > [POST] > base
        FolderTreeNode test = folder(first, "test");
        assertNotNull(test, "'test' folder under 'first'");
        FolderTreeNode postFolder = folder(test, "[POST]");
        assertNotNull(postFolder, "[POST] method folder under 'test'");
        assertNotNull(leaf(postFolder, "base"), "base leaf under [POST]");
    }

    @Test
    void methodFolderModeUsesConfiguredBaseName() {
        this.settings.setImportLeafMode(TreepeaterSettings.IMPORT_LEAF_MODE_METHOD_FOLDER);
        this.settings.setImportBaseLeafName("index");
        TreepeaterModel model = new TreepeaterModel();

        model.importRequestSorted(rr("GET", "/api"));

        FolderTreeNode api = folder(root(model), "api");
        assertNotNull(api);
        FolderTreeNode getFolder = folder(api, "[GET]");
        assertNotNull(getFolder);
        assertNotNull(leaf(getFolder, "index"), "leaf uses configured base name");
    }

    @Test
    void skipsFirstLayerGroupingFolder() {
        this.settings.setImportLeafMode(TreepeaterSettings.IMPORT_LEAF_MODE_DIRECT);
        TreepeaterModel model = new TreepeaterModel();

        // Pre-existing per-service grouping folder that is not part of the URL path.
        FolderTreeNode serviceA = model.findOrCreateChildFolder(root(model), "ServiceA");
        FolderTreeNode users = model.findOrCreateChildFolder(serviceA, "users");
        FolderTreeNode posts = model.findOrCreateChildFolder(users, "posts");

        model.importRequestSorted(rr("GET", "/users/posts/1"));

        FolderTreeNode root = root(model);
        // The leaf should be sorted into the existing ServiceA/users, not a new top-level "users".
        assertNull(folder(root, "users"), "no new top-level 'users' folder created");
        assertNotNull(leaf(posts, "1"), "endpoint leaf '1' under ServiceA/users");
    }

    @Test
    void picksDeepestMatchNotTheFirstMatchingBranch() {
        this.settings.setImportLeafMode(TreepeaterSettings.IMPORT_LEAF_MODE_DIRECT);
        TreepeaterModel model = new TreepeaterModel();

        FolderTreeNode root = root(model);
        // A shallow single folder literally named "a/b" (spans two path segments, no children)...
        FolderTreeNode ab = model.findOrCreateChildFolder(root, "a/b");
        // ...and a deeper separate chain a > b > c that matches more of the target path.
        FolderTreeNode a = model.findOrCreateChildFolder(root, "a");
        FolderTreeNode b = model.findOrCreateChildFolder(a, "b");
        FolderTreeNode c = model.findOrCreateChildFolder(b, "c");

        // A greedy descent would prefer the 2-segment "a/b" at the root and stop there (consuming 2).
        // The enumerate-and-select algorithm should instead pick a/b/c (consuming 3).
        model.importRequestSorted(rr("GET", "/a/b/c/x"));

        assertNotNull(leaf(c, "x"), "leaf attached to the deepest matching folder a/b/c");
        assertEquals(0, ab.getChildCount(), "shallow 'a/b' folder left untouched");
    }

    @Test
    void lenientFolderGroupingCanBeDisabled() {
        this.settings.setImportLeafMode(TreepeaterSettings.IMPORT_LEAF_MODE_DIRECT);
        this.settings.setImportGroupingFolderReconciliationEnabled(false);
        TreepeaterModel model = new TreepeaterModel();

        FolderTreeNode serviceA = model.findOrCreateChildFolder(root(model), "ServiceA");
        FolderTreeNode users = model.findOrCreateChildFolder(serviceA, "users");

        model.importRequestSorted(rr("GET", "/users/1"));

        assertNotNull(folder(root(model), "users"), "creates a new top-level folder when lenient folder grouping is disabled");
        assertEquals(0, users.getChildCount(), "existing grouped folder is not used");
    }

    @Test
    void dynamicSegmentNormalizationCollapsesNumericIds() {
        this.settings.setImportLeafMode(TreepeaterSettings.IMPORT_LEAF_MODE_DIRECT);
        this.settings.setImportNormalizeDynamicSegmentsEnabled(true);
        TreepeaterModel model = new TreepeaterModel();

        model.importRequestSorted(rr("GET", "/users/2/status"));
        model.importRequestSorted(rr("GET", "/users/7/status"));

        FolderTreeNode users = folder(root(model), "users");
        assertNotNull(users, "top-level users folder");

        FolderTreeNode idFolder = folder(users, ":id");
        assertNotNull(idFolder, "single :id folder under users");
        assertNotNull(leaf(idFolder, "status"), "first status leaf");
        assertEquals(2, idFolder.getChildCount(), "two status leaves under :id");
        assertNull(folder(users, "2"), "no literal 2 folder");
        assertNull(folder(users, "7"), "no literal 7 folder");
    }

    @Test
    void dynamicSegmentNormalizationDisabledByDefault() {
        this.settings.setImportLeafMode(TreepeaterSettings.IMPORT_LEAF_MODE_DIRECT);
        TreepeaterModel model = new TreepeaterModel();

        model.importRequestSorted(rr("GET", "/users/2/status"));

        FolderTreeNode users = folder(root(model), "users");
        assertNotNull(users);
        assertNotNull(folder(users, "2"), "literal numeric folder when normalization is off");
        assertNull(folder(users, ":id"), "no :id folder when normalization is off");
    }

    @Test
    void dynamicSegmentNormalizationInMethodFolderMode() {
        this.settings.setImportLeafMode(TreepeaterSettings.IMPORT_LEAF_MODE_METHOD_FOLDER);
        this.settings.setImportBaseLeafName("base");
        this.settings.setImportNormalizeDynamicSegmentsEnabled(true);
        TreepeaterModel model = new TreepeaterModel();

        model.importRequestSorted(rr("POST", "/users/2"));

        FolderTreeNode users = folder(root(model), "users");
        assertNotNull(users);
        FolderTreeNode idFolder = folder(users, ":id");
        assertNotNull(idFolder, ":id folder under users");
        FolderTreeNode postFolder = folder(idFolder, "[POST]");
        assertNotNull(postFolder, "[POST] method folder under :id");
        assertNotNull(leaf(postFolder, "base"), "base leaf under [POST]");
    }
}
