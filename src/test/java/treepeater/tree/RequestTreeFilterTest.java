package treepeater.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import javax.swing.tree.TreePath;
import javax.swing.JTree.DropLocation;
import javax.swing.TransferHandler.TransferSupport;

import org.junit.jupiter.api.Test;

import burp.api.montoya.http.message.requests.HttpRequest;

import treepeater.ImportTestSupport;
import treepeater.draggable.TreeTransferHandler;
import treepeater.settings.StatusRegistry;
import treepeater.tree.FilteredTreeModel.Kind;

/**
 * Headless behavior tests for the {@link RequestTree} search filter: filter state, selection
 * handling, "New Folder" and programmatic reveal clearing the filter.
 */
class RequestTreeFilterTest extends ImportTestSupport {
    private final RequestTree tree = new RequestTree();

    private FolderTreeNode addFolder(String name, TreepeaterNode parent) {
        FolderTreeNode folder = new FolderTreeNode((int) (Math.random() * 100000), StatusRegistry.getDefault(), name);
        this.tree.getTreeModel().insertNodeInto(folder, parent, parent.getChildCount());
        return folder;
    }

    private RequestTreeNode addRequest(String name, TreepeaterNode parent) {
        RequestTreeNode request = new RequestTreeNode((int) (Math.random() * 100000), name, null, null);
        this.tree.getTreeModel().insertNodeInto(request, parent, parent.getChildCount());
        return request;
    }

    private TreepeaterNode root() {
        return (TreepeaterNode) this.tree.getTreeModel().getRoot();
    }

    /** Mirrors the app init: expands the (hidden) root so top-level rows are shown. */
    private void syncTree() {
        this.tree.syncUiAfterBulkLoad();
    }

    @Test
    void filterQueryRoundTripAndFilteringState() {
        assertTrue(this.tree.getFilterQuery().isEmpty());
        assertFalse(this.tree.isFiltering());

        this.tree.setFilterQuery("login");

        assertEquals("login", this.tree.getFilterQuery());
        assertTrue(this.tree.isFiltering());

        this.tree.setFilterQuery("   ");

        assertEquals("   ", this.tree.getFilterQuery());
        assertFalse(this.tree.isFiltering());
    }

    @Test
    void filteringHidesUnrelatedNodesAndKeepsSelectionWhenVisible() {
        FolderTreeNode folder = this.addFolder("Login", this.root());
        RequestTreeNode login = this.addRequest("Login page", folder);
        this.addRequest("Unrelated", this.root());
        this.syncTree();

        this.tree.setFilterQuery("login");

        // Only the matching folder row is visible below the root.
        assertSame(folder, this.tree.getPathForRow(0).getLastPathComponent());
        assertEquals(Kind.DIRECT, this.tree.getNodeMatch(folder).kind());

        // Select the still-visible direct match; a further update keeps it.
        this.tree.setSelectionPath(new TreePath(login.getPath()));
        this.tree.setFilterQuery("login page");
        assertSame(login, this.tree.getSelectionPath().getLastPathComponent());
    }

    @Test
    void selectionIsClearedWhenItBecomesHidden() {
        RequestTreeNode unrelated = this.addRequest("Unrelated", this.root());
        this.addRequest("Login page", this.root());
        this.syncTree();

        this.tree.setSelectionPath(new TreePath(unrelated.getPath()));
        this.tree.setFilterQuery("login");

        assertNull(this.tree.getSelectionPath());
    }

    @Test
    void revealNodeClearsFilterWhenHiddenAndSelects() {
        RequestTreeNode unrelated = this.addRequest("Unrelated", this.root());
        this.addRequest("Login page", this.root());
        this.syncTree();

        this.tree.setFilterQuery("login");
        assertNull(this.tree.getNodeMatch(unrelated));

        this.tree.revealNode(unrelated);

        assertFalse(this.tree.isFiltering());
        assertSame(unrelated, this.tree.getSelectionPath().getLastPathComponent());
    }

    @Test
    void createFolderAndRenameClearsFilterFirst() {
        this.addRequest("Login page", this.root());
        this.syncTree();
        this.tree.setCreateFolderHandler(parent -> {
            FolderTreeNode folder = new FolderTreeNode(
                    (int) (Math.random() * 100000), StatusRegistry.getDefault(), "New Folder");
            this.tree.getTreeModel().insertNodeInto(folder, parent, parent.getChildCount());
            return folder;
        });

        this.tree.setFilterQuery("nomatch");
        assertTrue(this.tree.isFiltering());

        FolderTreeNode folder = this.tree.createFolderAndRename(this.root());

        assertFalse(this.tree.isFiltering());
        assertTrue(this.tree.getFilterQuery().isEmpty());
        assertSame(this.root(), folder.getParent());
    }

