package treepeater.requestResponse;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.tree.TreePath;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import treepeater.ai.HttpTargetSnapshot;
import treepeater.Treepeater;
import treepeater.TreepeaterModel;
import treepeater.components.CustomButton;
import treepeater.icons.DoubleArrowLeftIcon;
import treepeater.icons.DoubleArrowRightIcon;
import treepeater.requestResponse.toolbar.RequestResponseToolbar;
import treepeater.requestResponse.toolbar.RequestResponseToolbarListener;
import treepeater.settings.TreepeaterSettings;
import treepeater.tree.RequestTree;
import treepeater.tree.RequestTreeNode;
import treepeater.tree.CustomTreeCellEditor.ProgrammaticEdit;

public class RequestResponsePanel extends JPanel implements RequestResponseToolbarListener {

    /** When the expand panel is open, the divider cannot shrink it below this width (button closes only). */
    private static final int EXPAND_PANEL_MIN_OPEN_WIDTH = 120;

    /** Divider thickness when the expand strip is open; 0 when closed so nothing can be dragged. */
    private static int expandSplitDividerSizeWhenOpen() {
        int s = UIManager.getInt("SplitPane.dividerSize");
        return s > 0 ? s : 8;
    }

    private final TreepeaterModel model;
    private final RequestTree tree;
    private RequestTreeNode node;

    private HttpRequestEditor requestEditor;
    private HttpResponseEditor responseEditor;

    private CustomButton sendButton;
    private JButton cancelButton;
    private JPanel topBar;
    private JPanel topBarWrapper;

    private JSplitPane splitPane;
    private JPanel mainContent;
    private RequestResponseToolbar sideToolbar;

    private final List<RequestResponseChangeListener> requestResponseChangeListeners = new CopyOnWriteArrayList<>();

    private JSplitPane expandSplitPane;
    private JPanel expandPanel;
    private boolean expandPanelOpen;
    /**
     * Proportional width of the left component (request/response editors) in {@link #expandSplitPane} when the expand
     * strip is open. The expand panel is on the right, so e.g. {@code 0.78} leaves ~22% for the expand strip.
     */
    private double expandSplitEditorWidthFraction = 0.78;

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
                this.requestEditor,
                this.node,
                this.httpTarget,
                this.sendButton,
                this.cancelButton,
                (snapshot, response, time, label) -> {
                    this.setResponse(response);
                    this.addToHistory(snapshot, response, time, label);
                }).registerActions();

        this.splitPane.setLeftComponent(RequestResponsePanelUi.makeHeaderPanel("Request", this.requestEditor.uiComponent()));
        this.splitPane.setRightComponent(RequestResponsePanelUi.makeHeaderPanel("Response", this.responseEditor.uiComponent()));

        this.splitPane.setDividerLocation(0.5);
        this.splitPane.setResizeWeight(0.5);
