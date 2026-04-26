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
import treepeater.ai.AgentChatWorkspace;
import treepeater.requestResponse.RequestHistory;

import treepeater.tree.RequestTree;
import treepeater.tree.RequestTreeNode;
import treepeater.tree.RequestTreeNodeListener;

public class TreepeaterModel implements RequestTreeNodeListener {
    private RequestTree tree;
    private LinkedList<RequestTreeNode> tabs;
    private RequestTreeNode activeNode;
    private int requestCount;

    private String globalNotes = "";
    private AgentChatWorkspace globalAgentChatWorkspace = AgentChatWorkspace.EMPTY;

    private Set<TreepeaterModelListener> listeners;

    public TreepeaterModel(RequestTree tree, LinkedList<RequestTreeNode> tabs, RequestTreeNode activeNode, int requestCount) {
        this(tree, tabs, activeNode, requestCount, "", AgentChatWorkspace.EMPTY);
    }

    public TreepeaterModel(
            RequestTree tree,
            LinkedList<RequestTreeNode> tabs,
            RequestTreeNode activeNode,
            int requestCount,
            String globalNotes,
            AgentChatWorkspace globalAgentChatWorkspace) {
        this.tree = tree;
        this.tabs = tabs;
        this.activeNode = activeNode;
        this.requestCount = requestCount;
        this.globalNotes = globalNotes != null ? globalNotes : "";
        this.globalAgentChatWorkspace =
                globalAgentChatWorkspace != null ? globalAgentChatWorkspace : AgentChatWorkspace.EMPTY;
        this.listeners = new HashSet<>();

        this.listenToAllNodes((RequestTreeNode) this.tree.getTreeModel().getRoot());
    }

    public TreepeaterModel() {
        this.tree = new RequestTree();
        this.tabs = new LinkedList<>();
        this.requestCount = 0;
        this.activeNode = null;
        this.globalNotes = "";
        this.globalAgentChatWorkspace = AgentChatWorkspace.EMPTY;
        this.listeners = new HashSet<>();
    }

    public String getGlobalNotes() {
        return this.globalNotes != null ? this.globalNotes : "";
    }

    public void setGlobalNotes(String notes) {
        String next = notes != null ? notes : "";
        if (next.equals(this.globalNotes)) {
            return;
        }
        this.globalNotes = next;
        Treepeater.saveState();
    }

    public AgentChatWorkspace getGlobalAgentChatWorkspace() {
        return this.globalAgentChatWorkspace != null ? this.globalAgentChatWorkspace : AgentChatWorkspace.EMPTY;
    }

    public void setGlobalAgentChatWorkspace(AgentChatWorkspace workspace) {
        AgentChatWorkspace next = workspace != null ? workspace : AgentChatWorkspace.EMPTY;
        if (next.equals(this.globalAgentChatWorkspace)) {
            return;
        }
        this.globalAgentChatWorkspace = next;
        Treepeater.saveState();
    }

    private void listenToAllNodes(RequestTreeNode node) {
        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            RequestTreeNode child = (RequestTreeNode) node.getChildAt(i);
            child.addListener(this);
            this.listenToAllNodes(child);
        }
        node.addListener(this);
    }

    public void addTab(RequestTreeNode node) {
        this.activeNode = node;

        if (this.tabs.contains(node)) {
            this.listeners.forEach(l -> l.onOpenTab(node));
        } else {
            this.tabs.add(node);
            this.listeners.forEach(l -> l.onNewTab(node));
        }

        Treepeater.saveState();
    }

    public void removeNodeFromTree(RequestTreeNode node) {
        if (node == null) {
            return;
        }
        Object rootObj = this.tree.getTreeModel().getRoot();
        if (!(rootObj instanceof RequestTreeNode root)) {
            return;
        }
        if (node == root || node.getParent() == null) {
            return;
        }
        List<RequestTreeNode> subtree = new ArrayList<>();
        collectSubtreeNodes(node, subtree);
        Set<RequestTreeNode> removed = new HashSet<>(subtree);
        for (int i = this.tabs.size() - 1; i >= 0; i--) {
            if (removed.contains(this.tabs.get(i))) {
                this.removeTab(i);
            }
        }
        for (RequestTreeNode n : subtree) {
            n.removeListener(this);
        }
        this.tree.getTreeModel().removeNodeFromParent(node);
        Treepeater.saveState();
    }

    private static void collectSubtreeNodes(RequestTreeNode n, List<RequestTreeNode> out) {
        out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) {
            collectSubtreeNodes((RequestTreeNode) n.getChildAt(i), out);
        }
    }

    public void removeTab(int idx) {
        RequestTreeNode node = this.tabs.get(idx);

        // Identify if the active node is the one being closed
        if (idx == this.tabs.indexOf(this.activeNode)) {
            // If it is, set the active node to the next one
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

    public void insertNodeInto(RequestTreeNode child, RequestTreeNode parent, int index) {
        this.tree.insertNodeInto(child, parent, index);
        Treepeater.saveState();
    }

    /**
     * Inserts a new node immediately after {@code source} under the same parent, using the given
     * request/response (typically the editor snapshot). Returns the new node, or {@code null} if
     * {@code source} has no parent (e.g. implicit root).
     */
    public RequestTreeNode copyAsSiblingUnderSameParent(RequestTreeNode source, HttpRequest request, HttpResponse response) {
        TreeNode parentRaw = source.getParent();
        if (!(parentRaw instanceof RequestTreeNode parent)) {
            return null;
        }

        this.requestCount += 1;

        String baseName = source.getName();
        String copyName = baseName.endsWith(" (copy)") ? baseName : baseName + " (copy)";

        RequestHistory history = new RequestHistory();
        history.addEntry(request.httpService().host(), request, response);

        RequestTreeNode copy =
                new RequestTreeNode(
                        this.requestCount,
                        source.getStatus(),
                        copyName,
                        request,
                        response,
                        history);
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
    public void onSelect(RequestTreeNode node) {
        this.addTab(node);
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
    public void onDelete(RequestTreeNode node) {
        this.removeNodeFromTree(node);
    }
}