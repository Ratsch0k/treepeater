package treepeater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import treepeater.importing.ImportOptions;
import treepeater.settings.TreepeaterSettings;
import treepeater.tree.FolderTreeNode;
import treepeater.tree.RequestTreeNode;

/** Covers the default import status preference for Send-to-Treepeater paths (not copy). */
class DefaultImportStatusTest extends ImportTestSupport {

    @Test
    void insertNodeUsesConfiguredImportDefaultStatus() {
        this.settings.setImportDefaultStatusId("TODO");
        TreepeaterModel model = new TreepeaterModel();

        model.insertNode(rr("GET", "/api/users"));

        RequestTreeNode imported = leaf(root(model), "1");
        assertNotNull(imported);
        assertEquals("TODO", imported.getStatus().getId());
    }

    @Test
    void pathAwareImportUsesConfiguredImportDefaultStatus() {
        this.settings.setImportDefaultStatusId("TODO");
        this.settings.setImportLeafMode(TreepeaterSettings.IMPORT_LEAF_MODE_DIRECT);
        TreepeaterModel model = new TreepeaterModel();

        model.importRequestPathAware(rr("GET", "/api/users"));

        FolderTreeNode api = folder(root(model), "api");
        assertNotNull(api);
        RequestTreeNode imported = leaf(api, "users");
        assertNotNull(imported);
        assertEquals("TODO", imported.getStatus().getId());
        assertEquals("TODO", ImportOptions.fromSettings().statusId());
    }

    @Test
    void copyKeepsSourceStatusIgnoringImportDefault() {
        this.settings.setImportDefaultStatusId("TODO");
        TreepeaterModel model = new TreepeaterModel();

        model.insertNode(rrWithService("GET", "/api/users"));
        RequestTreeNode source = leaf(root(model), "1");
        assertNotNull(source);
        source.setStatus(Treepeater.getStatusRegistry().getById("FINDING"));
        assertEquals("FINDING", source.getStatus().getId());

        HttpRequestResponse rr = rrWithService("GET", "/api/users");
        RequestTreeNode copy = model.copyAsSiblingUnderSameParent(
                source, rr.request(), rr.response());

        assertNotNull(copy);
        assertEquals("FINDING", copy.getStatus().getId());
        assertEquals("TODO", this.settings.getImportDefaultStatusId());
    }

    private static HttpRequestResponse rrWithService(String method, String path) {
        HttpRequestResponse rr = rr(method, path);
        HttpService service = mock(HttpService.class);
        lenient().when(service.host()).thenReturn("example.com");
        lenient().when(rr.request().httpService()).thenReturn(service);
        return rr;
    }
}