    @Test
    void selectFirstVisibleRequestRowPicksTheFirstRequestRow() {
        this.addFolder("Login", this.root());
        RequestTreeNode login = this.addRequest("Login page", this.root());
        this.addRequest("Other", this.root());
        this.syncTree();

        assertTrue(this.tree.selectFirstVisibleRequestRow());
        assertSame(login, this.tree.getSelectionPath().getLastPathComponent());

        // Filtering to a single request row still selects it.
        this.tree.setFilterQuery("other");
        assertTrue(this.tree.selectFirstVisibleRequestRow());
    }

    @Test
    void emptyTreeHasNoFirstVisibleRow() {
        assertFalse(this.tree.selectFirstVisibleRequestRow());
    }

    private RequestTreeNode addRequest(String name, String method, TreepeaterNode parent) {
        HttpRequest req = mock(HttpRequest.class);
        when(req.method()).thenReturn(method);
        RequestTreeNode request = new RequestTreeNode((int) (Math.random() * 100000), name, req, null);
        this.tree.getTreeModel().insertNodeInto(request, parent, parent.getChildCount());
        return request;
    }

    @Test
    void methodAndStatusFiltersToggleFilteringAndFireEvents() {
        RequestTreeNode get = this.addRequest("a", "GET", this.root());
        RequestTreeNode post = this.addRequest("b", "post", this.root());
        this.syncTree();
        List<TreeFilter> events = new ArrayList<>();
        this.tree.addPropertyChangeListener(RequestTree.PROPERTY_TREE_FILTER,
                ev -> events.add((TreeFilter) ev.getNewValue()));

        this.tree.setMethodFilter(List.of("get"));

        assertTrue(this.tree.isFiltering());
        assertEquals(1, events.size());
        assertEquals(Kind.DIRECT, this.tree.getNodeMatch(get).kind());
        assertNull(this.tree.getNodeMatch(post));

        this.tree.setMethodFilter(List.of("GET"));
        assertEquals(1, events.size());

        this.tree.setStatusFilter(List.of("todo"));
        assertEquals(2, events.size());
        assertFalse(this.tree.hasVisibleNodes());

        this.tree.clearAttributeFilters();
        assertFalse(this.tree.isFiltering());
        assertEquals(3, events.size());
    }

    @Test
    void attributeFiltersDoNotChangeTheSearchText() {
        this.addRequest("a", "GET", this.root());
        this.tree.setFilterQuery("a");

        this.tree.setMethodFilter(List.of("GET"));
        assertEquals("a", this.tree.getFilterQuery());

        this.tree.clearFilters();
        assertEquals("", this.tree.getFilterQuery());
        assertFalse(this.tree.isFiltering());
    }

    @Test
    void revealNodeClearsAttributeFiltersToo() {
        this.addRequest("a", "GET", this.root());
        RequestTreeNode post = this.addRequest("b", "POST", this.root());
        this.syncTree();
        this.tree.setMethodFilter(List.of("GET"));
        assertNull(this.tree.getNodeMatch(post));

        this.tree.revealNode(post);

        assertFalse(this.tree.isFiltering());
        assertSame(post, this.tree.getSelectionPath().getLastPathComponent());
    }

    @Test
    void collectRequestMethodsListsDistinctUpperCasedMethodsStandardFirst() {
        FolderTreeNode folder = this.addFolder("f", this.root());
        this.addRequest("a", "post", folder);
        this.addRequest("b", "GET", this.root());
        this.addRequest("c", "PROPFIND", this.root());
        this.addRequest("d", "get", this.root());
        this.addRequest("no request", this.root());

        assertEquals(List.of("GET", "POST", "PROPFIND"), this.tree.collectRequestMethods());
    }

    @Test
    void retainStatusFilterDropsRemovedStatuses() {
        this.addRequest("a", "GET", this.root());
        this.tree.setStatusFilter(List.of("TODO", "gone"));

        this.tree.retainStatusFilter(List.of("DEFAULT", "TODO"));

        assertEquals(java.util.Set.of("todo"), this.tree.getTreeFilter().statusIds());
    }

