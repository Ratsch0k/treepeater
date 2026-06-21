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

    @Test
    void removeTab_nestedHorizontalCollapse_reportsLayoutChange() {
        EditorWorkspace ws = new EditorWorkspace();
        TabGroupNode root = (TabGroupNode) ws.root();
        RequestTreeNode n1 = node(1);
        RequestTreeNode n2 = node(2);
        RequestTreeNode n3 = node(3);
        RequestTreeNode n4 = node(4);
        root.addTab(n1);
        root.addTab(n2);
        root.setSelectedIndex(1);
        ws.splitGroup(root.id(), SplitOrientation.HORIZONTAL);

        TabGroupNode middle = ws.findGroupContaining(n2);
        assertNotNull(middle);
        middle.addTab(n3);
        middle.setSelectedIndex(middle.indexOf(n3));
        ws.splitGroup(middle.id(), SplitOrientation.HORIZONTAL);

        TabGroupNode rightInner = ws.focusedGroup();
        rightInner.addTab(n4);
        rightInner.setSelectedIndex(rightInner.indexOf(n4));
        ws.splitGroup(rightInner.id(), SplitOrientation.HORIZONTAL);

        middle = ws.findGroupContaining(n2);
        assertNotNull(middle);
        String middleGroupId = middle.id();

        assertTrue(ws.removeTab(n2));
        assertNull(ws.findGroupById(middleGroupId));
        assertEquals(3, ws.allTabGroups().size());
    }

    @Test
    void removeTab_gridBottomLeftCollapse_reportsLayoutChange() {
        EditorWorkspace ws = new EditorWorkspace();
        TabGroupNode root = (TabGroupNode) ws.root();
        RequestTreeNode n1 = node(1);
        RequestTreeNode n2 = node(2);
        RequestTreeNode n3 = node(3);
        RequestTreeNode n4 = node(4);
        root.addTab(n1);
        root.addTab(n2);
        root.setSelectedIndex(1);
        ws.splitGroup(root.id(), SplitOrientation.VERTICAL);

        TabGroupNode bottom = ws.focusedGroup();
        bottom.addTab(n3);
        bottom.setSelectedIndex(bottom.indexOf(n3));
        ws.splitGroup(bottom.id(), SplitOrientation.HORIZONTAL);

        TabGroupNode top = ws.findGroupContaining(n1);
        assertNotNull(top);
        top.addTab(n4);
        top.setSelectedIndex(top.indexOf(n4));
        ws.splitGroup(top.id(), SplitOrientation.VERTICAL);

        TabGroupNode bottomLeft = ws.findGroupContaining(n2);
        assertNotNull(bottomLeft);
        String bottomLeftGroupId = bottomLeft.id();

        assertTrue(ws.removeTab(n2));
        assertNull(ws.findGroupById(bottomLeftGroupId));

        SplitNode rootSplit = (SplitNode) ws.root();
        assertEquals(SplitOrientation.VERTICAL, rootSplit.orientation());
        assertTrue(rootSplit.second() instanceof TabGroupNode);
        assertEquals(n3, ((TabGroupNode) rootSplit.second()).selectedTab());
    }
}
