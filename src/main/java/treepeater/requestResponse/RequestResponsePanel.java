package treepeater.requestResponse;

import java.awt.BorderLayout;
import java.awt.Font;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.tree.TreePath;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import treepeater.ai.AgentToolContext;
import treepeater.ai.HttpTargetSnapshot;
import treepeater.Treepeater;
import treepeater.TreepeaterModel;
import treepeater.components.CustomButton;
import treepeater.settings.TreepeaterSettings;
import treepeater.tree.RequestTree;
import treepeater.tree.RequestTreeNode;
import treepeater.tree.CustomTreeCellEditor.ProgrammaticEdit;

public class RequestResponsePanel extends JPanel {

    private final TreepeaterModel model;
    private final RequestTree tree;
    private final RequestTreeNode node;

    private HttpRequestEditor requestEditor;
    private HttpResponseEditor responseEditor;

    private CustomButton sendButton;
    private JCheckBox updateContentLengthCheckBox;
    private JButton cancelButton;
    private JPanel topBar;
    private JPanel topBarWrapper;

    private JSplitPane splitPane;

    private final List<RequestResponseChangeListener> requestResponseChangeListeners = new CopyOnWriteArrayList<>();

    private JButton historyBackButton;
    private JButton historyBackDropButton;
    private JButton historyForwardButton;
    private JButton historyForwardDropButton;
    private JPanel historyBackSplitButton;
    private JPanel historyForwardSplitButton;

    private JLabel targetValueLabel;
    private JButton editTargetButton;

    private final RequestPanelHttpTarget httpTarget = new RequestPanelHttpTarget();

    private RequestHistoryNavigator historyNavigator;

    private final HotkeyHandler hotkeyHandler;
    private final AtomicBoolean hotkeyHandlerRegistered = new AtomicBoolean(false);

    private final HashMap<String, Runnable> hotkeyActions = new HashMap<>();

    private final Runnable selectPreviousRequestResponseTab;
    private final Runnable selectNextRequestResponseTab;

