package treepeater.draggable;

/**
 * Originally derived from https://coderanch.com/t/346509/java/JTree-drag-drop-tree-Java by Craig Wood
 * (referenced in https://stackoverflow.com/questions/4588109/drag-and-drop-nodes-in-jtree), but the
 * transfer mechanism has since been replaced with a direct in-JVM node-reference handoff so MOVE drops
 * preserve the original {@link TreepeaterNode} instance.
 */

import java.awt.datatransfer.*;
import java.util.*;

import javax.swing.*;
import javax.swing.tree.*;

import treepeater.Treepeater;
import treepeater.tree.FolderTreeNode;
import treepeater.tree.RequestTreeNode;
import treepeater.tree.TreepeaterNode;


public class TreeTransferHandler extends TransferHandler {
    private final DataFlavor nodesFlavor;
    private final DataFlavor[] flavors = new DataFlavor[1];
    private final DefaultTreeModel model;

    public TreeTransferHandler(DefaultTreeModel model) {
        this.model = model;
        try {
            String mimeType = DataFlavor.javaJVMLocalObjectMimeType
                    + ";class=\"" + TreepeaterNode[].class.getName() + "\"";
            // Burp loads extensions with their own classloader; the default DataFlavor constructor
            // resolves the class name via the system/context classloader, which can't see extension
            // classes. Pass our loader explicitly.
            nodesFlavor = new DataFlavor(mimeType, "TreepeaterNodes", TreepeaterNode.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to register DnD flavor for TreepeaterNode[]", e);
        }
        flavors[0] = nodesFlavor;
    }

    public boolean canImport(TransferHandler.TransferSupport support) {
        if (!support.isDrop()) {
            return false;
        }
        support.setShowDropLocation(true);
        if (!support.isDataFlavorSupported(nodesFlavor)) {
            return false;
        }

        JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
        JTree tree = (JTree) support.getComponent();

        TreePath destPath = dl.getPath();
        if (destPath == null) {
            return false;
        }
        Object destComponent = destPath.getLastPathComponent();

        if (!(destComponent instanceof FolderTreeNode)) {
            return false;
        }

        int dropRow = tree.getRowForPath(dl.getPath());
        int[] selRows = tree.getSelectionRows();
        if (selRows == null) {
            return false;
        }
        for (int i = 0; i < selRows.length; i++) {
            if (selRows[i] == dropRow) {
                return false;
            }
            TreepeaterNode treeNode =
                    (TreepeaterNode) tree.getPathForRow(selRows[i]).getLastPathComponent();
            for (TreeNode offspring : Collections.list(treeNode.depthFirstEnumeration())) {
                if (offspring instanceof TreepeaterNode offspringNode) {
                    if (tree.getRowForPath(new TreePath(offspringNode.getPath())) == dropRow) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Collects the top-level selected nodes (dropping any node whose ancestor is also selected so a subtree
     * isn't moved twice) and wraps their original references in the transferable. No deep copies are made
     * here; {@link #importData(TransferSupport)} decides whether to reuse the originals (MOVE) or deep-copy
     * them (COPY) based on the drop action.
     */
    protected Transferable createTransferable(JComponent c) {
        JTree tree = (JTree) c;
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null || paths.length == 0) {
            return null;
        }

        Set<TreepeaterNode> selected = new LinkedHashSet<>();
        for (TreePath path : paths) {
            Object last = path.getLastPathComponent();
            if (last instanceof TreepeaterNode node) {
                selected.add(node);
            }
        }

        List<TreepeaterNode> topLevel = new ArrayList<>();
        for (TreepeaterNode node : selected) {
            if (!hasSelectedAncestor(node, selected)) {
                topLevel.add(node);
            }
        }

        if (topLevel.isEmpty()) {
            return null;
        }

        return new NodesTransferable(topLevel.toArray(new TreepeaterNode[0]));
    }

    private static boolean hasSelectedAncestor(TreepeaterNode node, Set<TreepeaterNode> selected) {
        TreeNode ancestor = node.getParent();
        while (ancestor != null) {
            if (ancestor instanceof TreepeaterNode tp && selected.contains(tp)) {
                return true;
            }
            ancestor = ancestor.getParent();
        }
        return false;
    }

    private TreepeaterNode deepCopy(TreepeaterNode node) {
        TreepeaterNode copy;
        if (node instanceof FolderTreeNode folderNode) {
            copy = new FolderTreeNode(folderNode);
        } else {
            copy = new RequestTreeNode((RequestTreeNode) node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            copy.add(deepCopy((TreepeaterNode) node.getChildAt(i)));
        }
        return copy;
    }

    protected void exportDone(JComponent source, Transferable data, int action) {
        // Intentionally empty: MOVE detaches the source in importData via removeNodeFromParent, and COPY
        // never removes the source. This override exists solely to document that.
    }

    public int getSourceActions(JComponent c) {
        return COPY_OR_MOVE;
    }

    public boolean importData(TransferHandler.TransferSupport support) {
        if (!canImport(support)) {
            return false;
        }

        TreepeaterNode[] nodes;
        try {
            Transferable t = support.getTransferable();
            nodes = (TreepeaterNode[]) t.getTransferData(nodesFlavor);
        } catch (UnsupportedFlavorException ufe) {
            Treepeater.api.logging().logToError("UnsupportedFlavor: " + ufe);
            return false;
        } catch (java.io.IOException ioe) {
            Treepeater.api.logging().logToError("I/O error: " + ioe);
            return false;
        } catch (Exception e) {
            Treepeater.api.logging().logToError(e);
            return false;
        }

        if (nodes == null || nodes.length == 0) {
            return false;
        }

        JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
        int childIndex = dl.getChildIndex();
        TreePath dest = dl.getPath();
        TreepeaterNode parent = (TreepeaterNode) dest.getLastPathComponent();
        int insertIndex = childIndex == -1 ? parent.getChildCount() : childIndex;

        boolean isMove = (support.getDropAction() & MOVE) == MOVE;

        for (TreepeaterNode node : nodes) {
            TreepeaterNode effective;
            if (isMove) {
                TreepeaterNode oldParent = (TreepeaterNode) node.getParent();
                int oldIndex = oldParent != null ? oldParent.getIndex(node) : -1;
                if (oldParent != null) {
                    this.model.removeNodeFromParent(node);
                    if (oldParent == parent && oldIndex < insertIndex) {
                        insertIndex--;
                    }
                }
                effective = node;
            } else {
                effective = deepCopy(node);
            }
            this.model.insertNodeInto(effective, parent, insertIndex);
            insertIndex++;
        }
        return true;
    }

    public String toString() {
        return getClass().getName();
    }

    /**
     * Carries direct JVM references to the selected nodes. Only meaningful within the current JVM —
     * cross-process DnD has never been supported.
     */
    public class NodesTransferable implements Transferable {
        private final TreepeaterNode[] nodes;

        public NodesTransferable(TreepeaterNode[] nodes) {
            this.nodes = nodes;
        }

        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return this.nodes;
        }

        public DataFlavor[] getTransferDataFlavors() {
            return flavors;
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return nodesFlavor.equals(flavor);
        }
    }
}
