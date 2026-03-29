import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.DropMode;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

public class RequestTree extends JTree {
    private DefaultMutableTreeNode root;
    private DefaultTreeModel model;

    public RequestTree() {
            this.setDragEnabled(true);
            this.setDropMode(DropMode.ON_OR_INSERT);
            this.setTransferHandler(new TreeTransferHandler());
            this.getSelectionModel().setSelectionMode(
                    TreeSelectionModel.SINGLE_TREE_SELECTION);
            this.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int x = e.getX();
                    int y = e.getY();

                    TreePath path = RequestTree.this.getPathForLocation(x, y);
                    Treepeater.api.logging().logToOutput("MouseListener: " + path);

                    if (path == null) {
                        return;
                    };

                    Object lastComponent = path.getLastPathComponent();

                    Treepeater.api.logging().logToOutput(lastComponent);
                    if (!(lastComponent instanceof RequestTreeNode)) {
                        return;
                    }

                    RequestTree.this.startEditingAtPath(path);

                    CustomTreeCell cell = (CustomTreeCell) RequestTree.this.getCellEditor().getTreeCellEditorComponent(RequestTree.this, lastComponent, false, false, false, 0);

                    RequestTreeNode node = (RequestTreeNode) lastComponent;

                    Rectangle bounds = RequestTree.this.getPathBounds(path);

                    double relativeX = x - bounds.getX();
                    int buttonWidth = cell.getButtonWidth();

                    boolean isInButton = relativeX <= (buttonWidth + 4);

                    if (isInButton) {
                        SwingUtilities.invokeLater(cell::clickButton);
                        return;
                    }

                    node.select();
                }
            });
            this.setUI(new CustomTreeUI());
            this.root = new RequestTreeNode(0, "Treepeater", null, null);
            this.model = new DefaultTreeModel(this.root);
            //this.setMinimumSize(new Dimension(300, 0));
            this.expandRow(0);
            this.setRootVisible(false);
            this.setShowsRootHandles(true);
            this.setModel(model);
            this.setToggleClickCount(0);
            this.setEditable(true);
            CustomTreeCellRenderer renderer = new CustomTreeCellRenderer();
            this.setCellRenderer(renderer);
            this.setCellEditor(new CustomTreeCellEditor(this, renderer));
    }

    public DefaultTreeModel getTreeModel() {
        return this.model;
    }

    public void insertRootNode(RequestTreeNode node) {
        this.model.insertNodeInto(node, this.root, this.root.getChildCount());
        this.model.nodeStructureChanged(this.root);

    }

    public void insertNodeInto(RequestTreeNode child, RequestTreeNode parent, int index) {
        Treepeater.api.logging().logToOutput("[RequestTree]: insertNodeInto(" + child + ", " + parent + ", " + index + ")");
        this.model.insertNodeInto(child, parent, index);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return this.getParent() instanceof JViewport;
    }

    public List<RequestTreeNodeSimple> toSimpleRepeaterList() {
        ArrayList<RequestTreeNodeSimple> children = new ArrayList<>();
        
        for (int idx = 0; idx < this.root.getChildCount(); idx++) {
            RequestTreeNode child = (RequestTreeNode) this.root.getChildAt(idx);

            String childName = "[TR] " + child.getName();
            RequestTreeNodeSimple simple = new RequestTreeNodeSimple(child.getRequest(), childName);
            children.add(simple);

            children.addAll(this.toSimpleRepeaterList(childName, child));
        }

        return children;
    }

    public List<RequestTreeNodeSimple> toSimpleRepeaterList(String prefix, RequestTreeNode node) {
        ArrayList<RequestTreeNodeSimple> children = new ArrayList<>();
        
        for (int idx = 0; idx < node.getChildCount(); idx++) {
            RequestTreeNode child = (RequestTreeNode) node.getChildAt(idx);

            String childName = prefix + " / " + child.getName();

            children.add(new RequestTreeNodeSimple(child.getRequest(), childName));

            children.addAll(this.toSimpleRepeaterList(childName, child));
        }

        return children;
    }
}
