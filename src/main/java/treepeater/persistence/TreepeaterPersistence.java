package treepeater.persistence;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.swing.tree.DefaultTreeModel;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.persistence.PersistedObject;
import burp.api.montoya.persistence.Persistence;
import treepeater.Treepeater;
import treepeater.TreepeaterModel;
import treepeater.requestResponse.HistoryEntry;
import treepeater.requestResponse.RequestHistory;
import treepeater.requestResponse.Status;
import treepeater.tree.RequestTree;
import treepeater.tree.RequestTreeNode;

public class TreepeaterPersistence {
    private final Persistence persistence;

    private static final String PERSISTENCE_ROOT = "treepeater";
    private static final String PERSISTENCE_TREE = "tree";
    private static final String PERSISTENCE_TABS = "tabs";
    private static final String PERSISTENCE_CHILDREN = "children";
    private static final String PERSISTENCE_ACTIVE_NODE = "activeNode";
    private static final String PERSISTENCE_REQUEST_COUNT = "requestCount";
    private static final String PERSISTENCE_ID = "id";
    private static final String PERSISTENCE_STATUS = "status";
    private static final String PERSISTENCE_NAME = "name";
    private static final String PERSISTENCE_REQUEST = "request";
    private static final String PERSISTENCE_RESPONSE = "response";
    private static final String PERSISTENCE_HISTORY = "history";
    private static final String PERSISTENCE_HISTORY_INDEX = "currentIndex";
    private static final String PERSISTENCE_HISTORY_ENTRIES = "entries";
    private static final String PERSISTENCE_HISTORY_ENTRY_INDEX = "index";
    private static final String PERSISTENCE_HISTORY_ENTRY_TIME = "time";
    private static final String PERSISTENCE_HISTORY_ENTRY_TARGET_LABEL = "targetLabel";
    private static final String PERSISTENCE_HISTORY_ENTRY_REQUEST = "request";
    private static final String PERSISTENCE_HISTORY_ENTRY_RESPONSE = "response";
    private static final String PERSISTENCE_SIZE = "size";

    public TreepeaterPersistence(Persistence persistence) {
        this.persistence = persistence;
    }

    public void save(TreepeaterModel model) {
        Treepeater.api.logging().logToOutput("Saving state");
        PersistedObject extensionData = this.persistence.extensionData();

        PersistedObject root = PersistedObject.persistedObject();
        root.setInteger(PERSISTENCE_REQUEST_COUNT, model.getRequestCount());

        PersistedObject tree = this.saveTree(model.getTree());
        root.setChildObject(PERSISTENCE_TREE, tree);

        if (model.getActiveNode() != null) {
            root.setInteger(PERSISTENCE_ACTIVE_NODE, model.getActiveNode().getId());
        } else {
            root.setInteger(PERSISTENCE_ACTIVE_NODE, -1);
        }
        root.setChildObject(PERSISTENCE_TABS, saveTabs(model.getTabs()));

        extensionData.setChildObject(PERSISTENCE_ROOT, root);
    }

    

    private PersistedObject saveTabs(List<RequestTreeNode> tabs) {
        PersistedObject tabsObject = PersistedObject.persistedObject();
        tabsObject.setInteger(PERSISTENCE_SIZE, tabs.size());
        for (int idx = 0; idx < tabs.size(); idx++) {
            RequestTreeNode tab = tabs.get(idx);
            // Do not store the entire tab. In that case we would create copies
            // of the tab and its children. Instead, we only store the id of the tab.
            // On load reconstruct the tabs from the node in the node and reference it instead.
            tabsObject.setInteger(PERSISTENCE_TABS + "_" + idx, tab.getId());
        }
        return tabsObject;
    }

    private PersistedObject saveTree(RequestTree tree) {
        DefaultTreeModel treeModel = tree.getTreeModel();
        RequestTreeNode root = (RequestTreeNode) treeModel.getRoot();

        PersistedObject treeObject = PersistedObject.persistedObject();

        PersistedObject nodes = saveTreeNode(root);
        treeObject.setChildObject(PERSISTENCE_CHILDREN, nodes);

        return treeObject;
    }

