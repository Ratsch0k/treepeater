package treepeater.requestResponse;

import java.awt.BorderLayout;
import java.awt.Font;
import java.time.LocalDateTime;
import java.util.HashMap;
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
import javax.swing.tree.TreePath;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
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
    private RequestTreeNode node;

    private HttpRequestEditor requestEditor;
    private HttpResponseEditor responseEditor;

    private CustomButton sendButton;
    private JButton cancelButton;
    private JPanel topBar;
    private JPanel topBarWrapper;

    private JSplitPane splitPane;

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

        this.add(splitPane, BorderLayout.CENTER);

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
    }

    private void setResponse(HttpResponse response) {
        this.responseEditor.setResponse(response);
        this.node.setResponse(response);
        Treepeater.saveState();
    }

    private void addToHistory(HttpRequest request, HttpResponse response, LocalDateTime time, String targetLabel) {
        RequestHistory h = this.node.getHistory();
        h.addEntry(targetLabel, request, response);
        this.historyNavigator.refreshNavState();
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
    }
}
