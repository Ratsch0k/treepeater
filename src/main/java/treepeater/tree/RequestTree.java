package treepeater.tree;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.DropMode;
import javax.swing.InputMap;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import treepeater.draggable.RequestTreeNodeSimple;
import treepeater.draggable.TreeTransferHandler;
import treepeater.settings.StatusRegistry;
import treepeater.tree.CustomTreeCellEditor.ProgrammaticEdit;

public class RequestTree extends JTree {
    private static final String MAP_KEY_ACTIVATE_SELECTION = "treepeater.activateTreeSelection";

    /** Property fired via {@link #setFilterQuery} so the search field can stay in sync. */
    public static final String PROPERTY_FILTER_QUERY = "filterQuery";

    private FolderTreeNode root;
    private DefaultTreeModel model;
    private FilteredTreeModel filteredModel;
    private String filterQuery = "";
    private TreePath selectionBeforeFilterUpdate;
    private final CustomTreeUI ui;
    private final boolean dragSupported;
    private CreateFolderHandler createFolderHandler;
    private Consumer<RequestTreeNode> activateRequestFromKeyboardFollowUp;

    @FunctionalInterface
    public interface CreateFolderHandler {
        FolderTreeNode createFolder(TreepeaterNode parent);
    }

    public void setCreateFolderHandler(CreateFolderHandler handler) {
        this.createFolderHandler = handler;
    }

    /**
     * Runs on the EDT after {@link RequestTreeNode#select()} when the user activates the selected
     * row with Enter/Space (typically to focus the request editor).
     */
    public void setActivateRequestFromKeyboardFollowUp(Consumer<RequestTreeNode> followUp) {
        this.activateRequestFromKeyboardFollowUp = followUp;
    }

    public RequestTree() {
        // setDragEnabled(true) throws HeadlessException without a display; drag-and-drop is
        // meaningless headless (e.g. under unit tests), so only enable it when a display exists.
        this.dragSupported = !java.awt.GraphicsEnvironment.isHeadless();
        if (this.dragSupported) {
            this.setDragEnabled(true);
        }
        this.setDropMode(DropMode.ON_OR_INSERT);
        this.getSelectionModel().setSelectionMode(
                TreeSelectionModel.SINGLE_TREE_SELECTION);
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    handleRightClick(e);
                    return;
                }

                int x = e.getX();
                int y = e.getY();

                TreePath path = RequestTree.this.getPathForLocation(x, y);

                if (path == null) {
                    return;
                }

                Object lastComponent = path.getLastPathComponent();

                if (!(lastComponent instanceof TreepeaterNode)) {
                    return;
                }

                TreepeaterNode node = (TreepeaterNode) lastComponent;
                CustomTreeCellEditor editor = (CustomTreeCellEditor) RequestTree.this.getCellEditor();
                editor.prepareHitTest(node);
                CustomTreeCell cell = editor.getEditingPanel();

                Rectangle bounds = RequestTree.this.getPathBounds(path);

                double relativeX = x - bounds.getX();
                int buttonWidth = cell.getComboBoxWidth();

                boolean isInButton = relativeX <= (buttonWidth + 4);

                if (isInButton) {
                    editor.armOpenStatusPopupOnNextShow();
                    editor.setPermitEditStartWithNullMouseEvent(true);
                    try {
                        RequestTree.this.startEditingAtPath(path);
                    } finally {
                        editor.setPermitEditStartWithNullMouseEvent(false);
                    }
                    return;
                }

                RequestTree.this.startEditingAtPath(path);

                boolean isInCloseButton = relativeX >= bounds.getWidth() - cell.getCloseReservedWidth();
                if (isInCloseButton) {
                    node.delete();
                    return;
                }

