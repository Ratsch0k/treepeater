package treepeater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import treepeater.tree.FolderTreeNode;
import treepeater.tree.RequestTreeNode;

class UtilitiesTest {

    @Test
    void slashPath_skipsSyntheticRoot_andOrdersRootToLeaf() {
        RequestTreeNode root = new RequestTreeNode(0, "Treepeater", null, null);
        RequestTreeNode a = new RequestTreeNode(1, "Folder", null, null);
        RequestTreeNode b = new RequestTreeNode(2, "Request", null, null);
        root.add(a);
        a.add(b);
        assertEquals("Folder/Request", Utilities.slashPathForNode(b));
    }

    @Test
    void slashPath_singleSegment_isJustName() {
        RequestTreeNode root = new RequestTreeNode(0, "Treepeater", null, null);
        RequestTreeNode leaf = new RequestTreeNode(5, "Only", null, null);
        root.add(leaf);
        assertEquals("Only", Utilities.slashPathForNode(leaf));
    }

    @Test
    void slashPath_includesFolderTreeNodeAncestors() {
        RequestTreeNode root = new RequestTreeNode(0, "Treepeater", null, null);
        FolderTreeNode folder = new FolderTreeNode(1, null, "MyFolder");
        RequestTreeNode req = new RequestTreeNode(2, "GET /api", null, null);
        root.add(folder);
        folder.add(req);
        assertEquals("MyFolder/GET /api", Utilities.slashPathForNode(req));
        assertEquals("MyFolder", Utilities.parentSlashPathForNode(req));
    }

    @Test
    void parentSlashPath_emptyForRootLevelTab() {
        RequestTreeNode root = new RequestTreeNode(0, "Treepeater", null, null);
        RequestTreeNode leaf = new RequestTreeNode(5, "Only", null, null);
        root.add(leaf);
        assertEquals("", Utilities.parentSlashPathForNode(leaf));
    }

    @Test
    void parentSlashPath_nestedFolders() {
        RequestTreeNode root = new RequestTreeNode(0, "Treepeater", null, null);
        RequestTreeNode a = new RequestTreeNode(1, "A", null, null);
        RequestTreeNode b = new RequestTreeNode(2, "B", null, null);
        RequestTreeNode c = new RequestTreeNode(3, "Request", null, null);
        root.add(a);
        a.add(b);
        b.add(c);
        assertEquals("A/B", Utilities.parentSlashPathForNode(c));
    }

    @Test
    void parentSlashPath_handlesSlashesInTabName() {
        RequestTreeNode root = new RequestTreeNode(0, "Treepeater", null, null);
        FolderTreeNode folder = new FolderTreeNode(1, null, "MyFolder");
        RequestTreeNode req = new RequestTreeNode(2, "GET /api", null, null);
        root.add(folder);
        folder.add(req);
        assertEquals("MyFolder", Utilities.parentSlashPathForNode(req));
    }

    @Test
    void truncatePathMiddle_keepsShortPath() {
        FontMetrics fm = testFontMetrics();
        assertEquals("A/B/C", Utilities.truncatePathMiddle("A/B/C", fm.stringWidth("A/B/C"), fm));
    }

    @Test
    void truncatePathMiddle_showsFirstAndLastSegment() {
        FontMetrics fm = testFontMetrics();
        String path = "Alpha/Beta/Gamma/Delta/Epsilon";
        String expected = "Alpha/.../Epsilon";
        int maxWidth = fm.stringWidth(expected);
        String truncated = Utilities.truncatePathMiddle(path, maxWidth, fm);
        assertEquals(expected, truncated);
    }

    @Test
    void truncatePathMiddle_singleSegmentUsesEndEllipsis() {
        FontMetrics fm = testFontMetrics();
        String path = "VeryLongFolderName";
        int maxWidth = fm.stringWidth("Short");
        String truncated = Utilities.truncatePathMiddle(path, maxWidth, fm);
        assertTrue(truncated.endsWith("..."));
        assertTrue(fm.stringWidth(truncated) <= maxWidth);
    }

    private static FontMetrics testFontMetrics() {
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        return img.createGraphics().getFontMetrics(font);
    }
}
