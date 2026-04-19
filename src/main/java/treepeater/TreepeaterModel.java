package treepeater;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.swing.tree.TreeNode;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import treepeater.requestResponse.RequestHistory;
import treepeater.settings.StatusRegistry;

import treepeater.tree.FolderTreeNode;
import treepeater.tree.RequestTree;
import treepeater.tree.RequestTreeNode;
import treepeater.tree.TreepeaterNode;
import treepeater.tree.TreepeaterNodeListener;

public class TreepeaterModel implements TreepeaterNodeListener {
    private RequestTree tree;
    private LinkedList<RequestTreeNode> tabs;
    private RequestTreeNode activeNode;
    private int requestCount;

    private Set<TreepeaterModelListener> listeners;

    public TreepeaterModel(RequestTree tree, LinkedList<RequestTreeNode> tabs, RequestTreeNode activeNode, int requestCount) {
        this.tree = tree;
        this.tabs = tabs;
        this.activeNode = activeNode;
        this.requestCount = requestCount;
        this.listeners = new HashSet<>();
        
        this.listenToAllNodes((TreepeaterNode) this.tree.getTreeModel().getRoot());
    }

    public TreepeaterModel() {
        this.tree = new RequestTree();
        this.tabs = new LinkedList<>();
        this.requestCount = 0;
        this.activeNode = null;
        this.listeners = new HashSet<>();
    }

    private void listenToAllNodes(TreepeaterNode node) {
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            TreepeaterNode child = (TreepeaterNode) node.getChildAt(i);
            child.addListener(this);
            this.listenToAllNodes(child);
        }
        node.addListener(this);
    }

    public void addTab(RequestTreeNode node) {
        this.activeNode = node;

        int idxSameId = -1;
        for (int i = 0; i < this.tabs.size(); i++) {
            if (this.tabs.get(i).getId() == node.getId()) {
                idxSameId = i;
                break;
            }
        }
        if (idxSameId >= 0) {
            RequestTreeNode existing = this.tabs.get(idxSameId);
            if (existing != node) {
                this.tabs.set(idxSameId, node);
                this.listeners.forEach(l -> l.onTabNodeReplaced(existing, node));
            }
            this.listeners.forEach(l -> l.onOpenTab(node));
        } else if (this.tabs.contains(node)) {
            this.listeners.forEach(l -> l.onOpenTab(node));
        } else {
            this.tabs.add(node);
            this.listeners.forEach(l -> l.onNewTab(node));
        }

        Treepeater.saveState();
    }

    public void removeNodeFromTree(TreepeaterNode node) {
        if (node == null) {
            return;
        }
        Object rootObj = this.tree.getTreeModel().getRoot();
        if (!(rootObj instanceof TreepeaterNode root)) {
            return;
        }
        if (node == root || node.getParent() == null) {
            return;
        }
        List<TreepeaterNode> subtree = new ArrayList<>();
        collectSubtreeNodes(node, subtree);
        Set<TreepeaterNode> removed = new HashSet<>(subtree);
        for (int i = this.tabs.size() - 1; i >= 0; i--) {
            if (removed.contains(this.tabs.get(i))) {
                this.removeTab(i);
            }
        }
        for (TreepeaterNode n : subtree) {
            n.removeListener(this);
        }
        this.tree.getTreeModel().removeNodeFromParent(node);
        Treepeater.saveState();
    }

    private static void collectSubtreeNodes(TreepeaterNode n, List<TreepeaterNode> out) {
        out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) {
            collectSubtreeNodes((TreepeaterNode) n.getChildAt(i), out);
        }
    }

    public void removeTab(int idx) {
        RequestTreeNode node = this.tabs.get(idx);

        if (idx == this.tabs.indexOf(this.activeNode)) {
            if (idx < this.tabs.size() - 1) {
                this.activeNode = this.tabs.get(idx + 1);
            } else if (idx > 0) {
                this.activeNode = this.tabs.get(idx - 1);
            } else {
                this.activeNode = null;
            }
        }

        this.tabs.remove(idx);
        this.listeners.forEach(l -> l.onCloseTab(node));
        Treepeater.saveState();
    }

    public void insertNode(HttpRequestResponse requestResponse) {
        this.requestCount += 1;

        RequestTreeNode node = new RequestTreeNode(this.requestCount, String.valueOf(this.requestCount), requestResponse.request(), requestResponse.response());

        node.addListener(this);

        this.tree.insertRootNode(node);
        Treepeater.saveState();
    }

    public void insertNodeInto(TreepeaterNode child, TreepeaterNode parent, int index) {
        this.tree.insertNodeInto(child, parent, index);
        Treepeater.saveState();
    }

    public FolderTreeNode createFolder(TreepeaterNode parent) {
        this.requestCount += 1;
        FolderTreeNode folder = new FolderTreeNode(this.requestCount, StatusRegistry.getDefault(), "New Folder");
        folder.addListener(this);

        TreepeaterNode target = parent;
        if (target == null) {
            target = (TreepeaterNode) this.tree.getTreeModel().getRoot();
        }
        if (!(target instanceof FolderTreeNode)) {
            target = (TreepeaterNode) target.getParent();
            if (target == null) {
                target = (TreepeaterNode) this.tree.getTreeModel().getRoot();
            }
        }
        this.tree.insertNodeInto(folder, target, target.getChildCount());
        Treepeater.saveState();
        return folder;
    }

    /**
     * Inserts a new node immediately after {@code source} under the same parent, using the given
     * request/response (typically the editor snapshot). Returns the new node, or {@code null} if
     * {@code source} has no parent (e.g. implicit root).
     */
    public RequestTreeNode copyAsSiblingUnderSameParent(RequestTreeNode source, HttpRequest request, HttpResponse response) {
        TreeNode parentRaw = source.getParent();
        if (!(parentRaw instanceof TreepeaterNode parent)) {
            return null;
        }

        this.requestCount += 1;

        String baseName = source.getName();
        String copyName = baseName.endsWith(" (copy)") ? baseName : baseName + " (copy)";

        RequestHistory history = new RequestHistory();
        history.addEntry(request.httpService().host(), request, response);

        RequestTreeNode copy = new RequestTreeNode(this.requestCount, source.getStatus(), copyName, request, response, history, source.getNotes());
        copy.addListener(this);

        int insertIndex = parent.getIndex(source) + 1;
        this.tree.insertNodeInto(copy, parent, insertIndex);
        Treepeater.saveState();
        return copy;
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
    public void onSelect(TreepeaterNode node) {
        if (node instanceof RequestTreeNode requestNode) {
            this.addTab(requestNode);
        }
    }

    @Override
    public void onNameChange(String newName) {
    }

    public RequestTreeNode getActiveNode() {
        return this.activeNode;
    }

    public List<RequestTreeNode> getTabs() {
        return this.tabs;
    }

    @Override
    public void onDelete(TreepeaterNode node) {
        this.removeNodeFromTree(node);
    }
}
