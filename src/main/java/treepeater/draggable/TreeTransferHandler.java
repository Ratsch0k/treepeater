package treepeater.draggable;

/**
 * Edited from https://coderanch.com/t/346509/java/JTree-drag-drop-tree-Java
 * by Craig Wood and mentioned on
 * https://stackoverflow.com/questions/4588109/drag-and-drop-nodes-in-jtree
 */

import java.awt.datatransfer.*;
import java.util.*;

import javax.swing.*;
import javax.swing.tree.*;

import treepeater.Treepeater;
import treepeater.tree.FolderTreeNode;
import treepeater.tree.RequestTree;
import treepeater.tree.RequestTreeNode;
import treepeater.tree.TreepeaterNode;


public class TreeTransferHandler extends TransferHandler {
    DataFlavor nodesFlavor;
    DataFlavor[] flavors = new DataFlavor[1];
    TreepeaterNode[] nodesToRemove;

    public TreeTransferHandler() {
        nodesFlavor = new DataFlavor(RequestTreeNodeTransferable.class, "RequestTreeNodeTransferable");
        flavors[0] = nodesFlavor;
    }

    public boolean canImport(TransferHandler.TransferSupport support) {
        if(!support.isDrop()) {
            return false;
        }
        support.setShowDropLocation(true);
        if(!support.isDataFlavorSupported(nodesFlavor)) {
            return false;
        }

        JTree.DropLocation dl =
                (JTree.DropLocation)support.getDropLocation();
        JTree tree = (JTree)support.getComponent();

        TreePath destPath = dl.getPath();
        if (destPath == null) {
            return false;
        }
        Object destComponent = destPath.getLastPathComponent();

        // Only allow drops onto FolderTreeNode (or the root, which is also a folder)
        if (!(destComponent instanceof FolderTreeNode)) {
            return false;
        }

        int dropRow = tree.getRowForPath(dl.getPath());
        int[] selRows = tree.getSelectionRows();
        if (selRows == null) {
            return false;
        }
        for(int i = 0; i < selRows.length; i++) {
            if(selRows[i] == dropRow) {
                return false;
            }
            TreepeaterNode treeNode =
                    (TreepeaterNode)tree.getPathForRow(selRows[i]).getLastPathComponent();
            for (TreeNode offspring: Collections.list(treeNode.depthFirstEnumeration())) {
                if (offspring instanceof TreepeaterNode offspringNode) {
                    if (tree.getRowForPath(new TreePath(offspringNode.getPath())) == dropRow) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    protected Transferable createTransferable(JComponent c) {
        JTree tree = (JTree) c;
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null) {
            return null;
        }
        List<TreepeaterNode> copies = new ArrayList<>();
        List<TreepeaterNode> toRemove = new ArrayList<>();
        TreepeaterNode firstNode =
                (TreepeaterNode) paths[0].getLastPathComponent();
        HashSet<TreeNode> doneItems = new LinkedHashSet<>(paths.length);
        TreepeaterNode copy = copy(firstNode, doneItems, tree);
        copies.add(copy);
        toRemove.add(firstNode);
        for (int i = 1; i < paths.length; i++) {
            TreepeaterNode next =
                    (TreepeaterNode) paths[i].getLastPathComponent();
            if (doneItems.contains(next)) {
                continue;
            }
            if (next.getLevel() < firstNode.getLevel()) {
                break;
            } else if (next.getLevel() > firstNode.getLevel()) {
                copy.add(copy(next, doneItems, tree));
            } else {
                copies.add(copy(next, doneItems, tree));
                toRemove.add(next);
            }
            doneItems.add(next);
        }
        TreepeaterNode[] nodes =
                copies.toArray(new TreepeaterNode[copies.size()]);
        nodesToRemove =
                toRemove.toArray(new TreepeaterNode[toRemove.size()]);
        return new NodesTransferable(nodes);
    }

    private TreepeaterNode copy(TreepeaterNode node, HashSet<TreeNode> doneItems, JTree tree) {
        TreepeaterNode copy;
        if (node instanceof FolderTreeNode folderNode) {
            copy = new FolderTreeNode(folderNode);
        } else {
            copy = new RequestTreeNode((RequestTreeNode) node);
        }
        doneItems.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            copy.add(copy((TreepeaterNode) node.getChildAt(i), doneItems, tree));
        }
        int row = tree.getRowForPath(new TreePath(copy.getPath()));
        tree.expandRow(row);
        return copy;
    }

    protected void exportDone(JComponent source, Transferable data, int action) {
        if((action & MOVE) == MOVE) {
            JTree tree = (JTree)source;
            DefaultTreeModel model = (DefaultTreeModel)tree.getModel();
            for(int i = 0; i < nodesToRemove.length; i++) {
                model.removeNodeFromParent(nodesToRemove[i]);
            }
        }
    }

    public int getSourceActions(JComponent c) {
        return COPY_OR_MOVE;
    }

    public boolean importData(TransferHandler.TransferSupport support) {
        if(!canImport(support)) {
            return false;
        }

        RequestTreeNodeTransferable[] nodes = null;
        try {
            Transferable t = support.getTransferable();
            nodes = (RequestTreeNodeTransferable[])t.getTransferData(nodesFlavor);
            Treepeater.api.logging().logToOutput("ImportData -> nodes: " + nodes);
        } catch(UnsupportedFlavorException ufe) {
            Treepeater.api.logging().logToError("UnsupportedFlavor: " + ufe);
        } catch(java.io.IOException ioe) {
            Treepeater.api.logging().logToError("I/O error: " + ioe);
        } catch (Exception e) {
            Treepeater.api.logging().logToError(e);
        }

        JTree.DropLocation dl =
                (JTree.DropLocation)support.getDropLocation();
        int childIndex = dl.getChildIndex();
        TreePath dest = dl.getPath();
        TreepeaterNode parent =
                (TreepeaterNode)dest.getLastPathComponent();
        RequestTree tree = (RequestTree)support.getComponent();
        DefaultTreeModel model = (DefaultTreeModel)tree.getModel();
        int index = childIndex;
        if(childIndex == -1) {
            index = parent.getChildCount();
        }
        for(int i = 0; i < nodes.length; i++) {
            RequestTreeNodeTransferable transferable = nodes[i];
            TreepeaterNode node;
            if (transferable.isFolder) {
                node = new FolderTreeNode(transferable.id, transferable.status, transferable.name);
            } else {
                node = new RequestTreeNode(transferable.id, transferable.status, transferable.name, transferable.request, transferable.response, transferable.history, transferable.listener, transferable.notes);
            }
            model.insertNodeInto(node, parent, index++);
        }
        return true;
    }

    public String toString() {
        return getClass().getName();
    }

    public class NodesTransferable implements Transferable {
        TreepeaterNode[] nodes;

        public NodesTransferable(TreepeaterNode[] nodes) {
            this.nodes = nodes;
        }

        public Object getTransferData(DataFlavor flavor)
                throws UnsupportedFlavorException {
                
            if(!isDataFlavorSupported(flavor))
                throw new UnsupportedFlavorException(flavor);

            ArrayList<RequestTreeNodeTransferable> transferables = new ArrayList<>();
            
            for (TreepeaterNode treeNode : nodes) {
                boolean isFolder = treeNode instanceof FolderTreeNode;
                if (isFolder) {
                    transferables.add(new RequestTreeNodeTransferable(true, treeNode.getId(), treeNode.getStatus(), treeNode.getName(), null, null, null, treeNode.getListeners(), null));
                } else {
                    RequestTreeNode reqNode = (RequestTreeNode) treeNode;
                    transferables.add(new RequestTreeNodeTransferable(false, reqNode.getId(), reqNode.getStatus(), reqNode.getName(), reqNode.getRequest(), reqNode.getResponse(), reqNode.getNotes(), reqNode.getListeners(), reqNode.getHistory()));
                }
            }

            RequestTreeNodeTransferable[] transferableArray = transferables.toArray(new RequestTreeNodeTransferable[0]);
            return transferableArray;
        }

        public DataFlavor[] getTransferDataFlavors() {
            return flavors;
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return nodesFlavor.equals(flavor);
        }
    }
}
