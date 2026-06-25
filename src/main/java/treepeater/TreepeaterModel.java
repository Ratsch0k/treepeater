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
import treepeater.settings.StatusRegistry;
import treepeater.workspace.EditorWorkspace;
import treepeater.workspace.SplitOrientation;
import treepeater.workspace.TabGroupNode;

import treepeater.tree.FolderTreeNode;
import treepeater.tree.RequestTree;
import treepeater.tree.RequestTreeNode;
import treepeater.tree.TreepeaterNode;
import treepeater.tree.TreepeaterNodeListener;

public class TreepeaterModel implements TreepeaterNodeListener {
    private RequestTree tree;
    private EditorWorkspace workspace;
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
        this(
                tree,
                EditorWorkspace.fromLegacyTabs(tabs, activeNode),
                activeNode,
                requestCount,
                globalNotes,
                globalAgentChatWorkspace);
    }

    public TreepeaterModel(
            RequestTree tree,
            EditorWorkspace workspace,
            RequestTreeNode activeNode,
            int requestCount,
            String globalNotes,
            AgentChatWorkspace globalAgentChatWorkspace) {
        this.tree = tree;
        this.workspace = workspace != null ? workspace : new EditorWorkspace();
        this.activeNode = activeNode;
        this.requestCount = requestCount;
        this.globalNotes = globalNotes != null ? globalNotes : "";
        this.globalAgentChatWorkspace =
                globalAgentChatWorkspace != null ? globalAgentChatWorkspace : AgentChatWorkspace.EMPTY;
        this.listeners = new HashSet<>();

        this.listenToAllNodes((TreepeaterNode) this.tree.getTreeModel().getRoot());
    }

    public TreepeaterModel() {
        this.tree = new RequestTree();
        this.workspace = new EditorWorkspace();
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

        if (this.workspace.isOpen(node)) {
            TabGroupNode group = this.workspace.findGroupContaining(node);
            String groupId = group != null ? group.id() : this.workspace.focusedTabGroupId();
            this.workspace.setFocusedTabGroupId(groupId);
            if (group != null) {
                group.setSelectedIndex(group.indexOf(node));
            }
            this.listeners.forEach(l -> l.onOpenTab(node, groupId));
        } else {
            this.workspace.addTabToFocused(node);
            String groupId = this.workspace.focusedTabGroupId();
            this.listeners.forEach(l -> l.onNewTab(node, groupId));
        }

        Treepeater.saveState();
    }

    public void removeTab(RequestTreeNode node) {
        if (node == null) {
            return;
        }

        if (node.equals(this.activeNode)) {
            TabGroupNode group = this.workspace.findGroupContaining(node);
            if (group != null) {
                int idx = group.indexOf(node);
                if (idx >= 0 && idx < group.tabs().size() - 1) {
                    this.activeNode = group.tabs().get(idx + 1);
                } else if (idx > 0) {
                    this.activeNode = group.tabs().get(idx - 1);
                } else {
                    this.activeNode = pickFallbackActiveNode(node);
                }
            } else {
                this.activeNode = pickFallbackActiveNode(node);
            }
        }

        boolean layoutChanged = this.workspace.removeTab(node);
        this.listeners.forEach(l -> l.onCloseTab(node));
        if (layoutChanged) {
            this.listeners.forEach(TreepeaterModelListener::onWorkspaceLayoutChanged);
        }
        Treepeater.saveState();
    }

    private RequestTreeNode pickFallbackActiveNode(RequestTreeNode excluding) {
        for (RequestTreeNode tab : this.workspace.allOpenTabs()) {
            if (!tab.equals(excluding)) {
                return tab;
            }
        }
        return null;
    }

    public void moveTab(RequestTreeNode node, String fromGroupId, String toGroupId, int dropIndex) {
        this.workspace.moveTab(node, fromGroupId, toGroupId, dropIndex);
        this.activeNode = node;
        this.listeners.forEach(l -> l.onTabMoved(node, fromGroupId, toGroupId, dropIndex));
        Treepeater.saveState();
    }

    public void splitTabGroup(String groupId, SplitOrientation orientation) {
        EditorWorkspace.SplitResult result = this.workspace.splitGroup(groupId, orientation);
        if (result == null) {
            return;
        }
        this.listeners.forEach(TreepeaterModelListener::onWorkspaceLayoutChanged);
        this.listeners.forEach(l -> l.onFocusedGroupChanged(this.workspace.focusedTabGroupId()));
        Treepeater.saveState();
    }

    public void setActiveNode(RequestTreeNode node) {
        if (node == null) {
            this.activeNode = null;
            return;
        }
        this.activeNode = node;
        TabGroupNode group = this.workspace.findGroupContaining(node);
        if (group != null) {
            group.setSelectedIndex(group.indexOf(node));
            this.workspace.setFocusedTabGroupId(group.id());
        }
        Treepeater.saveState();
    }

    public void setFocusedTabGroupId(String tabGroupId) {
        this.workspace.setFocusedTabGroupId(tabGroupId);
        this.listeners.forEach(l -> l.onFocusedGroupChanged(tabGroupId));
    }

    public EditorWorkspace getWorkspace() {
        return this.workspace;
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
        for (RequestTreeNode tab : new ArrayList<>(this.workspace.allOpenTabs())) {
            if (removed.contains(tab)) {
                this.removeTab(tab);
            }
        }
        for (TreepeaterNode n : subtree) {
            n.removeListener(this);
        }
        this.tree.getTreeModel().removeNodeFromParent(node);
        Treepeater.saveState();
        notifyTreeChanged();
    }

    private static void collectSubtreeNodes(TreepeaterNode n, List<TreepeaterNode> out) {
        out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) {
            collectSubtreeNodes((TreepeaterNode) n.getChildAt(i), out);
        }
    }

    public void insertNode(HttpRequestResponse requestResponse) {
        this.requestCount += 1;

        RequestTreeNode node = new RequestTreeNode(this.requestCount, String.valueOf(this.requestCount), requestResponse.request(), requestResponse.response());

        node.addListener(this);

        this.tree.insertRootNode(node);
        Treepeater.saveState();
        notifyTreeChanged();
    }

    public void insertNodeInto(TreepeaterNode child, TreepeaterNode parent, int index) {
        if (child != null) {
            child.addListener(this);
            listenToAllNodes(child);
        }
        this.tree.insertNodeInto(child, parent, index);
        Treepeater.saveState();
        notifyTreeChanged();
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
     * Where to insert a copied sibling under the source node's parent.
     */
    public enum SiblingCopyPlacement {
        /** Immediately after the source node (default). */
        AFTER_SOURCE,
        /** First child of the parent. */
        PARENT_TOP,
        /** Last child of the parent. */
        PARENT_BOTTOM
    }

    /**
     * Inserts a new node immediately after {@code source} under the same parent, using the given
     * request/response (typically the editor snapshot). Returns the new node, or {@code null} if
     * {@code source} has no parent (e.g. implicit root).
     */
    public RequestTreeNode copyAsSiblingUnderSameParent(RequestTreeNode source, HttpRequest request, HttpResponse response) {
        return copyAsSiblingUnderSameParent(source, request, response, null, SiblingCopyPlacement.AFTER_SOURCE);
    }

    /**
     * Same as {@link #copyAsSiblingUnderSameParent(RequestTreeNode, HttpRequest, HttpResponse)} but uses
     * {@code nameOrNull} when non-blank; otherwise appends {@code " (copy)"} to the source name.
     */
    public RequestTreeNode copyAsSiblingUnderSameParent(
            RequestTreeNode source, HttpRequest request, HttpResponse response, String nameOrNull) {
        return copyAsSiblingUnderSameParent(source, request, response, nameOrNull, SiblingCopyPlacement.AFTER_SOURCE);
    }

    /**
     * Same as {@link #copyAsSiblingUnderSameParent(RequestTreeNode, HttpRequest, HttpResponse, String)} with
     * control over sibling insertion position under the parent.
     */
    public RequestTreeNode copyAsSiblingUnderSameParent(
            RequestTreeNode source,
            HttpRequest request,
            HttpResponse response,
            String nameOrNull,
            SiblingCopyPlacement placement) {
        TreeNode parentRaw = source.getParent();
        if (!(parentRaw instanceof TreepeaterNode parent)) {
            return null;
        }

        this.requestCount += 1;

        String copyName;
        if (nameOrNull != null && !nameOrNull.trim().isEmpty()) {
            copyName = nameOrNull.trim();
        } else {
            String baseName = source.getName();
            copyName = baseName.endsWith(" (copy)") ? baseName : baseName + " (copy)";
        }

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

        SiblingCopyPlacement where =
                placement != null ? placement : SiblingCopyPlacement.AFTER_SOURCE;
        int insertIndex =
                switch (where) {
                    case AFTER_SOURCE -> parent.getIndex(source) + 1;
                    case PARENT_TOP -> 0;
                    case PARENT_BOTTOM -> parent.getChildCount();
                };
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

    private void notifyTreeChanged() {
        this.listeners.forEach(TreepeaterModelListener::onTreeChanged);
    }

    @Override
    public void onSelect(TreepeaterNode node) {
        if (node instanceof RequestTreeNode requestNode) {
            this.addTab(requestNode);
        }
    }

    @Override
    public void onNameChange(String newName) {
        notifyTreeChanged();
    }

    public RequestTreeNode getActiveNode() {
        return this.activeNode;
    }

    public List<RequestTreeNode> getTabs() {
        return this.workspace.allOpenTabs();
    }

    public List<RequestTreeNode> getAllOpenTabs() {
        return this.workspace.allOpenTabs();
    }

    /**
     * The {@link #getTabs()} list is keyed by object identity. After drag-and-drop, the same logical tab
     * can still be in that list while the tree model holds a <em>new</em> {@link RequestTreeNode} with
     * the same id, and the list entry is a detached (removed) node. This finds the in-tree node for a
     * request id, or null if it does not exist.
     */
    public RequestTreeNode findRequestNodeInTreeById(int id) {
        Object root = this.tree.getTreeModel().getRoot();
        if (!(root instanceof TreepeaterNode r)) {
            return null;
        }
        return findRequestNodeInTreeById(r, id);
    }

    /** In-tree node for {@code id}, or {@code fallback} when the tab list holds a detached node. */
    public RequestTreeNode resolveRequestNode(int id, RequestTreeNode fallback) {
        RequestTreeNode found = findRequestNodeInTreeById(id);
        return found != null ? found : fallback;
    }

    /** All request leaf nodes in the tree, in depth-first order. */
    public List<RequestTreeNode> allRequestNodesInTree() {
        List<RequestTreeNode> out = new ArrayList<>();
        Object root = this.tree.getTreeModel().getRoot();
        if (root instanceof TreepeaterNode r) {
            collectRequestNodesInTree(r, out);
        }
        return out;
    }

    private static void collectRequestNodesInTree(TreepeaterNode node, List<RequestTreeNode> out) {
        if (node instanceof RequestTreeNode rn) {
            out.add(rn);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectRequestNodesInTree((TreepeaterNode) node.getChildAt(i), out);
        }
    }

    private static RequestTreeNode findRequestNodeInTreeById(TreepeaterNode node, int id) {
        if (node instanceof RequestTreeNode rn && rn.getId() == id) {
            return rn;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            TreepeaterNode c = (TreepeaterNode) node.getChildAt(i);
            RequestTreeNode f = findRequestNodeInTreeById(c, id);
            if (f != null) {
                return f;
            }
        }
        return null;
    }

    @Override
    public void onDelete(TreepeaterNode node) {
        this.removeNodeFromTree(node);
    }
}