;
        this.sideToolbar = new RequestResponseToolbar(this.node, this::buildTargetSnapshotForAi);
        this.addRequestResponseChangeListener(this.sideToolbar.getInfoToolbarTab());
        this.sideToolbar.addToolbarListener(this);
        this.expandPanel = this.sideToolbar.getToolbarPanel();

        this.notifyRequestChanged();
        this.notifyResponseChanged();

        this.expandSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, this.splitPane, this.expandPanel);
        Treepeater.api.userInterface().applyThemeToComponent(this.expandSplitPane);
        this.expandSplitPane.setResizeWeight(1.0);
        this.expandSplitPane.setOneTouchExpandable(false);
        this.expandSplitPane.setContinuousLayout(true);
        this.syncExpandSplitInteraction();

        this.mainContent = new JPanel(new BorderLayout());
        this.mainContent.add(this.expandSplitPane, BorderLayout.CENTER);
        this.mainContent.add(this.sideToolbar, BorderLayout.LINE_END);
        Treepeater.api.userInterface().applyThemeToComponent(this.mainContent);

        this.installExpandSplitInitiallyCollapsed();

        this.add(this.mainContent, BorderLayout.CENTER);

        this.hotkeyHandler = new HotkeyHandler();
        this.populateHotkeyActions();
        RequestResponseHotkeyInstaller.install(this, this.hotkeyHandler, this.hotkeyActions, this.hotkeyHandlerRegistered);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        this.applyThemeLocalStyles();
    }

    public void onToolbarOpen() {
        this.toggleExpandPanel();
    }

    public void onToolbarClose() {
        this.toggleExpandPanel();
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
        if (this.mainContent != null && Treepeater.api != null) {
            Treepeater.api.userInterface().applyThemeToComponent(this.mainContent);
        }
        if (this.expandSplitPane != null && Treepeater.api != null) {
            Treepeater.api.userInterface().applyThemeToComponent(this.expandSplitPane);
        }
        if (this.expandPanel != null && Treepeater.api != null) {
            Treepeater.api.userInterface().applyThemeToComponent(this.expandPanel);
        }
        if (this.sideToolbar != null && Treepeater.api != null) {
            Treepeater.api.userInterface().applyThemeToComponent(this.sideToolbar);
        }
        if (this.sideToolbar != null) {
            this.sideToolbar.applyLocalTheme();
        }
        SwingUtilities.invokeLater(this::applyExpandDividerInteractionState);
    }

    private void installExpandSplitInitiallyCollapsed() {
        this.expandSplitPane.addComponentListener(
                new ComponentAdapter() {
                    private boolean laidOut;

                    @Override
                    public void componentResized(ComponentEvent e) {
                        if (this.laidOut || RequestResponsePanel.this.expandSplitPane.getWidth() < 32) {
                            return;
                        }
                        this.laidOut = true;
                        RequestResponsePanel.this.expandSplitPane.setDividerLocation(1.0d);
                        RequestResponsePanel.this.syncExpandSplitInteraction();
                        RequestResponsePanel.this.expandSplitPane.removeComponentListener(this);
                    }
                });
    }

    private void syncExpandSplitInteraction() {
        this.applyExpandPanelMinSizeForState();
        this.applyExpandDividerInteractionState();
    }

    private void applyExpandPanelMinSizeForState() {
        if (this.expandPanel == null) {
            return;
        }
        int minW = this.expandPanelOpen ? EXPAND_PANEL_MIN_OPEN_WIDTH : 0;
        this.expandPanel.setMinimumSize(new Dimension(minW, 0));
        if (this.expandSplitPane != null) {
            this.expandSplitPane.revalidate();
        }
    }

    private void applyExpandDividerInteractionState() {
        if (this.expandSplitPane == null) {
            return;
        }
        if (this.expandPanelOpen) {
            this.expandSplitPane.setDividerSize(expandSplitDividerSizeWhenOpen());
        } else {
            this.expandSplitPane.setDividerSize(0);
        }
        if (!(this.expandSplitPane.getUI() instanceof BasicSplitPaneUI)) {
            return;
        }
        BasicSplitPaneDivider divider = ((BasicSplitPaneUI) this.expandSplitPane.getUI()).getDivider();
        if (divider == null) {
            return;
        }
        divider.setEnabled(this.expandPanelOpen);
    }

    private void toggleExpandPanel() {
        if (this.expandPanelOpen) {
            int w = this.expandSplitPane.getWidth();
            if (w > 0) {
                double editorFrac = this.expandSplitPane.getDividerLocation() / (double) w;
                this.expandSplitEditorWidthFraction = Math.max(0.35d, Math.min(0.96d, editorFrac));
            }
            this.sideToolbar.getExpandButton().setIcon(new DoubleArrowLeftIcon().withSize(24, 24).withColor(UIManager.getColor("Label.foreground")));
            this.expandPanelOpen = false;
            this.expandSplitPane.setResizeWeight(1.0);
            this.syncExpandSplitInteraction();
            SwingUtilities.invokeLater(() -> this.expandSplitPane.setDividerLocation(1.0d));
        } else {
            this.sideToolbar.getExpandButton().setIcon(new DoubleArrowRightIcon().withSize(24, 24).withColor(UIManager.getColor("Label.foreground")));
            this.expandPanelOpen = true;
            this.expandSplitPane.setResizeWeight(0.78);
            this.syncExpandSplitInteraction();
            SwingUtilities.invokeLater(() -> this.expandSplitPane.setDividerLocation(this.expandSplitEditorWidthFraction));
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

    private void notifyRequestChanged() {
        LocalDateTime received = this.currentHistoryResponseTime();
        HttpRequest request = this.requestEditor.getRequest();
        HttpResponse response = this.responseEditor.getResponse();
        for (RequestResponseChangeListener listener : this.requestResponseChangeListeners) {
            listener.onRequestChanged(request, response, received);
        }
    }

    private void notifyResponseChanged() {
        LocalDateTime received = this.currentHistoryResponseTime();
        HttpRequest request = this.requestEditor.getRequest();
        HttpResponse response = this.responseEditor.getResponse();
        for (RequestResponseChangeListener listener : this.requestResponseChangeListeners) {
            listener.onResponseChanged(request, response, received);
        }
    }

    /**
     * Time associated with the current history entry (typically when that response was received or recorded).
     */
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

    /**
     * Current target + request line for the AI tab (aligned with the Info toolbar fields).
     */
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
