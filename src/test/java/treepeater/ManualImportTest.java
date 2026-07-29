package treepeater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;


import org.junit.jupiter.api.Test;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;

import treepeater.importing.ImportOptions;
import treepeater.importing.ImportOptions.DirectNameMode;
import treepeater.importing.ImportOptions.DirectPlacement;
import treepeater.importing.ImportOptions.PathAwarePlacement;
import treepeater.settings.TreepeaterSettings;
import treepeater.tree.FolderTreeNode;
import treepeater.tree.RequestTreeNode;

class ManualImportTest extends ImportTestSupport {

    private static ImportOptions directOptions(DirectNameMode nameMode, String manualName, String statusId) {
        return new ImportOptions(statusId, new DirectPlacement(nameMode, manualName));
    }

    private static ImportOptions directOptions(DirectNameMode nameMode, String manualName) {
        return directOptions(nameMode, manualName, ImportOptions.fromSettings().statusId());
    }

    private static ImportOptions pathAwareOptions(
            String leafMode,
            String baseLeafName,
            boolean lenientEnabled,
            int lenientMaxSkip,
            int lenientThresholdPercent,
            boolean normalizeDynamic) {
        return new ImportOptions(
                ImportOptions.fromSettings().statusId(),
                new PathAwarePlacement(
                        leafMode,
                        baseLeafName,
                        lenientEnabled,
                        lenientMaxSkip,
                        lenientThresholdPercent,
                        normalizeDynamic));
    }

    private static ImportOptions pathAwareOptions(
            String leafMode,
            String baseLeafName,
            boolean lenientEnabled,
            boolean normalizeDynamic) {
        return pathAwareOptions(leafMode, baseLeafName, lenientEnabled, 2, 60, normalizeDynamic);
    }

    @Test
    void directImportAppliesSelectedStatus() {
        TreepeaterModel model = new TreepeaterModel();
        FolderTreeNode anchor = model.createFolder(root(model));

        model.importRequestManual(
                anchor,
                rr("GET", "/api/users"),
                directOptions(DirectNameMode.ID, "", "FINDING"));

        RequestTreeNode imported = leaf(anchor, "2");
        assertNotNull(imported);
        assertEquals("FINDING", imported.getStatus().getId());
    }

    @Test
    void directIdModeNamesWithCounterUnderChosenFolder() {
        TreepeaterModel model = new TreepeaterModel();
        FolderTreeNode anchor = model.createFolder(root(model));
        anchor.setName("Target");

        model.importRequestManual(anchor, rr("GET", "/ignored"), directOptions(DirectNameMode.ID, ""));

        assertEquals(1, anchor.getChildCount());
        RequestTreeNode imported = leaf(anchor, "2");
        assertNotNull(imported);
    }

    @Test
    void directUrlModeUsesRequestUrl() {
        TreepeaterModel model = new TreepeaterModel();
        FolderTreeNode anchor = model.createFolder(root(model));

        model.importRequestManual(
                anchor,
                rr("GET", "/api/users", "https://example.com/api/users"),
                directOptions(DirectNameMode.URL, ""));

        assertNotNull(leaf(anchor, "https://example.com/api/users"));
    }

    @Test
    void directUrlModeFallsBackToPathWhenUrlMissing() {
        TreepeaterModel model = new TreepeaterModel();
        FolderTreeNode anchor = model.createFolder(root(model));

        model.importRequestManual(
                anchor,
                rr("GET", "/api/users"),
                directOptions(DirectNameMode.URL, ""));

        assertNotNull(leaf(anchor, "/api/users"));
    }

