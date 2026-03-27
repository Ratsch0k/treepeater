import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;

import burp.api.montoya.http.message.HttpRequestResponse;

public class TreepeaterModel implements RequestTreeNodeListener {
    private RequestTree tree;
    private LinkedList<RequestTreeNode> tabs;
    private RequestTreeNode activeNode;
    private int requestCount;

    private Set<TreepeaterModelListener> listeners;

    public TreepeaterModel() {
        this.tree = new RequestTree();
        this.tabs = new LinkedList<>();
        this.requestCount = 0;
        this.activeNode = null;
        this.listeners = new HashSet<>();

        //this.tree.addMouseListener(new MouseAdapter() {
        //    @Override
        //    public void mouseClicked(MouseEvent e) {
        //        Treepeater.api.logging().logToOutput("Clicked on tree");
        //        if (e.getClickCount() == 2) {
        //            Treepeater.api.logging().logToOutput("Double clicked on tree");
        //            TreePath path = tree.getPathForLocation(e.getX(), e.getY());
        //            Treepeater.api.logging().logToOutput("Path is " + path);
        //            if (path != null) {
        //                RequestTreeNode node = (RequestTreeNode) path.getLastPathComponent();
        //                TreepeaterModel.this.activeNode = Optional.of(node);
        //                listeners.forEach(l -> l.onOpen(node));
        //            }
        //        }
        //    }
        //});
    }

    public void openTab(RequestTreeNode node) {

    }

    public void closeTab(int idx) {

    }

    public void insertNode(HttpRequestResponse requestResponse) {
        this.requestCount += 1;

        RequestTreeNode node = new RequestTreeNode(String.valueOf(this.requestCount), requestResponse.request(), requestResponse.response());

        node.addListener(this);

        this.tree.insertRootNode(node);
    }

    public void insertNodeInto(RequestTreeNode child, RequestTreeNode parent, int index) {
        Treepeater.api.logging().logToOutput("[TreepeaterModel]: insertNodeInto(" + child + ", " + parent + ", " + index + ")");
        this.tree.insertNodeInto(child, parent, index);
    }

    public RequestTree getTree() {
        return this.tree;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public void addListener(TreepeaterModelListener l) {
        this.listeners.add(l);
    }

    public void removeListener(TreepeaterModelListener l) {
        this.listeners.remove(l);
    }

    @Override
    public void onSelect(RequestTreeNode node) {
        Treepeater.api.logging().logToOutput("[TreepeaterModel] onSelect: " + node);
        this.activeNode = node;
        this.tabs.add(node);
        this.listeners.forEach(l -> l.onOpen(this.activeNode));
    }

    @Override
    public void onNameChange(String newName) {
        }
}