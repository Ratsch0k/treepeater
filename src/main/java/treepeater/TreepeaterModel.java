package treepeater;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import treepeater.requestResponse.HistoryEntry;
import treepeater.requestResponse.RequestHistory;
import treepeater.settings.StatusRegistry;
import treepeater.tree.RequestTree;
import treepeater.tree.RequestTreeNode;
import treepeater.tree.RequestTreeNodeListener;

public class TreepeaterModel implements RequestTreeNodeListener {
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
        
        this.listenToAllNodes((RequestTreeNode)this.tree.getTreeModel().getRoot());
    }

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
        Treepeater.api.logging().logToOutput("[TreepeaterModel]: insertNodeInto(" + child + ", " + parent + ", " + index + ")");
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

        RequestTreeNode copy = new RequestTreeNode(this.requestCount, source.getStatus(), copyName, request, response, history);
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
        Treepeater.api.logging().logToOutput("[TreepeaterModel] onSelect: " + node);
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

    /**
     * Logs a JSON-like snapshot of this model to the Burp extension output (or stdout if the API is not set).
     */
    public void debugDump() {
        String dump = toDebugJsonString();
        if (Treepeater.api != null) {
            for (String line : dump.split("\n", -1)) {
                Treepeater.api.logging().logToOutput(line);
            }
        } else {
            System.out.print(dump);
        }
    }

    /**
     * Returns a pretty-printed JSON-like representation of the full model (tree, tabs, active node, counts).
     */
    public String toDebugJsonString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendPair(sb, 1, "requestCount", this.requestCount);
        sb.append(",\n");
        appendPair(sb, 1, "listenerCount", this.listeners.size());
        sb.append(",\n");
        indent(sb, 1);
        sb.append("\"activeNode\": ");
        appendNodeRef(sb, this.activeNode);
        sb.append(",\n");
        indent(sb, 1);
        sb.append("\"tabs\": [\n");
        for (int i = 0; i < this.tabs.size(); i++) {
            indent(sb, 2);
            appendNodeRef(sb, this.tabs.get(i));
            if (i < this.tabs.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        indent(sb, 1);
        sb.append("],\n");
        indent(sb, 1);
        sb.append("\"tree\": ");
        Object root = this.tree.getTreeModel().getRoot();
        if (root instanceof RequestTreeNode) {
            appendRequestTreeNode(sb, (RequestTreeNode) root, 1);
        } else {
            sb.append("null");
        }
        sb.append("\n}\n");
        return sb.toString();
    }

    private static void appendRequestTreeNode(StringBuilder sb, RequestTreeNode node, int depth) {
        indent(sb, depth);
        sb.append("{\n");
        appendPair(sb, depth + 1, "id", node.getId());
        sb.append(",\n");
        appendEscapedPair(sb, depth + 1, "name", node.getName());
        sb.append(",\n");
        appendEscapedPair(sb, depth + 1, "status",
                node.getStatus() != null ? node.getStatus().getStatus() : null);
        sb.append(",\n");
        indent(sb, depth + 1);
        sb.append("\"request\": ");
        appendHttpMessage(sb, node.getRequest());
        sb.append(",\n");
        indent(sb, depth + 1);
        sb.append("\"response\": ");
        appendHttpMessage(sb, node.getResponse());
        sb.append(",\n");
        appendRequestHistory(sb, node.getHistory(), depth + 1);
        sb.append(",\n");
        indent(sb, depth + 1);
        sb.append("\"children\": [\n");
        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            DefaultMutableTreeNode raw = (DefaultMutableTreeNode) node.getChildAt(i);
            if (raw instanceof RequestTreeNode) {
                appendRequestTreeNode(sb, (RequestTreeNode) raw, depth + 2);
            } else {
                indent(sb, depth + 2);
                sb.append("null");
            }
            if (i < n - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        indent(sb, depth + 1);
        sb.append("]\n");
        indent(sb, depth);
        sb.append("}");
    }

    private static void appendRequestHistory(StringBuilder sb, RequestHistory history, int depth) {
        indent(sb, depth);
        sb.append("\"history\": {\n");
        appendPair(sb, depth + 1, "currentIndex", history.getCurrentIndex());
        sb.append(",\n");
        indent(sb, depth + 1);
        sb.append("\"entries\": [\n");
        List<HistoryEntry> entries = history.entries();
        for (int i = 0; i < entries.size(); i++) {
            HistoryEntry e = entries.get(i);
            indent(sb, depth + 2);
            sb.append("{\n");
            appendPair(sb, depth + 3, "index", e.getIndex());
            sb.append(",\n");
            appendEscapedPair(sb, depth + 3, "time", e.getTime() != null ? e.getTime().toString() : null);
            sb.append(",\n");
            appendEscapedPair(sb, depth + 3, "targetLabel", e.getTargetLabel());
            sb.append(",\n");
            indent(sb, depth + 3);
            sb.append("\"request\": ");
            appendHttpMessage(sb, e.getRequest());
            sb.append(",\n");
            indent(sb, depth + 3);
            sb.append("\"response\": ");
            appendHttpMessage(sb, e.getResponse());
            sb.append("\n");
            indent(sb, depth + 2);
            sb.append("}");
            if (i < entries.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        indent(sb, depth + 1);
        sb.append("]\n");
        indent(sb, depth);
        sb.append("}");
    }

    private static void appendNodeRef(StringBuilder sb, RequestTreeNode node) {
        if (node == null) {
            sb.append("null");
            return;
        }
        sb.append("{ \"id\": ").append(node.getId()).append(", \"name\": ");
        appendEscaped(sb, node.getName());
        sb.append(" }");
    }

    private static void appendHttpMessage(StringBuilder sb, Object message) {
        if (message == null) {
            sb.append("null");
            return;
        }
        appendEscaped(sb, message.toString());
    }

    private static void appendPair(StringBuilder sb, int depth, String key, int value) {
        indent(sb, depth);
        sb.append('"').append(key).append("\": ").append(value);
    }

    private static void appendEscapedPair(StringBuilder sb, int depth, String key, String value) {
        indent(sb, depth);
        sb.append('"').append(key).append("\": ");
        appendEscaped(sb, value);
    }

    private static void appendEscaped(StringBuilder sb, String s) {
        if (s == null) {
            sb.append("null");
            return;
        }
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static void indent(StringBuilder sb, int depth) {
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
    }

    @Override
    public void onDelete(RequestTreeNode node) {
        this.removeNodeFromTree(node);
    }
}