                if (node instanceof FolderTreeNode) {
                    if (RequestTree.this.isExpanded(path)) {
                        RequestTree.this.collapsePath(path);
                    } else {
                        RequestTree.this.expandPath(path);
                    }
                } else {
                    node.select();
                }
            }
        });
        InputMap inputMap = this.getInputMap(WHEN_FOCUSED);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), MAP_KEY_ACTIVATE_SELECTION);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0, false), MAP_KEY_ACTIVATE_SELECTION);
        ActionMap actionMap = this.getActionMap();
        actionMap.put(MAP_KEY_ACTIVATE_SELECTION,
                new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent ev) {
                        RequestTree.this.activateSelectionFromKeyboard();
                    }
                });
        this.ui = new CustomTreeUI();
        this.setUI(ui);
        this.root = new FolderTreeNode(0, StatusRegistry.getDefault(), "Treepeater");
        this.model = new DefaultTreeModel(this.root);
        this.filteredModel = new FilteredTreeModel(this.model);
        this.filteredModel.setBeforeStructureChangedCallback(this::beforeFilterStructureChanged);
        this.filteredModel.setStructureChangedCallback(this::onFilterStructureChanged);
        this.setTransferHandler(new TreeTransferHandler(this.model, this));
        this.expandRow(0);
        this.setRootVisible(false);
        this.setShowsRootHandles(true);
        this.setModel(this.filteredModel);
        this.setToggleClickCount(0);
        this.setEditable(true);
        CustomTreeCellRenderer renderer = new CustomTreeCellRenderer();
        this.setCellRenderer(renderer);
        this.setCellEditor(new CustomTreeCellEditor(this, renderer));
        this.setRowHeight(-1);
        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                RequestTree.this.ui.invalidateNodeLayoutCache();
            }
        });
    }

    private void activateSelectionFromKeyboard() {
        if (this.isEditing()) {
            return;
        }
        TreePath path = this.getSelectionPath();
        if (path == null) {
            return;
        }
        Object last = path.getLastPathComponent();
        if (last instanceof FolderTreeNode) {
            if (this.isExpanded(path)) {
                this.collapsePath(path);
            } else {
                this.expandPath(path);
            }
        } else if (last instanceof RequestTreeNode rn) {
            rn.select();
            Consumer<RequestTreeNode> followUp = this.activateRequestFromKeyboardFollowUp;
            if (followUp != null) {
                SwingUtilities.invokeLater(() -> followUp.accept(rn));
            }
        }
    }

    private void handleRightClick(MouseEvent e) {
        TreePath path = this.getPathForLocation(e.getX(), e.getY());
        final TreepeaterNode clickedNode;
        if (path != null && path.getLastPathComponent() instanceof TreepeaterNode node) {
            clickedNode = node;
            this.setSelectionPath(path);
        } else {
            clickedNode = null;
        }

        TreepeaterNode target;
        if (path != null) {
            Object component = path.getLastPathComponent();
            if (component instanceof FolderTreeNode folder) {
                target = folder;
            } else if (component instanceof TreepeaterNode node) {
                target = (TreepeaterNode) node.getParent();
            } else {
                target = this.root;
            }
        } else {
            target = this.root;
        }

        JPopupMenu menu = new JPopupMenu();
        boolean canEditNode = clickedNode != null && clickedNode.getParent() != null;
        if (canEditNode) {
            JMenuItem changeName = new JMenuItem("Change Name");
            changeName.addActionListener(ev -> this.startProgrammaticEditForNode(clickedNode,
                    ProgrammaticEdit.RENAME));
            menu.add(changeName);

            JMenuItem changeStatus = new JMenuItem("Change Status");
            changeStatus.addActionListener(ev -> this.startProgrammaticEditForNode(clickedNode,
                    ProgrammaticEdit.STATUS));
            menu.add(changeStatus);

            JMenuItem delete = new JMenuItem("Delete");
            delete.addActionListener(ev -> clickedNode.delete());
            menu.add(delete);

            menu.addSeparator();
        }

        JMenuItem newFolder = new JMenuItem("New Folder");
        newFolder.addActionListener(ev -> this.createFolderAndRename(target));
        menu.add(newFolder);
        menu.show(this, e.getX(), e.getY());
    }

    /**
     * Creates a folder under {@code target} (the root when {@code null}) and starts renaming it.
     * Clears an active search filter first so the new folder is visible. Shared by the tree's
     * right-click menu, the empty-state hint and the toolbar button.
     *
     * @return the created folder, or {@code null} when no create-folder handler is installed
     */
    public FolderTreeNode createFolderAndRename(TreepeaterNode target) {
        if (this.createFolderHandler == null) {
            return null;
        }
        this.setFilterQuery("");
        FolderTreeNode folder = this.createFolderHandler.createFolder(target);
        if (folder == null) {
            return null;
        }
        TreePath parentPath = new TreePath(((TreepeaterNode) folder.getParent()).getPath());
        this.expandPath(parentPath);
        this.startProgrammaticEditForNode(folder, ProgrammaticEdit.RENAME);
        return folder;
    }

    /** @return {@code true} when the tree contains at least one request leaf. */
    public boolean hasRequestNodes() {
        return containsRequestNode(this.root);
    }

    private static boolean containsRequestNode(TreepeaterNode node) {
        if (node instanceof RequestTreeNode) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (containsRequestNode((TreepeaterNode) node.getChildAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Burp theme switches replace the tree UI via {@link #updateUI()}; we always restore
     * {@link CustomTreeUI} so row painting and layout stay consistent.
     */
    @Override
    public void updateUI() {
        super.updateUI();
        setUI(this.ui);
    }

    /** Used by the scroll pane so {@link CustomTreeUI} can size rows to the viewport width. */
    public void setViewportContext(JViewport viewport) {
        this.ui.setViewportContext(viewport);
    }

    public DefaultTreeModel getTreeModel() {
        return this.model;
    }

    /**
     * Sets the raw search query shown in the tree panel's search field. An empty (or blank) value
     * clears the filter. Fires a {@value #PROPERTY_FILTER_QUERY} property change so the field can
     * stay in sync when the tree clears the filter itself.
     */
    public void setFilterQuery(String rawQuery) {
        String next = rawQuery != null ? rawQuery : "";
        if (next.equals(this.filterQuery)) {
            return;
        }
        String old = this.filterQuery;
        this.filterQuery = next;

        // Clearing the filter must keep folders opened during the search open: the structure
        // change fired by the wrapper would otherwise reset the expansion state.
        boolean clearing = this.filteredModel.isFiltering() && TreeTitleMatcher.parse(next).isEmpty();
        Enumeration<TreePath> expandedToRestore = null;
        if (clearing) {
            expandedToRestore = this.getExpandedDescendants(new TreePath(this.root.getPath()));
        }

        this.filteredModel.setQuery(TreeTitleMatcher.parse(next));

        if (expandedToRestore != null) {
            while (expandedToRestore.hasMoreElements()) {
                this.expandPath(expandedToRestore.nextElement());
            }
        }
        this.updateDragEnabled();
        this.firePropertyChange(PROPERTY_FILTER_QUERY, old, next);
    }

    public String getFilterQuery() {
        return this.filterQuery;
    }

    /** @return {@code true} while a non-blank search query is active. */
    public boolean isFiltering() {
        return this.filteredModel.isFiltering();
    }

    /**
     * @return {@code true} when the current filter leaves at least one node visible (below the
     * root); relevant for the "no matches" empty state.
     */
    public boolean hasVisibleNodes() {
        return !this.isFiltering() || this.filteredModel.getChildCount(this.root) > 0;
    }

    /** Display state of {@code node} while filtering; {@code null} when hidden or not filtering. */
    public FilteredTreeModel.NodeMatch getNodeMatch(TreepeaterNode node) {
        return this.filteredModel.getNodeMatch(node);
    }

    /**
     * Reveals {@code node}: clears the filter when the node would be hidden, then selects and
     * scrolls to it. Use for programmatic selections (e.g. after agent copies).
     */
    public void revealNode(TreepeaterNode node) {
        if (node == null) {
            return;
        }
        if (this.isFiltering() && this.filteredModel.getNodeMatch(node) == null) {
            this.setFilterQuery("");
        }
        TreePath path = new TreePath(node.getPath());
        this.setSelectionPath(path);
        this.scrollPathToVisible(path);
    }

    /**
     * Selects the first visible request row (falling back to the first visible row) and scrolls
     * to it. Used by the search field's Down/Enter shortcut; the caller then focuses the tree.
     *
     * @return {@code true} when a row was selected
     */
    public boolean selectFirstVisibleRequestRow() {
        int rowCount = this.getRowCount();
        int fallbackRow = -1;
        for (int row = 0; row < rowCount; row++) {
            if (fallbackRow < 0) {
                fallbackRow = row;
            }
            TreePath path = this.getPathForRow(row);
            if (path != null && path.getLastPathComponent() instanceof RequestTreeNode) {
                this.setSelectionRow(row);
                this.scrollRowToVisible(row);
                return true;
            }
        }
        if (fallbackRow >= 0) {
            this.setSelectionRow(fallbackRow);
            this.scrollRowToVisible(fallbackRow);
            return true;
        }
        return false;
    }

    /**
     * Callback from {@link FilteredTreeModel} right before a filtered structure change is
     * fired: the JTree drops both selection and expansion on root structure changes, so
     * capture the selection here for {@link #onFilterStructureChanged} to restore.
     */
    private void beforeFilterStructureChanged() {
        this.selectionBeforeFilterUpdate = this.getSelectionPath();
    }

    /**
     * Callback from {@link FilteredTreeModel} after a query or source update re-filtered the
     * tree: expands the path to every direct match, restores a selection that survived the
     * filter (clearing one that did not) and invalidates the row layout cache (bold text
     * changes row widths).
     */
    private void onFilterStructureChanged() {
        if (this.isFiltering()) {
            for (TreeNode node : this.filteredModel.getDirectMatchNodes()) {
                TreeNode parent = node.getParent();
                if (parent instanceof TreepeaterNode parentNode) {
                    this.expandPath(new TreePath(parentNode.getPath()));
                }
            }
        }
        this.restoreSelectionAfterFilterUpdate();
        this.invalidateLayoutCache();
    }

    /**
     * Keeps the selection if the node is still part of the filtered view, otherwise clears it.
     * After a filter clear everything is visible again, so the selection is always restored.
     */
    private void restoreSelectionAfterFilterUpdate() {
        TreePath selection = this.selectionBeforeFilterUpdate;
        this.selectionBeforeFilterUpdate = null;
        if (selection == null) {
            return;
        }
        Object last = selection.getLastPathComponent();
        if (!(last instanceof TreepeaterNode node)) {
            return;
        }
        if (!this.isFiltering() || this.filteredModel.getNodeMatch(node) != null) {
            this.setSelectionPath(selection);
        } else {
            this.clearSelection();
        }
    }

    /** Drag-and-drop is refused while a filter is active (the visible order is not the model order). */
    private void updateDragEnabled() {
        if (this.dragSupported) {
            this.setDragEnabled(!this.isFiltering());
        }
    }


    public void insertRootNode(TreepeaterNode node) {
        this.model.insertNodeInto(node, this.root, this.root.getChildCount());
    }

    public void insertNodeInto(TreepeaterNode child, TreepeaterNode parent, int index) {
        this.model.insertNodeInto(child, parent, index);
    }

    /** Call once after persistence has attached all top-level nodes (offline-built subtrees). */
    public void syncUiAfterBulkLoad() {
        this.model.nodeStructureChanged(this.root);
    }

    /** Clears cached row sizes so layout is recomputed for the current viewport width. */
    public void invalidateLayoutCache() {
        this.ui.invalidateNodeLayoutCache();
    }

    public List<RequestTreeNodeSimple> toSimpleRepeaterList() {
        ArrayList<RequestTreeNodeSimple> children = new ArrayList<>();

        for (int idx = 0; idx < this.root.getChildCount(); idx++) {
            TreepeaterNode child = (TreepeaterNode) this.root.getChildAt(idx);
            String childName = "[TR] " + child.getName();
            collectRepeaterNodes(childName, child, children);
        }

        return children;
    }

    private void collectRepeaterNodes(String prefix, TreepeaterNode node, List<RequestTreeNodeSimple> out) {
        if (node instanceof RequestTreeNode requestNode) {
            out.add(new RequestTreeNodeSimple(requestNode.getRequest(), prefix));
        }

        for (int idx = 0; idx < node.getChildCount(); idx++) {
            TreepeaterNode child = (TreepeaterNode) node.getChildAt(idx);
            String childName = prefix + " / " + child.getName();
            collectRepeaterNodes(childName, child, out);
        }
    }

    public boolean startProgrammaticEditForNode(TreepeaterNode node, ProgrammaticEdit editType) {
        if (node == null || node.getParent() == null) {
            return false;
        }
        TreePath path = new TreePath(node.getPath());
        this.setSelectionPath(path);
        this.scrollPathToVisible(path);
        CustomTreeCellEditor editor = (CustomTreeCellEditor) this.getCellEditor();
        editor.setProgrammaticEdit(editType);
        this.startEditingAtPath(path);
        if (!this.isEditing()) {
            editor.setProgrammaticEdit(ProgrammaticEdit.NONE);
            return false;
        }
        return true;
    }
}
