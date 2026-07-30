package treepeater;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import treepeater.importing.ImportOptions.DirectNameMode;
import treepeater.tree.RequestTreeNode;

class DirectImportSettingsTest extends ImportTestSupport {

    @Test
    void insertNodeUsesGlobalIdSetting() {
        this.settings.setDirectImportNameMode(DirectNameMode.ID);
        TreepeaterModel model = new TreepeaterModel();

        model.insertNode(rr("GET", "/api/users"));

        assertNotNull(leaf(root(model), "1"));
    }

    @Test
    void insertNodeUsesGlobalPathSetting() {
        this.settings.setDirectImportNameMode(DirectNameMode.PATH);
        TreepeaterModel model = new TreepeaterModel();

        model.insertNode(rr("GET", "/api/users", null, "/api/users?token=abc"));

        assertNotNull(leaf(root(model), "/api/users"));
    }

    @Test
    void insertNodeUsesGlobalUrlSetting() {
        this.settings.setDirectImportNameMode(DirectNameMode.URL);
        TreepeaterModel model = new TreepeaterModel();

        model.insertNode(rr("GET", "/api/users", "https://example.com/api/users?q=1"));

        RequestTreeNode imported = leaf(root(model), "https://example.com/api/users");
        assertNotNull(imported);
    }
}