    private PersistedObject saveTreeNode(RequestTreeNode node) {
        PersistedObject nodeObject = PersistedObject.persistedObject();

        nodeObject.setInteger(PERSISTENCE_ID, node.getId());
        nodeObject.setString(PERSISTENCE_STATUS, node.getStatus().getStatus());
        nodeObject.setString(PERSISTENCE_NAME, node.getName());
        nodeObject.setHttpRequest(PERSISTENCE_REQUEST, node.getRequest());
        nodeObject.setHttpResponse(PERSISTENCE_RESPONSE, node.getResponse());
        nodeObject.setChildObject(PERSISTENCE_HISTORY, saveHistory(node.getHistory()));

        nodeObject.setInteger(PERSISTENCE_SIZE, node.getChildCount());
        for (int idx = 0; idx < node.getChildCount(); idx++) {
            RequestTreeNode child = (RequestTreeNode) node.getChildAt(idx);
            PersistedObject childObject = saveTreeNode(child);
            nodeObject.setChildObject(PERSISTENCE_CHILDREN + "_" + idx, childObject);
        }

        return nodeObject;
    }

    private PersistedObject saveHistory(RequestHistory history) {
        PersistedObject historyObject = PersistedObject.persistedObject();

        historyObject.setInteger(PERSISTENCE_HISTORY_INDEX, history.getCurrentIndex());

        PersistedObject entriesObject = PersistedObject.persistedObject();

        entriesObject.setInteger(PERSISTENCE_SIZE, history.entries().size());

        for (HistoryEntry entry : history.entries()) {
            PersistedObject entryObject = PersistedObject.persistedObject();
            entryObject.setInteger(PERSISTENCE_HISTORY_ENTRY_INDEX, entry.getIndex());
            entryObject.setString(PERSISTENCE_HISTORY_ENTRY_TIME, entry.getTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            entryObject.setString(PERSISTENCE_HISTORY_ENTRY_TARGET_LABEL, entry.getTargetLabel());
            entryObject.setHttpRequest(PERSISTENCE_HISTORY_ENTRY_REQUEST, entry.getRequest());
            entryObject.setHttpResponse(PERSISTENCE_HISTORY_ENTRY_RESPONSE, entry.getResponse());
            entriesObject.setChildObject(PERSISTENCE_HISTORY_ENTRY_INDEX + "_" + entry.getIndex(), entryObject);
        }

        historyObject.setChildObject(PERSISTENCE_HISTORY_ENTRIES, entriesObject);

        return historyObject;
    }

    public TreepeaterModel load() {
        
        Treepeater.api.logging().logToOutput("Loading state from file");
        PersistedObject extensionData = this.persistence.extensionData();

        PersistedObject root = extensionData.getChildObject(PERSISTENCE_ROOT);

        if (root == null) {
            return new TreepeaterModel();
        }

        Integer requestCount = root.getInteger(PERSISTENCE_REQUEST_COUNT);

        RequestTree tree = this.loadTree(root.getChildObject(PERSISTENCE_TREE));
    
        // Go though the entire tree and build a map that maps each node ID to the actual request node
        HashMap<Integer, RequestTreeNode> nodeMap = this.buildNodeIdMap(tree);

        LinkedList<RequestTreeNode> tabs = this.loadTabs(root.getChildObject(PERSISTENCE_TABS), nodeMap);

        int activeNodeId = root.getInteger(PERSISTENCE_ACTIVE_NODE).intValue();
        RequestTreeNode activeNode = null;
        if (activeNodeId != -1) {
            activeNode = nodeMap.get(activeNodeId);
        }

        TreepeaterModel model = new TreepeaterModel(tree, tabs, activeNode, requestCount.intValue());

        return model;
    }

    private RequestTree loadTree(PersistedObject treeObject) {
        RequestTree tree = new RequestTree();

        // This will be the root and empty treepeater node. Don't actually load this
        PersistedObject rootChild = treeObject.getChildObject(PERSISTENCE_CHILDREN);

        // Do a depth-first traversal through the tree and slowly load each node
        int childCount = rootChild.getInteger(PERSISTENCE_SIZE).intValue();

        for (int idx = 0; idx < childCount; idx++) {
            PersistedObject childObject = rootChild.getChildObject(PERSISTENCE_CHILDREN + "_" + idx);
            RequestTreeNode child = this.loadTreeNode(childObject);
            tree.insertRootNode(child);
        }

        return tree;
    }

    private RequestTreeNode loadTreeNode(PersistedObject nodeObject) {
        int id = nodeObject.getInteger(PERSISTENCE_ID).intValue();
        String name = nodeObject.getString(PERSISTENCE_NAME);
        String status = nodeObject.getString(PERSISTENCE_STATUS);
        HttpRequest request = nodeObject.getHttpRequest(PERSISTENCE_REQUEST);
        HttpResponse response = nodeObject.getHttpResponse(PERSISTENCE_RESPONSE);
        RequestHistory history = this.loadHistory(nodeObject.getChildObject(PERSISTENCE_HISTORY));
        RequestTreeNode node = new RequestTreeNode(id, Status.fromName(status), name, request, response, history);

        int childCount = nodeObject.getInteger(PERSISTENCE_SIZE).intValue();
        for (int idx = 0; idx < childCount; idx++) {
            PersistedObject childObject = nodeObject.getChildObject(PERSISTENCE_CHILDREN + "_" + idx);
            RequestTreeNode child = this.loadTreeNode(childObject);
            node.add(child);
        }

        return node;
    }


    private RequestHistory loadHistory(PersistedObject historyObject) {
        int currentIndex = historyObject.getInteger(PERSISTENCE_HISTORY_INDEX).intValue();
        PersistedObject entriesObject = historyObject.getChildObject(PERSISTENCE_HISTORY_ENTRIES);
        int entriesCount = entriesObject.getInteger(PERSISTENCE_SIZE).intValue();
        List<HistoryEntry> entries = new LinkedList<>();
        for (int idx = 0; idx < entriesCount; idx++) {
            PersistedObject entryObject = entriesObject.getChildObject(PERSISTENCE_HISTORY_ENTRY_INDEX + "_" + idx);
            HistoryEntry entry = this.loadHistoryEntry(entryObject);
            entries.add(entry);
        }
        return new RequestHistory(currentIndex, entries);
    }

    private HistoryEntry loadHistoryEntry(PersistedObject entryObject) {
        int index = entryObject.getInteger(PERSISTENCE_HISTORY_ENTRY_INDEX).intValue();
        String time = entryObject.getString(PERSISTENCE_HISTORY_ENTRY_TIME);
        String targetLabel = entryObject.getString(PERSISTENCE_HISTORY_ENTRY_TARGET_LABEL);
        HttpRequest request = entryObject.getHttpRequest(PERSISTENCE_HISTORY_ENTRY_REQUEST);
        HttpResponse response = entryObject.getHttpResponse(PERSISTENCE_HISTORY_ENTRY_RESPONSE);
        return new HistoryEntry(index, LocalDateTime.parse(time), targetLabel, request, response);
    }

    private LinkedList<RequestTreeNode> loadTabs(PersistedObject tabsObject, HashMap<Integer, RequestTreeNode> nodeMap) {
        int tabCount = tabsObject.getInteger(PERSISTENCE_SIZE).intValue();

        LinkedList<RequestTreeNode> tabs = new LinkedList<>();

        for (int idx = 0; idx < tabCount; idx++) {
            int nodeId = tabsObject.getInteger(PERSISTENCE_TABS + "_" + idx).intValue();
            RequestTreeNode node = nodeMap.get(nodeId);
            if (node != null) {
                tabs.add(node);
            }
        }
        
        return tabs;
    }

    /**
     * Traverses the given RequestTree and returns a map from node IDs to RequestTreeNodes.
     * @param tree The RequestTree to traverse.
     * @return HashMap mapping node IDs to their corresponding RequestTreeNode.
     */
    private HashMap<Integer, RequestTreeNode> buildNodeIdMap(RequestTree tree) {
        HashMap<Integer, RequestTreeNode> nodeMap = new HashMap<>();

        traverseAndMap((RequestTreeNode) tree.getModel().getRoot(), nodeMap);
        return nodeMap;
    }

    /**
     * Helper method to recursively traverse the tree and fill the map.
     * @param node The current RequestTreeNode.
     * @param nodeMap The map to populate.
     */
    private void traverseAndMap(RequestTreeNode node, HashMap<Integer, RequestTreeNode> nodeMap) {
        if (node == null) {
            return;
        }
        nodeMap.put(node.getId(), node);
        for (int i = 0; i < node.getChildCount(); i++) {
            Object childObj = node.getChildAt(i);
            if (childObj instanceof RequestTreeNode) {
                traverseAndMap((RequestTreeNode) childObj, nodeMap);
            }
        }
    }
}
