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
import treepeater.tree.RequestTree;
import treepeater.tree.RequestTreeNode;


public class TreeTransferHandler extends TransferHandler {
    DataFlavor nodesFlavor;
    DataFlavor[] flavors = new DataFlavor[1];
    RequestTreeNode[] nodesToRemove;

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
        // Do not allow a drop on the drag source selections.
        JTree.DropLocation dl =
                (JTree.DropLocation)support.getDropLocation();
        JTree tree = (JTree)support.getComponent();
        int dropRow = tree.getRowForPath(dl.getPath());
        int[] selRows = tree.getSelectionRows();
        for(int i = 0; i < selRows.length; i++) {
            if(selRows[i] == dropRow) {
                return false;
            }
            RequestTreeNode treeNode =
                    (RequestTreeNode)tree.getPathForRow(selRows[i]).getLastPathComponent();
            for (TreeNode offspring: Collections.list(treeNode.depthFirstEnumeration())) {
                if (tree.getRowForPath(new TreePath(((RequestTreeNode)offspring).getPath())) == dropRow) {
                    return false;
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
        // Make up a node array of copies for transfer and
        // another for/of the nodes that will be removed in
        // exportDone after a successful drop.
        List<RequestTreeNode> copies =
                new ArrayList<RequestTreeNode>();
        List<RequestTreeNode> toRemove =
                new ArrayList<RequestTreeNode>();
        RequestTreeNode firstNode =
                (RequestTreeNode) paths[0].getLastPathComponent();
        HashSet<TreeNode> doneItems = new LinkedHashSet<>(paths.length);
        RequestTreeNode copy = copy(firstNode, doneItems, tree);
        copies.add(copy);
        toRemove.add(firstNode);
        for (int i = 1; i < paths.length; i++) {
            RequestTreeNode next =
                    (RequestTreeNode) paths[i].getLastPathComponent();
            if (doneItems.contains(next)) {
                continue;
            }
            // Do not allow higher level nodes to be added to list.
            if (next.getLevel() < firstNode.getLevel()) {
                break;
            } else if (next.getLevel() > firstNode.getLevel()) {  // child node
                copy.add(copy(next, doneItems, tree));
                // node already contains child
            } else {                                        // sibling
                copies.add(copy(next, doneItems, tree));
                toRemove.add(next);
            }
            doneItems.add(next);
        }
        RequestTreeNode[] nodes =
                copies.toArray(new RequestTreeNode[copies.size()]);
        nodesToRemove =
                toRemove.toArray(new RequestTreeNode[toRemove.size()]);
        return new NodesTransferable(nodes);
    }

    private RequestTreeNode copy(RequestTreeNode node, HashSet<TreeNode> doneItems, JTree tree) {
        RequestTreeNode copy = new RequestTreeNode(node);
        doneItems.add(node);
        for (int i=0; i<node.getChildCount(); i++) {
            copy.add(copy((RequestTreeNode)((TreeNode)node).getChildAt(i), doneItems, tree));
        }
        int row = tree.getRowForPath(new TreePath(copy.getPath()));
        tree.expandRow(row);
        return copy;
    }

    protected void exportDone(JComponent source, Transferable data, int action) {
        if((action & MOVE) == MOVE) {
            JTree tree = (JTree)source;
            DefaultTreeModel model = (DefaultTreeModel)tree.getModel();
            // Remove nodes saved in nodesToRemove in createTransferable.
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


        // Extract transfer data.
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

        // Get drop location info.
        JTree.DropLocation dl =
                (JTree.DropLocation)support.getDropLocation();
        int childIndex = dl.getChildIndex();
        TreePath dest = dl.getPath();
        RequestTreeNode parent =
                (RequestTreeNode)dest.getLastPathComponent();
        RequestTree tree = (RequestTree)support.getComponent();
        DefaultTreeModel model = (DefaultTreeModel)tree.getModel();
        // Configure for drop mode.
        int index = childIndex;    // DropMode.INSERT
        if(childIndex == -1) {     // DropMode.ON
            index = parent.getChildCount();
        }
        // Add data to model.
        for(int i = 0; i < nodes.length; i++) {
            RequestTreeNodeTransferable transferable = nodes[i];
            RequestTreeNode node =
                    new RequestTreeNode(
                            transferable.id,
                            transferable.status,
                            transferable.name,
                            transferable.request,
                            transferable.response,
                            transferable.listener);

            model.insertNodeInto(node, parent, index++);
        }
        return true;
    }

    public String toString() {
        return getClass().getName();
    }

    public class NodesTransferable implements Transferable {
        RequestTreeNode[] nodes;

        public NodesTransferable(RequestTreeNode[] nodes) {
            this.nodes = nodes;
        }

        public Object getTransferData(DataFlavor flavor)
                throws UnsupportedFlavorException {
                
            if(!isDataFlavorSupported(flavor))
                throw new UnsupportedFlavorException(flavor);

            ArrayList<RequestTreeNodeTransferable> transferables = new ArrayList<>();
            
            for (RequestTreeNode treeNode : nodes) {
                transferables.add(
                        new RequestTreeNodeTransferable(
                                treeNode.getId(),
                                treeNode.getStatus(),
                                treeNode.getName(),
                                treeNode.getRequest(),
                                treeNode.getResponse(),
                                treeNode.getListeners()));
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

