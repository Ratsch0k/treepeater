package treepeater.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.swing.tree.DefaultTreeModel;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.persistence.PersistedObject;
import burp.api.montoya.persistence.Persistence;
import treepeater.Treepeater;
import treepeater.TreepeaterModel;
import treepeater.Utilities;
import treepeater.ai.AgentChatSession;
import treepeater.ai.AgentChatWorkspace;
import treepeater.ai.AgentMode;
import treepeater.ai.AiModelOption;
import treepeater.ai.AiModelRef;
import treepeater.ai.AnthropicOutputEffort;
import treepeater.ai.ChatMessage;
import treepeater.ai.ChatRole;
import treepeater.ai.ChatToolCall;
import treepeater.ai.LlmRequestOptions;
import treepeater.ai.OpenAiReasoningEffort;
import treepeater.requestResponse.HistoryEntry;
import treepeater.requestResponse.RequestHistory;
import treepeater.requestResponse.Status;
import treepeater.settings.StatusRegistry;
import treepeater.tree.RequestTree;
import treepeater.tree.RequestTreeNode;

public class TreepeaterPersistence {
    private final Persistence persistence;

    private static final String PERSISTENCE_ROOT = "treepeater_v1";
    private static final String PERSISTENCE_GLOBAL_UI = "globalUi";
    private static final String PERSISTENCE_TREE = "tree";
    private static final String PERSISTENCE_TABS = "tabs";
    private static final String PERSISTENCE_CHILDREN = "children";
    private static final String PERSISTENCE_ACTIVE_NODE = "activeNode";
    private static final String PERSISTENCE_REQUEST_COUNT = "requestCount";
    private static final String PERSISTENCE_ID = "id";
    private static final String PERSISTENCE_STATUS = "status";
    private static final String PERSISTENCE_NAME = "name";
    private static final String PERSISTENCE_NOTES = "notes";
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
    private static final String PERSISTENCE_STATUS_REGISTRY = "statusRegistry";
    private static final String PERSISTENCE_STATUS_ID = "id";
    private static final String PERSISTENCE_STATUS_BACKGROUND_COLOR = "backgroundColor";
    private static final String PERSISTENCE_STATUS_BORDER_COLOR = "borderColor";
    private static final String PERSISTENCE_STATUS_SVG_CONTENT = "svgContent";

    private static final String PERSISTENCE_STATUS_COLOR_MODE = "colorMode";
    private static final String COLOR_MODE_VALUE = "VALUE";
    private static final String COLOR_MODE_NAMED = "NAMED";
    private static final String PERSISTENCE_STATUS_COLOR_BG_LIGHT = "colorBgLight";
    private static final String PERSISTENCE_STATUS_COLOR_BORDER_LIGHT = "colorBorderLight";
    private static final String PERSISTENCE_STATUS_COLOR_BG_DARK = "colorBgDark";
    private static final String PERSISTENCE_STATUS_COLOR_BORDER_DARK = "colorBorderDark";
    private static final String PERSISTENCE_STATUS_KEY_BG_LIGHT = "keyBgLight";
    private static final String PERSISTENCE_STATUS_KEY_BORDER_LIGHT = "keyBorderLight";
    private static final String PERSISTENCE_STATUS_KEY_BG_DARK = "keyBgDark";
    private static final String PERSISTENCE_STATUS_KEY_BORDER_DARK = "keyBorderDark";

