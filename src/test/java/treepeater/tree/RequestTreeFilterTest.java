package treepeater.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.tree.TreePath;

import org.junit.jupiter.api.Test;

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

    @Test
    void dndIsRefusedWhileFiltering() {
        FolderTreeNode folder = this.addFolder("Login", this.root());
        RequestTreeNode login = this.addRequest("Login page", this.root());
        this.syncTree();

        javax.swing.JTree.DropLocation dropLocation =
                org.mockito.Mockito.mock(javax.swing.JTree.DropLocation.class);
        org.mockito.Mockito.when(dropLocation.getPath()).thenReturn(new TreePath(folder.getPath()));
        org.mockito.Mockito.when(dropLocation.getChildIndex()).thenReturn(-1);

        javax.swing.TransferHandler.TransferSupport support =
                org.mockito.Mockito.mock(javax.swing.TransferHandler.TransferSupport.class);
        org.mockito.Mockito.when(support.isDrop()).thenReturn(true);
        org.mockito.Mockito.when(support.isDataFlavorSupported(org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);
        org.mockito.Mockito.when(support.getComponent()).thenReturn(this.tree);
        org.mockito.Mockito.when(support.getDropLocation()).thenReturn(dropLocation);

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
}