    @Test
    void dndIsRefusedWhileFiltering() {
        FolderTreeNode folder = this.addFolder("Login", this.root());
        RequestTreeNode login = this.addRequest("Login page", this.root());
        this.syncTree();

        DropLocation dropLocation =
                mock(DropLocation.class);
        when(dropLocation.getPath()).thenReturn(new TreePath(folder.getPath()));
        when(dropLocation.getChildIndex()).thenReturn(-1);

        TransferSupport support =
                mock(TransferSupport.class);
        when(support.isDrop()).thenReturn(true);
        when(support.isDataFlavorSupported(org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        when(support.getComponent()).thenReturn(this.tree);
        when(support.getDropLocation()).thenReturn(dropLocation);

        TreeTransferHandler handler = new TreeTransferHandler(this.tree.getTreeModel(), this.tree);

        // Without a filter, dropping the selected request row onto the folder is allowed.
        this.tree.setSelectionPath(new TreePath(login.getPath()));
        assertTrue(handler.canImport(support));

        // With a filter, the import is refused up front: the visible order is not the model order.
        this.tree.setFilterQuery("login");
        assertTrue(this.tree.isFiltering());
        assertFalse(handler.canImport(support));

        this.tree.setFilterQuery("");
        assertFalse(this.tree.isFiltering());
    }

    private TransferSupport dropSupportOn(TreepeaterNode destination) {
        DropLocation dropLocation =
                mock(DropLocation.class);
        when(dropLocation.getPath()).thenReturn(new TreePath(destination.getPath()));
        when(dropLocation.getChildIndex()).thenReturn(-1);

        TransferSupport support =
                mock(TransferSupport.class);
        when(support.isDrop()).thenReturn(true);
        when(support.isDataFlavorSupported(any()))
                .thenReturn(true);
        when(support.getComponent()).thenReturn(this.tree);
        when(support.getDropLocation()).thenReturn(dropLocation);
        return support;
    }

    @Test
    void folderWithCollapsedSubfolderCanBeDroppedOnRoot() {
        FolderTreeNode outer = this.addFolder("Outer", this.root());
        FolderTreeNode inner = this.addFolder("Inner", outer);
        this.addRequest("Leaf", inner);
        this.syncTree();
        this.tree.expandPath(new TreePath(outer.getPath()));
        this.tree.collapsePath(new TreePath(inner.getPath()));
        this.tree.setSelectionPath(new TreePath(outer.getPath()));

        TreeTransferHandler handler = new TreeTransferHandler(this.tree.getTreeModel(), this.tree);
        assertTrue(handler.canImport(this.dropSupportOn(this.root())));
    }

    @Test
    void collapsedFolderWithChildrenCanBeDroppedOnRoot() {
        FolderTreeNode outer = this.addFolder("Outer", this.root());
        this.addRequest("Leaf", outer);
        this.syncTree();
        this.tree.collapsePath(new TreePath(outer.getPath()));
        this.tree.setSelectionPath(new TreePath(outer.getPath()));

        TreeTransferHandler handler = new TreeTransferHandler(this.tree.getTreeModel(), this.tree);
        assertTrue(handler.canImport(this.dropSupportOn(this.root())));
    }

    @Test
    void folderCannotBeDroppedOntoItselfOrItsDescendants() {
        FolderTreeNode outer = this.addFolder("Outer", this.root());
        FolderTreeNode inner = this.addFolder("Inner", outer);
        this.addRequest("Leaf", inner);
        this.syncTree();
        this.tree.expandPath(new TreePath(outer.getPath()));
        this.tree.collapsePath(new TreePath(inner.getPath()));
        this.tree.setSelectionPath(new TreePath(outer.getPath()));

        TreeTransferHandler handler = new TreeTransferHandler(this.tree.getTreeModel(), this.tree);
        assertFalse(handler.canImport(this.dropSupportOn(outer)));
        assertFalse(handler.canImport(this.dropSupportOn(inner)));
    }

    @Test
    void movingNodeKeepsRowLayoutConsistent() {
        FolderTreeNode target = addFolder("Target", root());
        FolderTreeNode other = addFolder("Other", root());
        RequestTreeNode moved = addRequest("Moved", other);
        addRequest("Sibling", other);
        syncTree();
        this.tree.expandPath(new TreePath(other.getPath()));
        this.tree.setSelectionPath(new TreePath(moved.getPath()));

        this.tree.getTreeModel().removeNodeFromParent(moved);
        this.tree.getTreeModel().insertNodeInto(moved, target, 0);
        this.tree.expandPath(new TreePath(target.getPath()));

        int rows = this.tree.getRowCount();
        assertEquals(4, rows);
        for (int row = 0; row < rows; row++) {
            assertEquals(row, this.tree.getRowForPath(this.tree.getPathForRow(row)));
        }
        assertEquals(1, this.tree.getRowForPath(new TreePath(moved.getPath())));
    }}
