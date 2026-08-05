package treepeater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import treepeater.settings.TreepeaterSettings;
import treepeater.tree.FolderTreeNode;
import treepeater.tree.RequestTreeNode;

/**
 * Unit tests for {@link TreepeaterModel#importRequestPathAware(burp.api.montoya.http.message.HttpRequestResponse)}
 * covering path-aware import behavior: direct mode, endpoints that also have nested children,
 * method-folder mode, and lenient folder grouping.
 */
class BulkImportTest extends ImportTestSupport {

    @Test
    void directModeSortsTheIssueExample() {
        this.settings.setImportLeafMode(TreepeaterSettings.IMPORT_LEAF_MODE_DIRECT);
        TreepeaterModel model = new TreepeaterModel();

        model.importRequestPathAware(rr("GET", "/first/second/test"));
        model.importRequestPathAware(rr("GET", "/first/third/test"));
        model.importRequestPathAware(rr("GET", "/first/third"));
        model.importRequestPathAware(rr("GET", "/second/second/test"));

        FolderTreeNode root = root(model);

        FolderTreeNode first = folder(root, "first");
        assertNotNull(first, "top-level 'first' folder");
        FolderTreeNode second = folder(root, "second");
        assertNotNull(second, "top-level 'second' folder");
        assertEquals(2, root.getChildCount(), "only two top-level folders");

        FolderTreeNode firstSecond = folder(first, "second");
        assertNotNull(firstSecond);
        assertNotNull(leaf(firstSecond, "test"));

        FolderTreeNode firstThird = folder(first, "third");
        assertNotNull(firstThird, "'third' folder under 'first'");
        assertNotNull(leaf(firstThird, "test"));
        assertNotNull(leaf(first, "third"), "'third' endpoint leaf under 'first'");

        FolderTreeNode secondSecond = folder(second, "second");
        assertNotNull(secondSecond);
        assertNotNull(leaf(secondSecond, "test"));
    }

    @Test
    void endpointWithNestedChildrenCoexist() {
        this.settings.setImportLeafMode(TreepeaterSettings.IMPORT_LEAF_MODE_DIRECT);
        TreepeaterModel model = new TreepeaterModel();

        model.importRequestPathAware(rr("GET", "/first/third"));
        model.importRequestPathAware(rr("GET", "/first/third/test"));

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

        model.importRequestPathAware(rr("GET", "/first"));
        model.importRequestPathAware(rr("POST", "/first/test"));

        FolderTreeNode first = folder(root(model), "first");
        assertNotNull(first);

        FolderTreeNode getFolder = folder(first, "[GET]");
        assertNotNull(getFolder, "[GET] method folder under 'first'");
        assertNotNull(leaf(getFolder, "base"), "base leaf under [GET]");

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

        model.importRequestPathAware(rr("GET", "/api"));

        FolderTreeNode api = folder(root(model), "api");
        assertNotNull(api);
        FolderTreeNode getFolder = folder(api, "[GET]");
        assertNotNull(getFolder);
        assertNotNull(leaf(getFolder, "index"), "leaf uses configured base name");
    }

    @Test
    void skipsFirstLayerGroupingFolder() {
        this.settings.setImportLeafMode(TreepeaterSettings.IMPORT_LEAF_MODE_DIRECT);
        this.settings.setImportNormalizeDynamicSegmentsEnabled(false);
        TreepeaterModel model = new TreepeaterModel();

        FolderTreeNode serviceA = model.findOrCreateChildFolder(root(model), "ServiceA");
        FolderTreeNode users = model.findOrCreateChildFolder(serviceA, "users");
        FolderTreeNode posts = model.findOrCreateChildFolder(users, "posts");

        model.importRequestPathAware(rr("GET", "/users/posts/1"));

        FolderTreeNode root = root(model);
        assertNull(folder(root, "users"), "no new top-level 'users' folder created");
        assertNotNull(leaf(posts, "1"), "endpoint leaf '1' under ServiceA/users");
    }

    @Test
    void picksDeepestMatchNotTheFirstMatchingBranch() {
        this.settings.setImportLeafMode(TreepeaterSettings.IMPORT_LEAF_MODE_DIRECT);
        TreepeaterModel model = new TreepeaterModel();

        FolderTreeNode root = root(model);
        FolderTreeNode ab = model.findOrCreateChildFolder(root, "a/b");
        FolderTreeNode a = model.findOrCreateChildFolder(root, "a");
        FolderTreeNode b = model.findOrCreateChildFolder(a, "b");
        FolderTreeNode c = model.findOrCreateChildFolder(b, "c");

        model.importRequestPathAware(rr("GET", "/a/b/c/x"));

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

        model.importRequestPathAware(rr("GET", "/users/1"));

        assertNotNull(folder(root(model), "users"), "creates a new top-level folder when lenient folder grouping is disabled");
        assertEquals(0, users.getChildCount(), "existing grouped folder is not used");
    }

    @Test
    void dynamicSegmentNormalizationCollapsesNumericIds() {
        this.settings.setImportLeafMode(TreepeaterSettings.IMPORT_LEAF_MODE_DIRECT);
        this.settings.setImportNormalizeDynamicSegmentsEnabled(true);
        TreepeaterModel model = new TreepeaterModel();

        model.importRequestPathAware(rr("GET", "/users/2/status"));
        model.importRequestPathAware(rr("GET", "/users/7/status"));

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
    void dynamicSegmentNormalizationDisabledWhenSettingOff() {
        this.settings.setImportLeafMode(TreepeaterSettings.IMPORT_LEAF_MODE_DIRECT);
        this.settings.setImportNormalizeDynamicSegmentsEnabled(false);
        TreepeaterModel model = new TreepeaterModel();

        model.importRequestPathAware(rr("GET", "/users/2/status"));

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

        model.importRequestPathAware(rr("POST", "/users/2"));

        FolderTreeNode users = folder(root(model), "users");
        assertNotNull(users);
        FolderTreeNode idFolder = folder(users, ":id");
        assertNotNull(idFolder, ":id folder under users");
        FolderTreeNode postFolder = folder(idFolder, "[POST]");
        assertNotNull(postFolder, "[POST] method folder under :id");
        assertNotNull(leaf(postFolder, "base"), "base leaf under [POST]");
    }
}
