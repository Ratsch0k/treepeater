package treepeater.tree;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.DropMode;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import treepeater.draggable.RequestTreeNodeSimple;
import treepeater.draggable.TreeTransferHandler;
import treepeater.settings.StatusRegistry;
import treepeater.tree.CustomTreeCellEditor.ProgrammaticEdit;

public class RequestTree extends JTree {
    private FolderTreeNode root;
    private DefaultTreeModel model;
    private final CustomTreeUI ui;
    private CreateFolderHandler createFolderHandler;

    @FunctionalInterface
    public interface CreateFolderHandler {
        FolderTreeNode createFolder(TreepeaterNode parent);
    }

    public void setCreateFolderHandler(CreateFolderHandler handler) {
        this.createFolderHandler = handler;
    }

    public RequestTree() {
        this.setDragEnabled(true);
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
        this.ui = new CustomTreeUI();
        this.setUI(ui);
        this.root = new FolderTreeNode(0, StatusRegistry.getDefault(), "Treepeater");
        this.model = new DefaultTreeModel(this.root);
        this.setTransferHandler(new TreeTransferHandler(this.model));
        this.expandRow(0);
        this.setRootVisible(false);
        this.setShowsRootHandles(true);
        this.setModel(model);
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
        newFolder.addActionListener(ev -> {
            if (this.createFolderHandler != null) {
                FolderTreeNode folder = this.createFolderHandler.createFolder(target);
                if (folder != null) {
                    TreePath parentPath = new TreePath(((TreepeaterNode) folder.getParent()).getPath());
                    this.expandPath(parentPath);
                    this.startProgrammaticEditForNode(folder, ProgrammaticEdit.RENAME);
                }
            }
        });
        menu.add(newFolder);
        menu.show(this, e.getX(), e.getY());
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

    public void insertRootNode(TreepeaterNode node) {
        this.model.insertNodeInto(node, this.root, this.root.getChildCount());
        this.model.nodeStructureChanged(this.root);
    }

    public void insertNodeInto(TreepeaterNode child, TreepeaterNode parent, int index) {
        this.model.insertNodeInto(child, parent, index);
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
