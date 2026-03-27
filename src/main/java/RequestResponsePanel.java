
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.RequestOptions;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;

public class RequestResponsePanel extends JPanel {
    private RequestTreeNode node;

    private HttpRequestEditor requestEditor;
    private HttpResponseEditor responseEditor;

    private JButton sendButton;
    private JButton cancelButton;
    private JPanel topBar;

    private JSplitPane splitPane;

    private final List<HistoryEntry> history;
    private int currentHistoryIndex;

    private JButton historyBackButton;
    private JButton historyBackDropButton;
    private JButton historyForwardButton;
    private JButton historyForwardDropButton;
    private JPanel historyBackSplitButton;
    private JPanel historyForwardSplitButton;

    private JLabel targetValueLabel;
    private JButton editTargetButton;

    private String targetHost;
    private int targetPort;
    private boolean targetHttps;
    private boolean sniEnabled;

    private SwingWorker<HttpResponse, Void> activeWorker;
    private volatile boolean suppressHistoryApply;

    public RequestResponsePanel(RequestTreeNode node) {
        super(new BorderLayout());
        this.node = node;
        this.history = new ArrayList<>();
        this.currentHistoryIndex = -1;

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

        this.historyBackSplitButton = buildHistorySplitButton(this.historyBackButton, this.historyBackDropButton);
        this.historyForwardSplitButton = buildHistorySplitButton(this.historyForwardButton, this.historyForwardDropButton);

        this.historyBackButton.addActionListener(e -> navigateHistory(-1));
        this.historyForwardButton.addActionListener(e -> navigateHistory(1));
        this.historyBackDropButton.addActionListener(e -> showHistoryMenu(-1, this.historyBackDropButton));
        this.historyForwardDropButton.addActionListener(e -> showHistoryMenu(1, this.historyForwardDropButton));

        this.sendButton = new CustomButton("Send", Color.WHITE, Color.ORANGE, Color.BLACK);
        this.cancelButton = new JButton();

        initTargetStateFromRequest(this.node.getRequest());
        refreshTargetLabel();

        // Put first request and response into the history
        this.history.add(new HistoryEntry(0, LocalDateTime.now(), node.getRequest().httpService().host(), node.getRequest(), node.getResponse()));
        refreshHistoryNavState();

        this.sendButton.setAction(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                RequestResponsePanel.this.sendButton.setEnabled(false);
                RequestResponsePanel.this.cancelButton.setEnabled(true);

                HttpRequest requestToSend = RequestResponsePanel.this.requestEditor.getRequest();
                requestToSend = withTargetFromState(requestToSend);
                RequestResponsePanel.this.requestEditor.setRequest(requestToSend);
                RequestResponsePanel.this.node.setRequest(requestToSend);

                final HttpRequest historyRequestSnapshot = requestToSend;
                final String historyTargetLabel = targetLabelFromState();
                final LocalDateTime historyTime = LocalDateTime.now();

                SwingWorker<HttpResponse, Void> worker = new SwingWorker<>() {

                    @Override
                    protected HttpResponse doInBackground() throws Exception {
                        RequestOptions options = RequestOptions.requestOptions();
                        if (RequestResponsePanel.this.sniEnabled && RequestResponsePanel.this.targetHttps && RequestResponsePanel.this.targetHost != null
                                && !RequestResponsePanel.this.targetHost.isBlank()) {
                            options = options.withServerNameIndicator(RequestResponsePanel.this.targetHost.trim());
                        }
                        HttpRequestResponse response = Treepeater.api.http().sendRequest(historyRequestSnapshot, options);
                        return response.response();
                    }

                    protected void done() {
                        try {
                            if (isCancelled()) {
                                return;
                            }
                            HttpResponse r = get();
                            RequestResponsePanel.this.setResponse(r);
                            RequestResponsePanel.this.addToHistory(historyRequestSnapshot, r, historyTime, historyTargetLabel);
                        } catch (Exception e) {
                            Treepeater.api.logging().logToError(e);
                        } finally {
                            RequestResponsePanel.this.sendButton.setEnabled(true);
                            RequestResponsePanel.this.cancelButton.setEnabled(false);
                            if (Objects.equals(RequestResponsePanel.this.activeWorker, this)) {
                                RequestResponsePanel.this.activeWorker = null;
                            }
                        }
                    }
                };

                RequestResponsePanel.this.activeWorker = worker;
                worker.execute();
            }

            
        });
        //styleAsBurpSendButton(this.sendButton);

        this.cancelButton.setAction(new AbstractAction("Cancel") {
            @Override
            public void actionPerformed(ActionEvent e) {
                SwingWorker<HttpResponse, Void> w = RequestResponsePanel.this.activeWorker;
                if (w != null) {
                    w.cancel(true);
                }
                RequestResponsePanel.this.activeWorker = null;
                RequestResponsePanel.this.sendButton.setEnabled(true);
                RequestResponsePanel.this.cancelButton.setEnabled(false);
            }
        });
        this.cancelButton.setEnabled(false);

        Border labelPadding = BorderFactory.createEmptyBorder(0, 0, 0, 8);

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
        this.add(this.topBar, BorderLayout.PAGE_START);

        this.splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);

        this.requestEditor = Treepeater.api.userInterface().createHttpRequestEditor();
        this.requestEditor.setRequest(this.node.getRequest());

        this.responseEditor = Treepeater.api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);
        this.responseEditor.setResponse(this.node.getResponse());

        this.splitPane.setLeftComponent(this.makeHeaderPanel("Request", this.requestEditor.uiComponent()));
        this.splitPane.setRightComponent(this.makeHeaderPanel("Response", this.responseEditor.uiComponent()));

        this.splitPane.setDividerLocation(0.5);
        this.splitPane.setResizeWeight(0.5);

        this.add(splitPane, BorderLayout.CENTER);
    }


    public void setRequest(HttpRequest request) {
        this.requestEditor.setRequest(request);
        this.node.setRequest(request);
        initTargetStateFromRequest(request);
        refreshTargetLabel();
    }

    private void setResponse(HttpResponse response) {
        this.responseEditor.setResponse(response);
        this.node.setResponse(response);
    }

    private JPanel makeHeaderPanel(String header, Component component) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        Border labelPadding = BorderFactory.createEmptyBorder(16, 8, 0, 0);

        JLabel label = new JLabel(header);
        label.setBorder(labelPadding);
        
        label.setFont(label.getFont().deriveFont(Font.BOLD, 16f));

        panel.add(label);
        panel.add(component);

        return panel;
    }

    private void addToHistory(HttpRequest request, HttpResponse response, LocalDateTime time, String targetLabel) {
        int index = this.history.size() + 1;
        HistoryEntry entry = new HistoryEntry(index, time, targetLabel, request, response);
        this.history.add(entry);
        this.currentHistoryIndex = this.history.size() - 1;
        refreshHistoryNavState();
    }

    private void initTargetStateFromRequest(HttpRequest request) {
        if (request == null) {
            return;
        }
        HttpService service;
        try {
            service = request.httpService();
        } catch (Exception ignored) {
            return;
        }
        if (service == null) {
            return;
        }

        this.targetHost = service.host();
        this.targetPort = service.port();
        this.targetHttps = service.secure();
        if (this.targetPort <= 0) {
            this.targetPort = this.targetHttps ? 443 : 80;
        }
        // Burp defaults SNI on for HTTPS; mimic that.
        if (!this.targetHttps) {
            this.sniEnabled = false;
        } else if (this.targetHost != null && !this.targetHost.isBlank()) {
            this.sniEnabled = true;
        }
    }

    private HttpRequest withTargetFromState(HttpRequest request) {
        if (request == null) {
            return null;
        }
        String host = (this.targetHost == null) ? "" : this.targetHost.trim();
        if (host.isEmpty()) {
            return request;
        }
        int port = this.targetPort;
        boolean secure = this.targetHttps;
        try {
            HttpService service = HttpService.httpService(host, port, secure);
            return request.withService(service);
        } catch (IllegalArgumentException ex) {
            return request;
        }
    }

    private String targetLabelFromState() {
        String host = (this.targetHost == null) ? "" : this.targetHost.trim();
        if (host.isEmpty()) {
            return "";
        }
        String scheme = this.targetHttps ? "https" : "http";
        return scheme + "://" + host + ":" + this.targetPort + (this.targetHttps ? (this.sniEnabled ? " (SNI)" : " (no SNI)") : "");
    }

    private void refreshTargetLabel() {
        String label = targetLabelFromState();
        if (label.isEmpty()) {
            label = "Target: (not set)";
        } else {
            label = "Target: " + label;
        }
        this.targetValueLabel.setText(label);
    }

    private void refreshHistoryNavState() {
        boolean hasHistory = !this.history.isEmpty();
        boolean canBack = hasHistory && this.currentHistoryIndex > 0;
        boolean canForward = hasHistory && this.currentHistoryIndex >= 0 && this.currentHistoryIndex < (this.history.size() - 1);

        this.historyBackButton.setEnabled(canBack);
        this.historyBackDropButton.setEnabled(canBack);
        this.historyForwardButton.setEnabled(canForward);
        this.historyForwardDropButton.setEnabled(canForward);
    }

    private void navigateHistory(int delta) {
        if (this.history.isEmpty() || this.currentHistoryIndex < 0) {
            return;
        }
        int next = this.currentHistoryIndex + delta;
        if (next < 0 || next >= this.history.size()) {
            return;
        }
        applyHistoryIndex(next);
    }

    private void showHistoryMenu(int direction, JButton anchor) {
        if (this.history.isEmpty() || this.currentHistoryIndex < 0) {
            return;
        }

        JPopupMenu menu = new JPopupMenu();

        if (direction < 0) {
            for (int i = this.currentHistoryIndex - 1; i >= 0; i--) {
                HistoryEntry entry = this.history.get(i);
                JMenuItem item = new JMenuItem(entry.toString());
                final int idx = i;
                item.addActionListener(e -> applyHistoryIndex(idx));
                menu.add(item);
            }
        } else {
            for (int i = this.currentHistoryIndex + 1; i < this.history.size(); i++) {
                HistoryEntry entry = this.history.get(i);
                JMenuItem item = new JMenuItem(entry.toString());
                final int idx = i;
                item.addActionListener(e -> applyHistoryIndex(idx));
                menu.add(item);
            }
        }

        if (menu.getComponentCount() == 0) {
            return;
        }

        menu.show(anchor, 0, anchor.getHeight());
    }

    private void applyHistoryIndex(int index) {
        if (index < 0 || index >= this.history.size()) {
            return;
        }

        HistoryEntry selected = this.history.get(index);
        if (selected == null || selected.request == null) {
            return;
        }

        suppressHistoryApply = true;
        try {
            this.currentHistoryIndex = index;
            this.requestEditor.setRequest(selected.request);
            this.node.setRequest(selected.request);
            if (selected.response != null) {
                this.setResponse(selected.response);
            }
            initTargetStateFromRequest(selected.request);
            refreshTargetLabel();
            refreshHistoryNavState();
        } finally {
            suppressHistoryApply = false;
        }
    }

    private void openEditTargetDialog() {
        EditTargetDialogContent content = new EditTargetDialogContent(this.targetHost, this.targetPort, this.targetHttps, this.sniEnabled);
        
        int result = JOptionPane.showConfirmDialog(
                this,
                content,
                "Edit target",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        TargetSettings settings = content.getSettings();

        this.targetHost = settings.host();
        this.targetPort = settings.port();
        this.targetHttps = settings.https();
        this.sniEnabled = settings.sniEnabled();

        HttpRequest current = this.requestEditor.getRequest();
        HttpRequest updated = withTargetFromState(current);
        if (updated != null) {
            this.requestEditor.setRequest(updated);
            this.node.setRequest(updated);
        }
        refreshTargetLabel();
    }

    private static JPanel buildHistorySplitButton(JButton navButton, JButton dropButton) {
        Color borderColor = uiBorderColor();
        Color dividerColor = borderColor;
        Color hoverColor = uiHoverColor();

        styleAsFlatButton(navButton);
        styleAsFlatButton(dropButton);

        // Make them look like a single split-button control.
        navButton.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        dropButton.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        dropButton.setFont(dropButton.getFont().deriveFont(dropButton.getFont().getSize2D() - 1f));

        installHoverBackground(navButton, hoverColor);
        installHoverBackground(dropButton, hoverColor);

        SplitButtonPanel panel = new SplitButtonPanel(borderColor, dividerColor);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

        panel.add(navButton);
        panel.add(dropButton);

        return panel;
    }

    private static void styleAsFlatButton(JButton button) {
        button.setFocusPainted(false);
        button.setContentAreaFilled(true);
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setRolloverEnabled(true);
        Color bg =
                UIManager.getColor("Panel.background") != null ? UIManager.getColor("Panel.background") :
                UIManager.getColor("control");
        if (bg != null) {
            button.setBackground(bg);
        }
    }

    private static Color uiBorderColor() {
        Color c =
                UIManager.getColor("Separator.foreground") != null ? UIManager.getColor("Separator.foreground") :
                UIManager.getColor("Component.borderColor") != null ? UIManager.getColor("Component.borderColor") :
                UIManager.getColor("controlShadow");
        if (c != null) {
            return c;
        }
        return new Color(0, 0, 0, 80);
    }
    

    private static Color uiHoverColor() {
        Color c =
                UIManager.getColor("Button.hoverBackground") != null ? UIManager.getColor("Button.hoverBackground") :
                UIManager.getColor("Button.highlight") != null ? UIManager.getColor("Button.highlight") :
                UIManager.getColor("Table.selectionBackground");
        if (c != null) {
            return new Color(c.getRed(), c.getGreen(), c.getBlue(), 70);
        }
        return new Color(0, 0, 0, 18);
    }

    private static void installHoverBackground(JButton button, Color hoverBg) {
        final Color normalBg = button.getBackground();
        button.getModel().addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                Color next;
                if (!button.isEnabled()) {
                    next = normalBg;
                } else {
                    next = button.getModel().isRollover() ? hoverBg : normalBg;
                }
                if (!Objects.equals(button.getBackground(), next)) {
                    button.setBackground(next);
                }

                // Ensure the split-button outline/divider gets repainted too.
                button.repaint();
                Component parent = button.getParent();
                if (parent != null) {
                    parent.repaint();
                }
            }
        });
    }
}
