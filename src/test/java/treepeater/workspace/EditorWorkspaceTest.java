package treepeater.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Test;

import treepeater.tree.RequestTreeNode;

class EditorWorkspaceTest {

    private static RequestTreeNode node(int id) {
        return new RequestTreeNode(id, "req-" + id, null, null);
    }

    @Test
    void splitGroup_movesActiveTabToNewGroup() {
        EditorWorkspace ws = new EditorWorkspace();
        TabGroupNode root = (TabGroupNode) ws.root();
        RequestTreeNode a = node(1);
        RequestTreeNode b = node(2);
        root.addTab(a);
        root.addTab(b);
        root.setSelectedIndex(1);
        ws.setFocusedTabGroupId(root.id());

        EditorWorkspace.SplitResult result = ws.splitGroup(root.id(), SplitOrientation.HORIZONTAL);
        assertNotNull(result);

        TabGroupNode original = ws.findGroupById(result.originalGroupId());
        TabGroupNode created = ws.findGroupById(result.newGroupId());
        assertNotNull(original);
        assertNotNull(created);
        assertTrue(original.contains(a));
        assertFalse(original.contains(b));
        assertTrue(created.contains(b));
        assertEquals(created.id(), ws.focusedTabGroupId());
        assertTrue(ws.root() instanceof SplitNode);
    }

    @Test
    void splitGroup_rejectedWhenOnlyOneTab() {
        EditorWorkspace ws = new EditorWorkspace();
        TabGroupNode root = (TabGroupNode) ws.root();
        root.addTab(node(1));

        assertNull(ws.splitGroup(root.id(), SplitOrientation.HORIZONTAL));
        assertTrue(ws.root() instanceof TabGroupNode);
    }

    @Test
    void removeTab_doesNotChangeRootWhenGroupStillHasTabs() {
        EditorWorkspace ws = new EditorWorkspace();
        TabGroupNode root = (TabGroupNode) ws.root();
        RequestTreeNode a = node(1);
        RequestTreeNode b = node(2);
        root.addTab(a);
        root.addTab(b);
        WorkspaceNode rootBefore = ws.root();

        assertFalse(ws.removeTab(a));
        assertTrue(ws.root() instanceof TabGroupNode);
        assertEquals(rootBefore, ws.root());
        assertFalse(root.contains(a));
        assertTrue(root.contains(b));
    }

    @Test
    void removeTab_collapsesEmptyGroup() {
        EditorWorkspace ws = new EditorWorkspace();
        TabGroupNode root = (TabGroupNode) ws.root();
        RequestTreeNode a = node(1);
        RequestTreeNode b = node(2);
        root.addTab(a);
        root.addTab(b);
        root.setSelectedIndex(1);
        ws.splitGroup(root.id(), SplitOrientation.HORIZONTAL);

        assertTrue(ws.removeTab(b));
        assertTrue(ws.root() instanceof TabGroupNode);
        TabGroupNode group = (TabGroupNode) ws.root();
        assertEquals(1, group.tabs().size());
        assertTrue(group.contains(a));
    }

    @Test
    void moveTab_transfersBetweenGroups() {
        EditorWorkspace ws = new EditorWorkspace();
        TabGroupNode root = (TabGroupNode) ws.root();
        RequestTreeNode a = node(1);
        RequestTreeNode b = node(2);
        root.addTab(a);
        root.addTab(b);
        ws.splitGroup(root.id(), SplitOrientation.VERTICAL);

        TabGroupNode from = ws.findGroupContaining(a);
        TabGroupNode to = ws.findGroupContaining(b);
        assertNotNull(from);
        assertNotNull(to);

        ws.moveTab(a, from.id(), to.id(), 0);
        assertFalse(from.contains(a));
        assertTrue(to.contains(a));
        assertEquals(0, to.indexOf(a));
    }

    @Test
    void fromLegacyTabs_buildsSingleGroup() {
        RequestTreeNode a = node(1);
        RequestTreeNode b = node(2);
        LinkedList<RequestTreeNode> tabs = new LinkedList<>(List.of(a, b));
        EditorWorkspace ws = EditorWorkspace.fromLegacyTabs(tabs, b);

        assertTrue(ws.root() instanceof TabGroupNode);
        TabGroupNode group = (TabGroupNode) ws.root();
        assertEquals(2, group.tabs().size());
        assertEquals(1, group.selectedIndex());
        assertEquals(b, group.selectedTab());
    }

    @Test
    void nestedSplit_collapsesInnerEmptyGroup() {
        EditorWorkspace ws = new EditorWorkspace();
        TabGroupNode root = (TabGroupNode) ws.root();
        RequestTreeNode a = node(1);
        RequestTreeNode b = node(2);
        root.addTab(a);
        root.addTab(b);
        root.setSelectedIndex(1);
        ws.splitGroup(root.id(), SplitOrientation.HORIZONTAL);

        TabGroupNode newGroup = ws.focusedGroup();
        RequestTreeNode c = node(3);
        newGroup.addTab(c);
        newGroup.setSelectedIndex(newGroup.indexOf(c));
        ws.splitGroup(newGroup.id(), SplitOrientation.VERTICAL);

        RequestTreeNode only = newGroup.tabs().isEmpty() ? null : newGroup.tabs().getFirst();
        TabGroupNode inner = ws.allTabGroups().stream()
                .filter(g -> !g.id().equals(root.id()) && !g.id().equals(newGroup.id()))
                .findFirst()
                .orElse(null);
        if (inner != null && !inner.tabs().isEmpty()) {
            only = inner.tabs().getFirst();
            ws.removeTab(only);
        }
        ws.collapseEmpty();

        assertNotNull(ws.findGroupById(root.id()));
        assertNull(ws.findGroupContaining(only));
    }
}
