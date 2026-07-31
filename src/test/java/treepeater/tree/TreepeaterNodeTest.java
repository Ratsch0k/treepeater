package treepeater.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import treepeater.settings.StatusRegistry;

class TreepeaterNodeTest {

    @Test
    void toStringReflectsRenamedFolderName() {
        FolderTreeNode folder = new FolderTreeNode(1, StatusRegistry.getDefault(), "New Folder");

        assertEquals("New Folder", folder.getName());
        assertEquals("New Folder", folder.toString());

        folder.setName("API");

        assertEquals("API", folder.getName());
        assertEquals("API", folder.toString());
    }
}
