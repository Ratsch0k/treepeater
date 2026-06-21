package treepeater.workspace;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.InvalidDnDOperationException;

import javax.swing.JComponent;
import javax.swing.TransferHandler;

import treepeater.tree.RequestTreeNode;

public class TabTransferHandler extends TransferHandler {
    private final DataFlavor nodeFlavor;
    private final TabGroupPanel targetGroup;
    private final RequestTreeNode dragNode;

    public TabTransferHandler(TabGroupPanel targetGroup) {
        this(targetGroup, null);
    }

    public TabTransferHandler(TabGroupPanel targetGroup, RequestTreeNode dragNode) {
        this.targetGroup = targetGroup;
        this.dragNode = dragNode;
        try {
            String mimeType =
                    DataFlavor.javaJVMLocalObjectMimeType
                            + ";class=\""
                            + RequestTreeNode.class.getName()
                            + "\"";
            this.nodeFlavor =
                    new DataFlavor(
                            mimeType,
                            "RequestTreeNode",
                            RequestTreeNode.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to register DnD flavor for RequestTreeNode", e);
        }
    }

    @Override
    public int getSourceActions(JComponent c) {
        if (this.dragNode == null) {
            return NONE;
        }
        return MOVE;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        if (this.dragNode == null) {
            return null;
        }
        return new NodeTransferable(this.dragNode, this.nodeFlavor);
    }

    @Override
    public boolean canImport(TransferSupport support) {
        if (!support.isDrop() || !support.isDataFlavorSupported(this.nodeFlavor)) {
            return false;
        }
        // Showing the built-in tab drop indicator changes tab bar size and shifts split panes.
        support.setShowDropLocation(false);
        return true;
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!this.canImport(support)) {
            return false;
        }
        RequestTreeNode node;
        try {
            node = (RequestTreeNode) support.getTransferable().getTransferData(this.nodeFlavor);
        } catch (UnsupportedFlavorException | java.io.IOException e) {
            return false;
        }
        if (node == null) {
            return false;
        }

        int dropIndex = this.targetGroup.getTabCount();
        dropIndex = resolveDropIndex(support, this.targetGroup, dropIndex);

        EditorWorkspace workspace = this.targetGroup.model().getWorkspace();
        TabGroupNode fromGroup = workspace.findGroupContaining(node);
        String fromId = fromGroup != null ? fromGroup.id() : this.targetGroup.groupId();
        this.targetGroup.workspaceInterface().onTabMoved(node, fromId, this.targetGroup.groupId(), dropIndex);
        return true;
    }

    static void installDropTarget(JComponent component, TabGroupPanel targetGroup) {
        component.setTransferHandler(new TabTransferHandler(targetGroup));
    }

    private static int resolveDropIndex(TransferSupport support, TabGroupPanel targetGroup, int defaultIndex) {
        try {
            Object loc = support.getDropLocation();
            if (loc == null) {
                return defaultIndex;
            }
            if ("javax.swing.JTabbedPane$DropLocation".equals(loc.getClass().getName())) {
                Object idx = loc.getClass().getMethod("getIndex").invoke(loc);
                if (idx instanceof Integer i && i >= 0) {
                    return Math.min(i, targetGroup.getTabCount());
                }
            }
        } catch (ReflectiveOperationException | InvalidDnDOperationException ignored) {
        }
        return defaultIndex;
    }

    private static final class NodeTransferable implements Transferable {
        private final RequestTreeNode node;
        private final DataFlavor flavor;

        NodeTransferable(RequestTreeNode node, DataFlavor flavor) {
            this.node = node;
            this.flavor = flavor;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] {this.flavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return this.flavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!this.isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return this.node;
        }
    }
}