    private static final String PERSISTENCE_AGENT_CHATS = "agentChats";
    private static final String PERSISTENCE_AGENT_CHATS_VERSION = "agentChatsVersion";
    private static final int AGENT_CHATS_VERSION = 1;
    private static final String PERSISTENCE_AGENT_SELECTED_TAB = "agentSelectedTab";
    private static final String PERSISTENCE_AGENT_NEXT_TAB_INDEX = "agentNextTabIndex";
    private static final String PERSISTENCE_AGENT_SESSION = "s";
    private static final String PERSISTENCE_AGENT_SESSION_TITLE = "agentTitle";
    private static final String PERSISTENCE_AGENT_SESSION_MODE = "agentMode";
    private static final String PERSISTENCE_AGENT_SESSION_MODEL = "agentModel";
    private static final String PERSISTENCE_MODEL_LABEL = "modelLabel";
    private static final String PERSISTENCE_MODEL_KIND = "modelKind";
    private static final String PERSISTENCE_MODEL_OLLAMA = "ollamaModel";
    private static final String PERSISTENCE_MODEL_ANTHROPIC = "anthropicModel";
    private static final String PERSISTENCE_MODEL_OPENAI_DEP = "openAiDeployment";
    private static final String PERSISTENCE_LLM_OAI_REASON = "llmOaiReasoning";
    private static final String PERSISTENCE_LLM_ANTH_EFFORT = "llmAnthropicEffort";
    private static final String PERSISTENCE_LLM_ANTH_THINK = "llmAnthropicExtendedThinking";
    private static final String PERSISTENCE_AGENT_MESSAGES = "agentMessages";
    private static final String PERSISTENCE_AGENT_MESSAGE = "m";
    private static final String PERSISTENCE_MSG_ROLE = "msgRole";
    private static final String PERSISTENCE_MSG_CONTENT = "msgContent";
    private static final String PERSISTENCE_MSG_TOOL_CALL_ID = "msgToolCallId";
    private static final String PERSISTENCE_MSG_TOOL_CALLS = "msgToolCalls";
    private static final String PERSISTENCE_TOOL_CALL = "call";
    private static final String PERSISTENCE_TOOL_CALL_ID = "toolId";
    private static final String PERSISTENCE_TOOL_CALL_NAME = "toolName";
    private static final String PERSISTENCE_TOOL_CALL_ARGS = "toolArgsJson";

    private record LegacyNodeUiData(String notes, AgentChatWorkspace workspace) {
    }

    public TreepeaterPersistence(Persistence persistence) {
        this.persistence = persistence;
    }

    public void saveModel(TreepeaterModel model) {
        PersistedObject extensionData = this.persistence.extensionData();

        PersistedObject root = extensionData.childObjectKeys().contains(PERSISTENCE_ROOT) ? extensionData.getChildObject(PERSISTENCE_ROOT) : PersistedObject.persistedObject();
        root.setInteger(PERSISTENCE_REQUEST_COUNT, model.getRequestCount());

        PersistedObject tree = this.saveTree(model.getTree());
        root.setChildObject(PERSISTENCE_TREE, tree);

        if (model.getActiveNode() != null) {
            root.setInteger(PERSISTENCE_ACTIVE_NODE, model.getActiveNode().getId());
        } else {
            root.setInteger(PERSISTENCE_ACTIVE_NODE, -1);
        }
        root.setChildObject(PERSISTENCE_TABS, saveTabs(model.getTabs()));

        PersistedObject globalUi = PersistedObject.persistedObject();
        globalUi.setString(PERSISTENCE_NOTES, model.getGlobalNotes());
        globalUi.setChildObject(PERSISTENCE_AGENT_CHATS, saveAgentChatWorkspace(model.getGlobalAgentChatWorkspace()));
        root.setChildObject(PERSISTENCE_GLOBAL_UI, globalUi);

        extensionData.setChildObject(PERSISTENCE_ROOT, root);

    }

    public void saveStatusRegistry(StatusRegistry statusRegistry) {
        PersistedObject extensionData = this.persistence.extensionData();
        PersistedObject root = extensionData.childObjectKeys().contains(PERSISTENCE_ROOT) ? extensionData.getChildObject(PERSISTENCE_ROOT) : PersistedObject.persistedObject();

        PersistedObject statusesObject = PersistedObject.persistedObject();
        List<Status> statuses = statusRegistry.getAll();
        statusesObject.setInteger(PERSISTENCE_SIZE, statuses.size()-1);

        
        // Important: Skip the default status
        for (int idx = 1; idx < statuses.size(); idx++) {
            Status status = statuses.get(idx);
            statusesObject.setChildObject(PERSISTENCE_STATUS + "_" + (idx - 1), saveStatus(status));
        }

        root.setChildObject(PERSISTENCE_STATUS_REGISTRY, statusesObject);

        extensionData.setChildObject(PERSISTENCE_ROOT, root);
    }

