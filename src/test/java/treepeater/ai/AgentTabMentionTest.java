package treepeater.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import treepeater.tree.FolderTreeNode;
import treepeater.tree.RequestTreeNode;

class AgentTabMentionTest {

    @Test
    void slashPath_skipsSyntheticRoot_andOrdersRootToLeaf() {
        RequestTreeNode root = new RequestTreeNode(0, "Treepeater", null, null);
        RequestTreeNode a = new RequestTreeNode(1, "Folder", null, null);
        RequestTreeNode b = new RequestTreeNode(2, "Request", null, null);
        root.add(a);
        a.add(b);
        assertEquals("Folder/Request", AgentTabMention.slashPathForNode(b));
    }

    @Test
    void slashPath_singleSegment_isJustName() {
        RequestTreeNode root = new RequestTreeNode(0, "Treepeater", null, null);
        RequestTreeNode leaf = new RequestTreeNode(5, "Only", null, null);
        root.add(leaf);
        assertEquals("Only", AgentTabMention.slashPathForNode(leaf));
    }

    @Test
    void slashPath_includesFolderTreeNodeAncestors() {
        RequestTreeNode root = new RequestTreeNode(0, "Treepeater", null, null);
        FolderTreeNode folder = new FolderTreeNode(1, null, "MyFolder");
        RequestTreeNode req = new RequestTreeNode(2, "GET /api", null, null);
        root.add(folder);
        folder.add(req);
        assertEquals("MyFolder/GET /api", AgentTabMention.slashPathForNode(req));
    }
}