    public RequestResponsePanel(
            TreepeaterModel model,
            RequestTreeNode node,
            Runnable selectPreviousRequestResponseTab,
            Runnable selectNextRequestResponseTab) {
        super(new BorderLayout());
        this.model = model;
        this.tree = model.getTree();
        this.node = node;
        this.selectPreviousRequestResponseTab = selectPreviousRequestResponseTab;
        this.selectNextRequestResponseTab = selectNextRequestResponseTab;

        this.topBar = new JPanel();
        this.topBar.setLayout(new BoxLayout(this.topBar, BoxLayout.X_AXIS));
        this.topBar.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        this.targetValueLabel = new JLabel();
        this.targetValueLabel.setFont(this.targetValueLabel.getFont().deriveFont(Font.BOLD));

        this.editTargetButton = new JButton("Edit");
        this.editTargetButton.addActionListener(e -> openEditTargetDialog());

        this.historyBackButton = new JButton("<");
        this.historyForwardButton = new JButton(">");
        this.historyBackDropButton = new JButton("▼");
        this.historyForwardDropButton = new JButton("▼");

        this.historyBackSplitButton = RequestResponsePanelUi.buildHistorySplitButton(this.historyBackButton, this.historyBackDropButton);
        this.historyForwardSplitButton = RequestResponsePanelUi.buildHistorySplitButton(this.historyForwardButton, this.historyForwardDropButton);

        this.sendButton = new CustomButton("Send");
        this.sendButton.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));

        this.updateContentLengthCheckBox = new JCheckBox("Update Content-Length");
        this.updateContentLengthCheckBox.setSelected(true);
        this.updateContentLengthCheckBox.setToolTipText(
                "When enabled, Content-Length is set from the request body before sending (omitted for chunked encoding).");

        this.cancelButton = new JButton();

        this.httpTarget.initFromRequest(this.node.getRequest());
        this.refreshTargetLabel();

        RequestHistory.ensureSeededFromNode(this.node);

        this.topBar.add(this.sendButton);
        this.topBar.add(Box.createHorizontalStrut(6));
        this.topBar.add(this.cancelButton);

        this.topBar.add(Box.createHorizontalStrut(14));
        this.topBar.add(this.historyBackSplitButton);
        this.topBar.add(Box.createHorizontalStrut(4));
        this.topBar.add(this.historyForwardSplitButton);

        this.topBar.add(Box.createHorizontalStrut(6));
        this.topBar.add(this.updateContentLengthCheckBox);

        this.topBar.add(Box.createHorizontalGlue());
        this.topBar.add(this.targetValueLabel);
        this.topBar.add(Box.createHorizontalStrut(6));
        this.topBar.add(this.editTargetButton);

        this.topBarWrapper = new JPanel();
        this.topBarWrapper.setLayout(new BorderLayout());
        this.topBarWrapper.add(this.topBar, BorderLayout.CENTER);

        this.add(this.topBarWrapper, BorderLayout.PAGE_START);

        this.applyThemeLocalStyles();

        this.splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        Treepeater.api.userInterface().applyThemeToComponent(this.splitPane);

        this.requestEditor = Treepeater.api.userInterface().createHttpRequestEditor();
        this.requestEditor.setRequest(this.node.getRequest());

        this.responseEditor = Treepeater.api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);
        this.responseEditor.setResponse(this.node.getResponse());

        this.historyNavigator = new RequestHistoryNavigator(
                this.node,
                this.requestEditor,
                this.historyBackButton,
                this.historyBackDropButton,
                this.historyForwardButton,
                this.historyForwardDropButton,
                this::setResponse,
                () -> {
                    this.httpTarget.initFromRequest(this.requestEditor.getRequest());
                    this.refreshTargetLabel();
                    this.historyNavigator.refreshNavState();
                    this.notifyRequestChanged();
                    this.notifyResponseChanged();
                });

        this.historyBackButton.addActionListener(e -> this.historyNavigator.navigateBack());
        this.historyForwardButton.addActionListener(e -> this.historyNavigator.navigateForward());
        this.historyBackDropButton.addActionListener(e -> this.historyNavigator.showHistoryMenu(-1, this.historyBackDropButton));
        this.historyForwardDropButton.addActionListener(e -> this.historyNavigator.showHistoryMenu(1, this.historyForwardDropButton));

        this.historyNavigator.refreshNavState();

        new RequestResponseSendCoordinator(
                this.httpTarget,
                this.sendButton,
                this.cancelButton,
                this::prepareAndCommitRequestForSend,
                (snapshot, response, time, label) -> {
                    this.setResponse(response);
                    this.addToHistory(snapshot, response, time, label);
                    this.refreshTreeAndRequestListenersAfterSendResponse();
                }).registerActions();

        this.splitPane.setLeftComponent(RequestResponsePanelUi.makeHeaderPanel("Request", this.requestEditor.uiComponent()));
        this.splitPane.setRightComponent(RequestResponsePanelUi.makeHeaderPanel("Response", this.responseEditor.uiComponent()));

        this.splitPane.setDividerLocation(0.5);
        this.splitPane.setResizeWeight(0.5);

        this.notifyRequestChanged();
        this.notifyResponseChanged();

        this.add(this.splitPane, BorderLayout.CENTER);

        this.hotkeyHandler = new HotkeyHandler();
        this.populateHotkeyActions();
        RequestResponseHotkeyInstaller.install(this, this.hotkeyHandler, this.hotkeyActions, this.hotkeyHandlerRegistered);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        this.applyThemeLocalStyles();
    }

    /**
     * Re-reads {@link UIManager} colors for controls that are configured once in the constructor
     * but must track Burp theme changes.
     */
    private void applyThemeLocalStyles() {
        RequestResponsePanelUi.applyTopBarTheme(
                this.topBarWrapper,
                this.sendButton,
                this.historyBackSplitButton,
                this.historyForwardSplitButton,
                this.historyBackButton,
                this.historyBackDropButton,
                this.historyForwardButton,
                this.historyForwardDropButton);
        if (this.splitPane != null && Treepeater.api != null) {
            Treepeater.api.userInterface().applyThemeToComponent(this.splitPane);
        }
        if (this.updateContentLengthCheckBox != null && Treepeater.api != null) {
            Treepeater.api.userInterface().applyThemeToComponent(this.updateContentLengthCheckBox);
        }
    }

    private void populateHotkeyActions() {
        this.hotkeyActions.put(TreepeaterSettings.SEND_REQUEST_HOTKEY_SETTING, this::handleSendRequest);
        this.hotkeyActions.put(TreepeaterSettings.HISTORY_BACK_HOTKEY_SETTING, this.historyNavigator::navigateBack);
        this.hotkeyActions.put(TreepeaterSettings.HISTORY_FORWARD_HOTKEY_SETTING, this.historyNavigator::navigateForward);
        this.hotkeyActions.put(TreepeaterSettings.COPY_SAME_PARENT_REQUEST_HOTKEY_SETTING, this::handleCopySameParentRequest);
        this.hotkeyActions.put(TreepeaterSettings.RENAME_HOTKEY_SETTING, this::handleRename);
        this.hotkeyActions.put(TreepeaterSettings.CHANGE_STATUS_HOTKEY_SETTING, this::handleChangeStatus);
        this.hotkeyActions.put(TreepeaterSettings.EDIT_TARGET_HOTKEY_SETTING, () -> this.editTargetButton.doClick());
        this.hotkeyActions.put(TreepeaterSettings.TAB_PREVIOUS_HOTKEY_SETTING, this.selectPreviousRequestResponseTab);
        this.hotkeyActions.put(TreepeaterSettings.TAB_NEXT_HOTKEY_SETTING, this.selectNextRequestResponseTab);
    }

    private void handleSendRequest() {
        if (!this.sendButton.isEnabled()) {
            return;
        }
        this.sendButton.doClick();
    }

    private void handleCopySameParentRequest() {
        HttpRequest request = this.requestEditor.getRequest();
        HttpResponse response = this.responseEditor.getResponse();
        RequestTreeNode copy = this.model.copyAsSiblingUnderSameParent(this.node, request, response);
        if (copy == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            TreePath path = new TreePath(copy.getPath());
            this.tree.setSelectionPath(path);
            this.tree.scrollPathToVisible(path);
            copy.select();
        });
    }

    private void handleRename() {
        SwingUtilities.invokeLater(() -> this.tree.startProgrammaticEditForNode(this.node, ProgrammaticEdit.RENAME));
    }

    private void handleChangeStatus() {
        SwingUtilities.invokeLater(() -> this.tree.startProgrammaticEditForNode(this.node, ProgrammaticEdit.STATUS));
    }

    public void setRequest(HttpRequest request) {
        this.requestEditor.setRequest(request);
        this.node.setRequest(request);
        Treepeater.saveState();
        this.httpTarget.initFromRequest(request);
        this.refreshTargetLabel();
        this.notifyRequestChanged();
    }

    private void setResponse(HttpResponse response) {
        this.responseEditor.setResponse(response);
        this.node.setResponse(response);
        Treepeater.saveState();
        this.notifyResponseChanged();
    }

    private void addToHistory(HttpRequest request, HttpResponse response, LocalDateTime time, String targetLabel) {
        RequestHistory h = this.node.getHistory();
        h.addEntry(targetLabel, request, response);
        this.historyNavigator.refreshNavState();
        this.notifyResponseChanged();
    }

    public void addRequestResponseChangeListener(RequestResponseChangeListener listener) {
        this.requestResponseChangeListeners.add(listener);
    }

    public void removeRequestResponseChangeListener(RequestResponseChangeListener listener) {
        this.requestResponseChangeListeners.remove(listener);
    }

    /**
     * Notifies listeners with the current editor-backed request/response (e.g. after switching tabs).
     */
    public void refreshToolbarLinkedInfo() {
        this.notifyRequestChanged();
        this.notifyResponseChanged();
    }

    private void notifyRequestChanged() {
        LocalDateTime received = this.currentHistoryResponseTime();
        HttpRequest request = this.requestEditor.getRequest();
        HttpResponse response = this.responseEditor.getResponse();
        for (RequestResponseChangeListener listener : this.requestResponseChangeListeners) {
            listener.onRequestChanged(request, response, received);
        }
    }

    /**
     * Called on the EDT when the HTTP response has been received and applied ({@link RequestResponseSendCoordinator}
     * completion path). Refreshes the tree method prefix and {@link RequestResponseChangeListener} request snapshot.
     */
    private void refreshTreeAndRequestListenersAfterSendResponse() {
        this.tree.getTreeModel().nodeChanged(this.node);
        this.notifyRequestChanged();
    }

    private void notifyResponseChanged() {
        LocalDateTime received = this.currentHistoryResponseTime();
        HttpRequest request = this.requestEditor.getRequest();
        HttpResponse response = this.responseEditor.getResponse();
        for (RequestResponseChangeListener listener : this.requestResponseChangeListeners) {
            listener.onResponseChanged(request, response, received);
        }
    }

    private LocalDateTime currentHistoryResponseTime() {
        RequestHistory h = this.node.getHistory();
        if (h.isEmpty()) {
            return null;
        }
        int idx = h.getCurrentIndex();
        if (idx < 0 || idx >= h.size()) {
            return null;
        }
        try {
            return h.getEntry(idx).getTime();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void refreshTargetLabel() {
        String label = this.httpTarget.statusLineLabel();
        if (label.isEmpty()) {
            label = "Target: (not set)";
        } else {
            label = "Target: " + label;
        }
        this.targetValueLabel.setText(label);
    }

    private HttpTargetSnapshot buildTargetSnapshotForAi() {
        String method = "";
        String url = "";
        String path = "";
        String protocol = this.httpTarget.isHttps() ? "HTTPS" : "HTTP";
        String host = "";
        String portLabel = "";
        HttpRequest request = this.requestEditor.getRequest();
        if (request != null) {
            HttpService service = safeHttpService(request);
            if (service != null) {
                protocol = service.secure() ? "HTTPS" : "HTTP";
                String h = service.host();
                host = (h == null || h.isBlank()) ? "" : h;
                int port = service.port();
                portLabel = port > 0 ? String.valueOf(port) : "";
            } else {
                host = this.httpTarget.getHost() == null ? "" : this.httpTarget.getHost().trim();
                int p = this.httpTarget.getPort();
                portLabel = p > 0 ? String.valueOf(p) : "";
                protocol = this.httpTarget.isHttps() ? "HTTPS" : "HTTP";
            }
            method = safeHttpString(() -> request.method(), "");
            url = safeHttpString(() -> request.url(), "");
            path = safeHttpString(() -> request.path(), "");
        } else {
            host = this.httpTarget.getHost() == null ? "" : this.httpTarget.getHost().trim();
            int p = this.httpTarget.getPort();
            portLabel = p > 0 ? String.valueOf(p) : "";
        }
        int portNum = -1;
        try {
            portNum = Integer.parseInt(portLabel);
        } catch (NumberFormatException ignored) {
        }
        if (portNum <= 0) {
            portNum = "HTTPS".equals(protocol) ? 443 : 80;
        }
        String scheme = "HTTPS".equals(protocol) ? "https" : "http";
        return new HttpTargetSnapshot(
                scheme,
                host,
                portNum,
                this.httpTarget.isSniEnabled(),
                method,
                url,
                path);
    }

    /** Live editor request for agent tab discovery (may differ from last-applied target). */
    public HttpRequest getLiveRequestFromEditor() {
        return this.requestEditor != null ? this.requestEditor.getRequest() : null;
    }

    /** Repeater tree node id for this tab (AI tools / labels). */
    public int getRequestNodeId() {
        return this.node.getId();
    }

    public AgentToolContext buildAgentToolContextForAi() {
        RequestHistory h = this.node.getHistory();
        int cur = h.getCurrentIndex();
        List<AgentToolContext.HistoryEntryInfo> infos = new ArrayList<>();
        for (int i = 0; i < h.size(); i++) {
            HistoryEntry e = h.getEntry(i);
            infos.add(
                    new AgentToolContext.HistoryEntryInfo(
                            i,
                            e.getTime() != null ? e.getTime().toString() : "",
                            e.getTargetLabel() != null ? e.getTargetLabel() : ""));
        }
        return new AgentToolContext(
                buildTargetSnapshotForAi(),
                this.node.getId(),
                cur,
                infos,
                idx -> resolveRequestForHistoryIndex(h, cur, idx),
                idx -> resolveResponseForHistoryIndex(h, cur, idx),
                this::setRequest,
                this::sendCurrentHttpRequestBlocking);
    }

    /**
     * EDT. Applies host/target and optionally syncs {@code Content-Length} to the body, returning the request that
     * will actually be sent and stored on the node. The request editor document is left untouched so the user's
     * undo history is preserved across sends; the content-length/target adjustments live only on the outgoing
     * snapshot.
     */
    private HttpRequest prepareAndCommitRequestForSend() {
        HttpRequest r = this.requestEditor.getRequest();
        r = this.httpTarget.applyToRequest(r);
        if (this.updateContentLengthCheckBox.isSelected()) {
            r = RequestContentLength.syncContentLengthToBody(r);
        }
        this.node.setRequest(r);
        return r;
    }

    /**
     * Sends the live editor request (target applied), then updates the response editor and send history on the EDT,
     * matching the Send button. Blocks until the HTTP exchange finishes. Call from a background thread; uses {@link
     * SwingUtilities#invokeAndWait} for UI segments.
     *
     * @return HTTP status code of the response
     */
    private int sendCurrentHttpRequestBlocking() throws Exception {
        if (Treepeater.api == null) {
            throw new IllegalStateException("Burp API unavailable");
        }
        final HttpRequest[] prepared = new HttpRequest[1];
        final String[] targetLabel = new String[1];
        SwingUtilities.invokeAndWait(
                () -> {
                    prepared[0] = this.prepareAndCommitRequestForSend();
                    targetLabel[0] = this.httpTarget.statusLineLabel();
                });

        RequestOptions options = RequestOptions.requestOptions();
        if (this.httpTarget.isSniEnabled() && this.httpTarget.isHttps()) {
            String host = this.httpTarget.getHost();
            if (host != null && !host.isBlank()) {
                options = options.withServerNameIndicator(host.trim());
            }
        }

        HttpRequestResponse rr = Treepeater.api.http().sendRequest(prepared[0], options);
        HttpResponse response = rr.response();
        if (response == null) {
            throw new IllegalStateException("no HTTP response");
        }
        int status = Short.toUnsignedInt(response.statusCode());
        LocalDateTime time = LocalDateTime.now();
        HttpRequest snapshot = prepared[0];
        SwingUtilities.invokeAndWait(
                () -> {
                    this.setResponse(response);
                    this.addToHistory(snapshot, response, time, targetLabel[0]);
                });
        return status;
    }

    private HttpRequest resolveRequestForHistoryIndex(RequestHistory h, int currentIndex, int index) {
        if (index < 0 || index >= h.size()) {
            return null;
        }
        if (index == currentIndex) {
            return this.requestEditor.getRequest();
        }
        return h.getEntry(index).getRequest();
    }

    private HttpResponse resolveResponseForHistoryIndex(RequestHistory h, int currentIndex, int index) {
        if (index < 0 || index >= h.size()) {
            return null;
        }
        HttpResponse fromEntry = h.getEntry(index).getResponse();
        if (fromEntry != null) {
            return fromEntry;
        }
        if (index == currentIndex) {
            return this.responseEditor.getResponse();
        }
        return null;
    }

    private static HttpService safeHttpService(HttpRequest request) {
        try {
            return request.httpService();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String safeHttpString(java.util.function.Supplier<String> supplier, String onFailure) {
        try {
            String s = supplier.get();
            return (s == null || s.isBlank()) ? onFailure : s;
        } catch (Exception ignored) {
            return onFailure;
        }
    }

    private void openEditTargetDialog() {
        EditTargetDialogContent content = new EditTargetDialogContent(
                this.httpTarget.getHost(),
                this.httpTarget.getPort(),
                this.httpTarget.isHttps(),
                this.httpTarget.isSniEnabled());

        int result = JOptionPane.showConfirmDialog(
                this,
                content,
                "Edit target",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        TargetSettings settings = content.getSettings();

        this.httpTarget.applyFromDialog(settings);

        HttpRequest current = this.requestEditor.getRequest();
        HttpRequest updated = this.httpTarget.applyToRequest(current);
        if (updated != null) {
            this.requestEditor.setRequest(updated);
            this.node.setRequest(updated);
        }
        this.refreshTargetLabel();
        this.notifyRequestChanged();
    }
}