    private PersistedObject saveStatus(Status status) {
        PersistedObject statusObject = PersistedObject.persistedObject();
        statusObject.setString(PERSISTENCE_STATUS, status.getStatus());
        statusObject.setString(PERSISTENCE_STATUS_ID, status.getId());
        statusObject.setString(PERSISTENCE_STATUS_SVG_CONTENT, status.getSvgContent());

        if (status.getColors().isPresent()) {
            Status.StatusColors c = status.getColors().get();
            statusObject.setString(PERSISTENCE_STATUS_COLOR_MODE, COLOR_MODE_VALUE);
            statusObject.setString(PERSISTENCE_STATUS_COLOR_BG_LIGHT, Utilities.colorToHex(c.backgroundColor()));
            statusObject.setString(PERSISTENCE_STATUS_COLOR_BORDER_LIGHT, Utilities.colorToHex(c.borderColor()));
            statusObject.setString(PERSISTENCE_STATUS_COLOR_BG_DARK, Utilities.colorToHex(c.backgroundDarkModeColor()));
            statusObject.setString(PERSISTENCE_STATUS_COLOR_BORDER_DARK, Utilities.colorToHex(c.borderColorDarkModeColor()));
        } else if (status.getNamedColors().isPresent()) {
            Status.StatusNamedColors k = status.getNamedColors().get();
            statusObject.setString(PERSISTENCE_STATUS_COLOR_MODE, COLOR_MODE_NAMED);
            statusObject.setString(PERSISTENCE_STATUS_KEY_BG_LIGHT, k.backgroundColorKey());
            statusObject.setString(PERSISTENCE_STATUS_KEY_BORDER_LIGHT, k.borderColorKey());
            statusObject.setString(PERSISTENCE_STATUS_KEY_BG_DARK, k.backgroundDarkModeColorKey());
            statusObject.setString(PERSISTENCE_STATUS_KEY_BORDER_DARK, k.borderColorDarkModeColorKey());
        } else {
            throw new IllegalStateException("Status has neither value colors nor named colors: " + status.getId());
        }

        return statusObject;
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

    public TreepeaterModel loadModel() {
        PersistedObject extensionData = this.persistence.extensionData();

        PersistedObject root = extensionData.getChildObject(PERSISTENCE_ROOT);

        if (root == null) {
            return new TreepeaterModel();
        }

        Integer requestCount = root.getInteger(PERSISTENCE_REQUEST_COUNT);

        Map<Integer, LegacyNodeUiData> legacyUiByNodeId = new HashMap<>();
        RequestTree tree = this.loadTree(root.getChildObject(PERSISTENCE_TREE), legacyUiByNodeId);

        // Go though the entire tree and build a map that maps each node ID to the actual request node
        HashMap<Integer, RequestTreeNode> nodeMap = this.buildNodeIdMap(tree);

        LinkedList<RequestTreeNode> tabs = this.loadTabs(root.getChildObject(PERSISTENCE_TABS), nodeMap);

        int activeNodeId = root.getInteger(PERSISTENCE_ACTIVE_NODE).intValue();
        RequestTreeNode activeNode = null;
        if (activeNodeId != -1) {
            activeNode = nodeMap.get(activeNodeId);
        }

        String globalNotes = "";
        AgentChatWorkspace globalWs = AgentChatWorkspace.EMPTY;
        PersistedObject globalUi = root.getChildObject(PERSISTENCE_GLOBAL_UI);
        if (globalUi != null) {
            String p = globalUi.getString(PERSISTENCE_NOTES);
            globalNotes = p != null ? p : "";
            if (globalUi.childObjectKeys().contains(PERSISTENCE_AGENT_CHATS)) {
                globalWs = this.loadAgentChatWorkspace(globalUi.getChildObject(PERSISTENCE_AGENT_CHATS));
            }
        } else if (activeNode != null) {
            LegacyNodeUiData leg = legacyUiByNodeId.get(activeNode.getId());
            if (leg != null) {
                globalNotes = leg.notes() != null ? leg.notes() : "";
                globalWs = leg.workspace() != null ? leg.workspace() : AgentChatWorkspace.EMPTY;
            }
        }

        return new TreepeaterModel(tree, tabs, activeNode, requestCount.intValue(), globalNotes, globalWs);
    }

    public boolean hasStatusRegistry() {
        PersistedObject extensionData = this.persistence.extensionData();
        return extensionData.childObjectKeys().contains(PERSISTENCE_ROOT) && extensionData.getChildObject(PERSISTENCE_ROOT).childObjectKeys().contains(PERSISTENCE_STATUS_REGISTRY);
    }

    public StatusRegistry loadStatusRegistry() {
        PersistedObject extensionData = this.persistence.extensionData();
        PersistedObject root = extensionData.getChildObject(PERSISTENCE_ROOT);

        PersistedObject statusesObject = root.getChildObject(PERSISTENCE_STATUS_REGISTRY);

        if (statusesObject == null) {
            return new StatusRegistry();
        }

        int statusCount = statusesObject.getInteger(PERSISTENCE_SIZE).intValue();

        if (statusCount == 0) {
            return new StatusRegistry(List.of());
        }
        List<Status> statuses = new ArrayList<>();

        for (int idx = 0; idx < statusCount; idx++) {
            PersistedObject statusObject = statusesObject.getChildObject(PERSISTENCE_STATUS + "_" + idx);
            Status status = this.loadStatus(statusObject);
            statuses.add(status);
        }
        return new StatusRegistry(statuses);
    }

    private Status loadStatus(PersistedObject statusObject) {
        String status = statusObject.getString(PERSISTENCE_STATUS);
        String id = statusObject.getString(PERSISTENCE_STATUS_ID);
        String svgContent = statusObject.getString(PERSISTENCE_STATUS_SVG_CONTENT);

        String mode = statusObject.getString(PERSISTENCE_STATUS_COLOR_MODE);

        if (COLOR_MODE_NAMED.equals(mode)) {
            Status.StatusNamedColors named = new Status.StatusNamedColors(
                    statusObject.getString(PERSISTENCE_STATUS_KEY_BG_LIGHT),
                    statusObject.getString(PERSISTENCE_STATUS_KEY_BORDER_LIGHT),
                    statusObject.getString(PERSISTENCE_STATUS_KEY_BG_DARK),
                    statusObject.getString(PERSISTENCE_STATUS_KEY_BORDER_DARK));
            return new Status(id, status, named, svgContent);
        }

        if (COLOR_MODE_VALUE.equals(mode)) {
            Status.StatusColors colors = new Status.StatusColors(
                    Utilities.hexToColor(statusObject.getString(PERSISTENCE_STATUS_COLOR_BG_LIGHT)),
                    Utilities.hexToColor(statusObject.getString(PERSISTENCE_STATUS_COLOR_BORDER_LIGHT)),
                    Utilities.hexToColor(statusObject.getString(PERSISTENCE_STATUS_COLOR_BG_DARK)),
                    Utilities.hexToColor(statusObject.getString(PERSISTENCE_STATUS_COLOR_BORDER_DARK)));
            return new Status(id, status, colors, svgContent);
        }

        // Legacy: only backgroundColor + borderColor (same for light and dark).
        String backgroundColor = statusObject.getString(PERSISTENCE_STATUS_BACKGROUND_COLOR);
        String borderColor = statusObject.getString(PERSISTENCE_STATUS_BORDER_COLOR);
        if (backgroundColor != null && borderColor != null) {
            Status.StatusColors colors = new Status.StatusColors(
                    Utilities.hexToColor(backgroundColor),
                    Utilities.hexToColor(borderColor),
                    Utilities.hexToColor(backgroundColor),
                    Utilities.hexToColor(borderColor));
            return new Status(id, status, colors, svgContent);
        }

        throw new IllegalStateException("Invalid persisted status (missing color data): " + id);
    }

    private RequestTree loadTree(PersistedObject treeObject, Map<Integer, LegacyNodeUiData> legacyUiByNodeId) {
        RequestTree tree = new RequestTree();

        if (treeObject == null) {
            return tree;
        }

        PersistedObject rootChild = treeObject.getChildObject(PERSISTENCE_CHILDREN);
        if (rootChild == null) {
            return tree;
        }

        int childCount = rootChild.getInteger(PERSISTENCE_SIZE).intValue();

        for (int idx = 0; idx < childCount; idx++) {
            PersistedObject childObject = rootChild.getChildObject(PERSISTENCE_CHILDREN + "_" + idx);
            RequestTreeNode child = this.loadTreeNode(childObject, legacyUiByNodeId);
            tree.insertRootNode(child);
        }

        return tree;
    }

    private RequestTreeNode loadTreeNode(PersistedObject nodeObject, Map<Integer, LegacyNodeUiData> legacyUiByNodeId) {
        int id = nodeObject.getInteger(PERSISTENCE_ID).intValue();
        String name = nodeObject.getString(PERSISTENCE_NAME);
        String statusId = nodeObject.getString(PERSISTENCE_STATUS);
        HttpRequest request = nodeObject.getHttpRequest(PERSISTENCE_REQUEST);
        HttpResponse response = nodeObject.getHttpResponse(PERSISTENCE_RESPONSE);
        RequestHistory history = this.loadHistory(nodeObject.getChildObject(PERSISTENCE_HISTORY));

        Status status = Treepeater.getStatusRegistry().getById(statusId);
        if (status == null) {
            status = StatusRegistry.getDefault();
        }

        String notesPersisted = nodeObject.getString(PERSISTENCE_NOTES);
        String notes = notesPersisted != null ? notesPersisted : "";
        AgentChatWorkspace agentWs = AgentChatWorkspace.EMPTY;
        if (nodeObject.childObjectKeys().contains(PERSISTENCE_AGENT_CHATS)) {
            agentWs = this.loadAgentChatWorkspace(nodeObject.getChildObject(PERSISTENCE_AGENT_CHATS));
        }
        legacyUiByNodeId.put(id, new LegacyNodeUiData(notes, agentWs));

        RequestTreeNode node = new RequestTreeNode(id, status, name, request, response, history);

        int childCount = nodeObject.getInteger(PERSISTENCE_SIZE).intValue();
        for (int idx = 0; idx < childCount; idx++) {
            PersistedObject childObject = nodeObject.getChildObject(PERSISTENCE_CHILDREN + "_" + idx);
            RequestTreeNode child = this.loadTreeNode(childObject, legacyUiByNodeId);
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
    private PersistedObject saveAgentChatWorkspace(AgentChatWorkspace ws) {
        AgentChatWorkspace w = ws != null ? ws : AgentChatWorkspace.EMPTY;
        PersistedObject o = PersistedObject.persistedObject();
        o.setInteger(PERSISTENCE_AGENT_CHATS_VERSION, AGENT_CHATS_VERSION);
        o.setInteger(PERSISTENCE_AGENT_SELECTED_TAB, w.selectedSessionIndex());
        o.setInteger(PERSISTENCE_AGENT_NEXT_TAB_INDEX, w.nextChatTabIndex());
        int n = w.sessions().size();
        o.setInteger(PERSISTENCE_SIZE, n);
        for (int i = 0; i < n; i++) {
            o.setChildObject(PERSISTENCE_AGENT_SESSION + "_" + i, saveAgentSession(w.sessions().get(i)));
        }
        return o;
    }

    private PersistedObject saveAgentSession(AgentChatSession s) {
        PersistedObject o = PersistedObject.persistedObject();
        o.setString(PERSISTENCE_AGENT_SESSION_TITLE, s.title());
        o.setString(PERSISTENCE_AGENT_SESSION_MODE, s.agentMode().name());
        o.setChildObject(PERSISTENCE_AGENT_SESSION_MODEL, saveAiModelRef(s.modelRef()));
        o.setString(PERSISTENCE_LLM_OAI_REASON, s.llmRequestOptions().openAiReasoningEffort().name());
        o.setString(PERSISTENCE_LLM_ANTH_EFFORT, s.llmRequestOptions().anthropicOutputEffort().name());
        o.setBoolean(PERSISTENCE_LLM_ANTH_THINK, s.llmRequestOptions().anthropicExtendedThinking());
        o.setChildObject(PERSISTENCE_AGENT_MESSAGES, saveChatMessages(s.conversation()));
        return o;
    }

    private PersistedObject saveAiModelRef(AiModelRef m) {
        PersistedObject o = PersistedObject.persistedObject();
        o.setString(PERSISTENCE_MODEL_LABEL, m.label());
        o.setString(PERSISTENCE_MODEL_KIND, m.kind().name());
        o.setString(PERSISTENCE_MODEL_OLLAMA, m.ollamaModel());
        o.setString(PERSISTENCE_MODEL_ANTHROPIC, m.anthropicModel());
        o.setString(PERSISTENCE_MODEL_OPENAI_DEP, m.openAiDeployment());
        return o;
    }

    private PersistedObject saveChatMessages(List<ChatMessage> messages) {
        PersistedObject o = PersistedObject.persistedObject();
        o.setInteger(PERSISTENCE_SIZE, messages.size());
        for (int i = 0; i < messages.size(); i++) {
            o.setChildObject(PERSISTENCE_AGENT_MESSAGE + "_" + i, saveChatMessage(messages.get(i)));
        }
        return o;
    }

    private PersistedObject saveChatMessage(ChatMessage m) {
        PersistedObject o = PersistedObject.persistedObject();
        o.setString(PERSISTENCE_MSG_ROLE, m.role().name());
        o.setString(PERSISTENCE_MSG_CONTENT, m.content());
        o.setString(PERSISTENCE_MSG_TOOL_CALL_ID, m.toolCallId());
        if (m.hasAssistantToolCalls()) {
            PersistedObject tcRoot = PersistedObject.persistedObject();
            List<ChatToolCall> calls = m.assistantToolCalls();
            tcRoot.setInteger(PERSISTENCE_SIZE, calls.size());
            for (int i = 0; i < calls.size(); i++) {
                tcRoot.setChildObject(PERSISTENCE_TOOL_CALL + "_" + i, saveToolCall(calls.get(i)));
            }
            o.setChildObject(PERSISTENCE_MSG_TOOL_CALLS, tcRoot);
        }
        return o;
    }

    private PersistedObject saveToolCall(ChatToolCall tc) {
        PersistedObject o = PersistedObject.persistedObject();
        o.setString(PERSISTENCE_TOOL_CALL_ID, tc.id());
        o.setString(PERSISTENCE_TOOL_CALL_NAME, tc.name());
        o.setString(PERSISTENCE_TOOL_CALL_ARGS, tc.argumentsJson());
        return o;
    }

    private AgentChatWorkspace loadAgentChatWorkspace(PersistedObject o) {
        if (o == null) {
            return AgentChatWorkspace.EMPTY;
        }
        int version = 1;
        if (o.childObjectKeys() != null && o.getInteger(PERSISTENCE_AGENT_CHATS_VERSION) != null) {
            version = o.getInteger(PERSISTENCE_AGENT_CHATS_VERSION).intValue();
        }
        if (version != 1) {
            return AgentChatWorkspace.EMPTY;
        }
        int selected = o.getInteger(PERSISTENCE_AGENT_SELECTED_TAB) != null ? o.getInteger(PERSISTENCE_AGENT_SELECTED_TAB).intValue() : 0;
        int nextIdx = o.getInteger(PERSISTENCE_AGENT_NEXT_TAB_INDEX) != null ? o.getInteger(PERSISTENCE_AGENT_NEXT_TAB_INDEX).intValue() : 1;
        int n = o.getInteger(PERSISTENCE_SIZE) != null ? o.getInteger(PERSISTENCE_SIZE).intValue() : 0;
        List<AgentChatSession> sessions = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            PersistedObject so = o.getChildObject(PERSISTENCE_AGENT_SESSION + "_" + i);
            if (so != null) {
                sessions.add(loadAgentSession(so));
            }
        }
        return new AgentChatWorkspace(sessions, selected, nextIdx);
    }

    private AgentChatSession loadAgentSession(PersistedObject o) {
        String title = o.getString(PERSISTENCE_AGENT_SESSION_TITLE);
        if (title == null) {
            title = "Chat";
        }
        String modeS = o.getString(PERSISTENCE_AGENT_SESSION_MODE);
        AgentMode mode = AgentMode.ASK;
        if (modeS != null) {
            try {
                mode = AgentMode.valueOf(modeS);
            } catch (IllegalArgumentException ignored) {
            }
        }
        AiModelRef modelRef = loadAiModelRef(o.getChildObject(PERSISTENCE_AGENT_SESSION_MODEL));
        LlmRequestOptions llm = loadLlmOptions(o);
        List<ChatMessage> messages = loadChatMessages(o.getChildObject(PERSISTENCE_AGENT_MESSAGES));
        return new AgentChatSession(title, messages, mode, modelRef, llm);
    }

    private LlmRequestOptions loadLlmOptions(PersistedObject o) {
        OpenAiReasoningEffort oai = LlmRequestOptions.DEFAULTS.openAiReasoningEffort();
        String s1 = o.getString(PERSISTENCE_LLM_OAI_REASON);
        if (s1 != null) {
            try {
                oai = OpenAiReasoningEffort.valueOf(s1);
            } catch (IllegalArgumentException ignored) {
            }
        }
        AnthropicOutputEffort ao = LlmRequestOptions.DEFAULTS.anthropicOutputEffort();
        String s2 = o.getString(PERSISTENCE_LLM_ANTH_EFFORT);
        if (s2 != null) {
            try {
                ao = AnthropicOutputEffort.valueOf(s2);
            } catch (IllegalArgumentException ignored) {
            }
        }
        boolean think = o.getBoolean(PERSISTENCE_LLM_ANTH_THINK) != null
                && o.getBoolean(PERSISTENCE_LLM_ANTH_THINK).booleanValue();
        return new LlmRequestOptions(oai, ao, think);
    }

    private static AiModelRef loadAiModelRef(PersistedObject o) {
        if (o == null) {
            return AiModelRef.fromOption(null);
        }
        String label = o.getString(PERSISTENCE_MODEL_LABEL);
        String kindS = o.getString(PERSISTENCE_MODEL_KIND);
        AiModelOption.Kind kind = AiModelOption.Kind.BURP;
        if (kindS != null) {
            try {
                kind = AiModelOption.Kind.valueOf(kindS);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new AiModelRef(
                label,
                kind,
                o.getString(PERSISTENCE_MODEL_OLLAMA),
                o.getString(PERSISTENCE_MODEL_ANTHROPIC),
                o.getString(PERSISTENCE_MODEL_OPENAI_DEP));
    }

    private static List<ChatMessage> loadChatMessages(PersistedObject o) {
        if (o == null) {
            return List.of();
        }
        int n = o.getInteger(PERSISTENCE_SIZE) != null ? o.getInteger(PERSISTENCE_SIZE).intValue() : 0;
        List<ChatMessage> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            PersistedObject mo = o.getChildObject(PERSISTENCE_AGENT_MESSAGE + "_" + i);
            if (mo != null) {
                list.add(loadChatMessage(mo));
            }
        }
        return list;
    }

    private static ChatMessage loadChatMessage(PersistedObject o) {
        String roleS = o.getString(PERSISTENCE_MSG_ROLE);
        ChatRole role = ChatRole.USER;
        if (roleS != null) {
            try {
                role = ChatRole.valueOf(roleS);
            } catch (IllegalArgumentException ignored) {
            }
        }
        String content = o.getString(PERSISTENCE_MSG_CONTENT);
        if (content == null) {
            content = "";
        }
        String toolCallId = o.getString(PERSISTENCE_MSG_TOOL_CALL_ID);
        List<ChatToolCall> calls = new ArrayList<>();
        if (o.childObjectKeys().contains(PERSISTENCE_MSG_TOOL_CALLS)) {
            PersistedObject tcRoot = o.getChildObject(PERSISTENCE_MSG_TOOL_CALLS);
            int n = tcRoot.getInteger(PERSISTENCE_SIZE) != null ? tcRoot.getInteger(PERSISTENCE_SIZE).intValue() : 0;
            for (int i = 0; i < n; i++) {
                PersistedObject tco = tcRoot.getChildObject(PERSISTENCE_TOOL_CALL + "_" + i);
                if (tco != null) {
                    calls.add(
                            new ChatToolCall(
                                    tco.getString(PERSISTENCE_TOOL_CALL_ID),
                                    tco.getString(PERSISTENCE_TOOL_CALL_NAME),
                                    tco.getString(PERSISTENCE_TOOL_CALL_ARGS)));
                }
            }
        }
        return new ChatMessage(role, content, List.copyOf(calls), toolCallId);
    }

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