    @Test
    void directUrlModeFallsBackToQuestionMarkWhenUrlAndPathUnavailable() {
        TreepeaterModel model = new TreepeaterModel();
        FolderTreeNode anchor = model.createFolder(root(model));

        HttpRequest request = mock(HttpRequest.class);
        lenient().when(request.url()).thenThrow(new RuntimeException("no url"));
        lenient().when(request.pathWithoutQuery()).thenThrow(new RuntimeException("no path"));
        lenient().when(request.path()).thenThrow(new RuntimeException("no path"));
        HttpRequestResponse requestResponse = mock(HttpRequestResponse.class);
        lenient().when(requestResponse.request()).thenReturn(request);
        lenient().when(requestResponse.response()).thenReturn(null);

        model.importRequestManual(anchor, requestResponse, directOptions(DirectNameMode.URL, ""));

        assertNotNull(leaf(anchor, "?"));
    }

    @Test
    void directManualModeUsesProvidedName() {
        TreepeaterModel model = new TreepeaterModel();
        FolderTreeNode anchor = model.createFolder(root(model));

        model.importRequestManual(
                anchor,
                rr("GET", "/api/users"),
                directOptions(DirectNameMode.MANUAL, "My Request"));

        assertNotNull(leaf(anchor, "My Request"));
    }

    @Test
    void directManualModeFallsBackToQuestionMarkForBlankName() {
        TreepeaterModel model = new TreepeaterModel();
        FolderTreeNode anchor = model.createFolder(root(model));

        model.importRequestManual(
                anchor,
                rr("GET", "/api/users"),
                directOptions(DirectNameMode.MANUAL, "   "));

        assertNotNull(leaf(anchor, "?"));
    }

    @Test
    void pathAwareImportBuildsChainUnderAnchorFolder() {
        TreepeaterModel model = new TreepeaterModel();
        FolderTreeNode anchor = model.createFolder(root(model));
        anchor.setName("API");

        ImportOptions options = pathAwareOptions(
                TreepeaterSettings.IMPORT_LEAF_MODE_DIRECT,
                "base",
                false,
                false);
        model.importRequestManual(anchor, rr("GET", "/users/1"), options);

        FolderTreeNode users = folder(anchor, "users");
        assertNotNull(users, "users folder under anchor");
        assertNotNull(leaf(users, "1"), "leaf under users");
        assertNull(folder(root(model), "users"), "users folder should not be created at tree root");
    }

    @Test
    void pathAwareMethodFolderModeHonorsDialogOverrides() {
        TreepeaterModel model = new TreepeaterModel();
        FolderTreeNode anchor = model.createFolder(root(model));

        ImportOptions options = pathAwareOptions(
                TreepeaterSettings.IMPORT_LEAF_MODE_METHOD_FOLDER,
                "endpoint",
                false,
                false);
        model.importRequestManual(anchor, rr("POST", "/first/test"), options);

        FolderTreeNode first = folder(anchor, "first");
        assertNotNull(first);
        FolderTreeNode test = folder(first, "test");
        assertNotNull(test);
        FolderTreeNode methodFolder = folder(test, "[POST]");
        assertNotNull(methodFolder);
        assertNotNull(leaf(methodFolder, "endpoint"));
    }

    @Test
    void pathAwareImportHonorsDialogLenientGroupingOverrides() {
        this.settings.setImportGroupingFolderReconciliationEnabled(false);
        TreepeaterModel model = new TreepeaterModel();
        FolderTreeNode anchor = model.createFolder(root(model));

        FolderTreeNode serviceA = model.findOrCreateChildFolder(anchor, "ServiceA");
        FolderTreeNode users = model.findOrCreateChildFolder(serviceA, "users");

        ImportOptions options = pathAwareOptions(
                TreepeaterSettings.IMPORT_LEAF_MODE_DIRECT,
                "base",
                true,
                1,
                60,
                false);
        model.importRequestManual(anchor, rr("GET", "/users/1"), options);

        assertNull(folder(anchor, "users"), "lenient grouping should not create a sibling users folder");
        assertNotNull(leaf(users, "1"), "dialog-enabled lenient grouping attaches under ServiceA/users");
    }
}
