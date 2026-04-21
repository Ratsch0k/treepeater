package treepeater.requestResponse.toolbar.ai;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.InputMap;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;

import com.formdev.flatlaf.FlatClientProperties;

import treepeater.Treepeater;
import treepeater.ai.AgentSystemPrompt;
import treepeater.ai.AiModelOption;
import treepeater.ai.MarkdownRenderer;
import treepeater.ai.ChatErrors;
import treepeater.ai.ChatMessage;
import treepeater.ai.ChatRole;
import treepeater.ai.ChatStreamMessage;
import treepeater.ai.ChatStreamSession;
import treepeater.ai.ChatTooling;
import treepeater.components.RoundedPanel;
import treepeater.components.StyledButton;
import treepeater.settings.TreepeaterSettings;

/**
 * One nested “agent” chat tab: transcript, draft input, model choice, and any in-flight request for this
 * tab only.
 */
public final class AIAgentChatPanel extends JPanel {
    private static final Object TOOL_USAGE_BORDER_MARK = new Object();

    private static final int INPUT_AREA_MIN_ROWS = 1;
    private static final int INPUT_AREA_MAX_ROWS = 14;

    private final AIChatHost host;

    private final AITranscriptListPanel transcriptList;
    private final JScrollPane transcriptScroll;
    private final Component transcriptBottomGlue = leftAlignedVerticalGlue();
    private final JTextArea inputArea;
    private JScrollPane inputScroll;
    private RoundedPanel inputPanel;
    private final StyledButton sendButton;
    private final JComboBox<AiModelOption> modelCombo;
    private final List<ChatMessage> conversation = new ArrayList<>();
    private final List<AssistantStrip> renderedStrips = new ArrayList<>();
    private final AtomicReference<SwingWorker<List<ChatMessage>, Void>> activeChatWorker =
            new AtomicReference<>();

    private final AtomicReference<AssistantStrip> transcriptActiveAssistantStrip = new AtomicReference<>();

    private final AtomicReference<ChatStreamSession> activeSession = new AtomicReference<>();

    public AIAgentChatPanel(AIChatHost host) {
        super(new BorderLayout());
        this.host = host;
        setOpaque(false);

        this.transcriptList = new AITranscriptListPanel();
        this.transcriptScroll = new JScrollPane(this.transcriptList);
        this.transcriptScroll.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        this.transcriptScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        this.transcriptScroll.getVerticalScrollBar().setUnitIncrement(16);
        this.transcriptScroll.setPreferredSize(new Dimension(0, 200));

        this.inputArea = new JTextArea(1, 0);
        this.inputArea.setLineWrap(true);
        this.inputArea.setWrapStyleWord(true);
        this.inputArea.putClientProperty(FlatClientProperties.STYLE, "background: $Colors.ui.background.1;");

        this.sendButton = new StyledButton("Send");
        this.sendButton.setStyle(StyledButton.Style.AI);
        this.sendButton.setPreferredSize(new Dimension(80, 22));

        this.modelCombo =
                new JComboBox<>(new DefaultComboBoxModel<>(new Vector<>(AiModelOption.defaultChoices())));
        this.modelCombo.setSelectedIndex(0);
        this.modelCombo.setMaximumRowCount(12);

        this.sendButton.addActionListener(e -> AIAgentChatPanel.this.startSend());

        InputMap inputMap = this.inputArea.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = this.inputArea.getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "aiSendPrompt");
        actionMap.put(
                "aiSendPrompt",
                new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        AIAgentChatPanel.this.startSend();
                    }
                });

        this.inputPanel = new RoundedPanel();
        this.inputPanel.setLayout(new GridBagLayout());
        this.inputPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 0));
        applyInputPanelTheme(this.inputPanel);

        this.inputScroll = new JScrollPane(this.inputArea);
        this.inputScroll.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        this.inputScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        this.inputScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        this.inputArea
                .getDocument()
                .addDocumentListener(
                        new DocumentListener() {
                            @Override
                            public void insertUpdate(DocumentEvent e) {
                                AIAgentChatPanel.this.scheduleAdjustInputAreaHeight();
                            }

                            @Override
                            public void removeUpdate(DocumentEvent e) {
                                AIAgentChatPanel.this.scheduleAdjustInputAreaHeight();
                            }

                            @Override
                            public void changedUpdate(DocumentEvent e) {
                                AIAgentChatPanel.this.scheduleAdjustInputAreaHeight();
                            }
                        });
        this.inputScroll
                .getViewport()
                .addComponentListener(
                        new ComponentAdapter() {
                            @Override
                            public void componentResized(ComponentEvent e) {
                                AIAgentChatPanel.this.scheduleAdjustInputAreaHeight();
                            }
                        });

        this.modelCombo.setPreferredSize(new Dimension(140, this.modelCombo.getPreferredSize().height));

        JPanel sendControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        sendControls.setOpaque(false);
        sendControls.add(this.modelCombo);
        sendControls.add(this.sendButton);

        GridBagConstraints inputGbc = new GridBagConstraints();
        inputGbc.gridx = 0;
        inputGbc.weightx = 1;
        inputGbc.fill = GridBagConstraints.HORIZONTAL;

        inputGbc.gridy = 0;
        inputGbc.weighty = 1;
        inputGbc.insets = new Insets(0, 0, 0, 0);
        inputGbc.anchor = GridBagConstraints.FIRST_LINE_START;
        this.inputPanel.add(Box.createVerticalGlue(), inputGbc);

        inputGbc.gridy = 1;
        inputGbc.weighty = 0;
        inputGbc.insets = new Insets(0, 0, 6, 8);
        inputGbc.anchor = GridBagConstraints.SOUTH;
        this.inputPanel.add(this.inputScroll, inputGbc);

        inputGbc.gridy = 2;
        inputGbc.insets = new Insets(0, 0, 0, 0);
        inputGbc.anchor = GridBagConstraints.FIRST_LINE_END;
        this.inputPanel.add(sendControls, inputGbc);

        SwingUtilities.invokeLater(this::adjustInputAreaHeight);

        JPanel inputRow = new JPanel(new BorderLayout());
        inputRow.setOpaque(false);
        JPanel inputPanelWrapper = new JPanel(new BorderLayout());
        inputPanelWrapper.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        inputPanelWrapper.add(this.inputPanel, BorderLayout.CENTER);
        inputRow.add(inputPanelWrapper, BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(this.transcriptScroll, BorderLayout.CENTER);
        body.add(inputRow, BorderLayout.SOUTH);

        add(body, BorderLayout.CENTER);
    }

    void applyInputPanelTheme() {
        if (this.inputPanel == null) {
            return;
        }
        applyInputPanelTheme(this.inputPanel);
    }

    void adjustInputAreaHeight() {
        if (this.inputArea == null || this.inputScroll == null) {
            return;
        }
        JViewport vp = this.inputScroll.getViewport();
        int textWidth = vp.getExtentSize().width;
        if (textWidth <= 0) {
            return;
        }

        FontMetrics fm = this.inputArea.getFontMetrics(this.inputArea.getFont());
        int lineH = fm.getHeight();
        int minH = lineH * INPUT_AREA_MIN_ROWS;
        int maxH = lineH * INPUT_AREA_MAX_ROWS;

        int contentH = this.inputArea.getPreferredSize().height;
        int h = Math.min(Math.max(contentH, minH), maxH);

        this.inputScroll.setPreferredSize(new Dimension(textWidth, h));
        this.inputScroll.revalidate();
        this.inputScroll.repaint();
        this.inputPanel.revalidate();
        this.inputPanel.repaint();
    }

    void refreshTranscriptThemes() {
        for (Component c : this.transcriptList.getComponents()) {
            this.refreshTranscriptRowTheme(c);
        }
        for (AssistantStrip strip : this.renderedStrips) {
            if (strip.body != null && strip.textAccumulator.length() > 0) {
                renderMarkdown(strip);
            }
        }
    }

    private void startSend() {
        if (Treepeater.api == null || this.modelCombo == null) {
            return;
        }
        AiModelOption choice = (AiModelOption) this.modelCombo.getSelectedItem();
        if (choice != null && choice.kind() == AiModelOption.Kind.BURP && !AIChatHost.isBurpAiEnabled()) {
            JOptionPane.showMessageDialog(
                    this.host.dialogParent(),
                    "Enable Burp's AI for this extension under Extensions (Use AI), or choose an Ollama model.",
                    "Burp AI unavailable",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (choice != null && choice.kind() == AiModelOption.Kind.ANTHROPIC) {
            String key = TreepeaterSettings.getInstance().getLlmAnthropicApiKey();
            if (key == null || key.isBlank()) {
                JOptionPane.showMessageDialog(
                        this.host.dialogParent(),
                        "Add your Anthropic API key under Extension settings for Treepeater (LLMs \u2192 Anthropic).",
                        "Anthropic API key required",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        }

        if (choice != null && choice.kind() == AiModelOption.Kind.OLLAMA) {
            String base = TreepeaterSettings.getInstance().getLlmOllamaBaseUrl();
            if (base == null || base.isBlank()) {
                JOptionPane.showMessageDialog(
                        this.host.dialogParent(),
                        "Set the Ollama base URL under Extension settings for Treepeater (LLMs \u2192 Ollama).",
                        "Ollama base URL required",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        }

        if (choice != null && choice.kind() == AiModelOption.Kind.OPENAI) {
            TreepeaterSettings s = TreepeaterSettings.getInstance();
            String endpoint = s.getLlmAzureOpenAiEndpoint();
            String key = s.getLlmAzureOpenAiApiKey();
            if (endpoint == null || endpoint.isBlank() || key == null || key.isBlank()) {
                JOptionPane.showMessageDialog(
                        this.host.dialogParent(),
                        "Add your Azure OpenAI / Foundry endpoint and API key under Extension settings for Treepeater "
                                + "(LLMs \u2192 Azure OpenAI / Foundry).",
                        "Azure OpenAI configuration required",
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        }

        String text = this.inputArea.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        SwingWorker<List<ChatMessage>, Void> running = this.activeChatWorker.get();
        if (running != null && !running.isDone()) {
            return;
        }

        this.inputArea.setText("");
        addMessageBubble("You", text);

        List<ChatMessage> messages = new ArrayList<>(this.conversation);
        messages.add(new ChatMessage(ChatRole.USER, text));
        AgentSystemPrompt.prependDefault(messages);

        this.sendButton.setEnabled(false);
        this.modelCombo.setEnabled(false);

        ChatTooling requestTooling = this.host.chatTooling();

        AssistantStrip firstStrip = createPlainAssistantStrip();
        this.transcriptActiveAssistantStrip.set(firstStrip);
        appendTranscriptRow(firstStrip.root);

        AtomicReference<ChatStreamSession> sessionRef = new AtomicReference<>();
        Consumer<ChatStreamMessage> outbound =
                m -> SwingUtilities.invokeLater(() -> handleStreamMessageOnEdt(m, sessionRef.get()));
        ChatStreamSession session = new ChatStreamSession(outbound);
        sessionRef.set(session);
        this.activeSession.set(session);

        SwingWorker<List<ChatMessage>, Void> worker = new SwingWorker<>() {
            private final List<ChatMessage> requestMessages = messages;

            @Override
            protected List<ChatMessage> doInBackground() throws Exception {
                try {
                    return AIAgentChatPanel.this
                            .host
                            .clientForSelectedModel(AIAgentChatPanel.this.modelCombo)
                            .streamChat(this.requestMessages, requestTooling, session);
                } finally {
                    session.close();
                    AIAgentChatPanel.this.activeSession.compareAndSet(session, null);
                }
            }

            @Override
            protected void done() {
                try {
                    if (isCancelled()) {
                        return;
                    }
                    List<ChatMessage> history = get();
                    AIAgentChatPanel.this.conversation.clear();
                    AIAgentChatPanel.this.conversation.addAll(AgentSystemPrompt.stripDefaultLeadingSystem(history));
                } catch (Exception ex) {
                    AIAgentChatPanel.this.host.logError(ex);
                    AIAgentChatPanel.this.addMessageBubble("Error", ChatErrors.formatUserMessage(ex));
                } finally {
                    AssistantStrip last = AIAgentChatPanel.this.transcriptActiveAssistantStrip.get();
                    if (last != null) {
                        removeAssistantWaitingIndicator(last);
                        flushMarkdownRender(last);
                    }
                    AIAgentChatPanel.this.transcriptActiveAssistantStrip.set(null);
                    AIAgentChatPanel.this.sendButton.setEnabled(true);
                    AIAgentChatPanel.this.modelCombo.setEnabled(true);
                    if (AIAgentChatPanel.this.activeChatWorker.get() == this) {
                        AIAgentChatPanel.this.activeChatWorker.set(null);
                    }
                }
            }
        };

        this.activeChatWorker.set(worker);
        worker.execute();
    }

    /** Stops any in-flight chat request; call before removing this panel from its tab. */
    void cancelInFlightChat() {
        ChatStreamSession session = this.activeSession.get();
        if (session != null) {
            session.close();
        }
        SwingWorker<List<ChatMessage>, Void> w = this.activeChatWorker.get();
        if (w != null && !w.isDone()) {
            w.cancel(true);
        }
    }

    /** EDT-side dispatch for outbound stream messages; translates {@link ChatStreamMessage} into UI updates. */
    private void handleStreamMessageOnEdt(ChatStreamMessage m, ChatStreamSession session) {
        SwingWorker<List<ChatMessage>, Void> w = this.activeChatWorker.get();
        if (w == null || w.isCancelled()) {
            if (m instanceof ChatStreamMessage.ToolApprovalRequest req && session != null) {
                session.postReply(new ChatStreamMessage.ToolApprovalResponse(req.toolCallId(), false));
            }
            return;
        }
        if (m instanceof ChatStreamMessage.AssistantDelta ad) {
            if (ad.text().isEmpty()) {
                return;
            }
            AssistantStrip strip = this.transcriptActiveAssistantStrip.get();
            if (strip == null) {
                return;
            }
            removeAssistantWaitingIndicator(strip);
            ensureAssistantBody(strip);
            appendAssistantReplyDelta(strip, ad.text());
            return;
        }
        if (m instanceof ChatStreamMessage.ToolApprovalRequest req) {
            if (session == null) {
                return;
            }
            AssistantStrip strip = this.transcriptActiveAssistantStrip.get();
            if (strip == null) {
                session.postReply(new ChatStreamMessage.ToolApprovalResponse(req.toolCallId(), false));
                return;
            }
            removeAssistantWaitingIndicator(strip);
            flushMarkdownRender(strip);
            if (strip.body == null) {
                removeAssistantStripRowFromTranscript(strip.root);
            }
            RoundedPanel toolCard =
                    buildToolApprovalCard(
                            req.humanDescription(),
                            approved ->
                                    session.postReply(
                                            new ChatStreamMessage.ToolApprovalResponse(
                                                    req.toolCallId(), approved)));
            appendTranscriptRow(toolCard);
            AssistantStrip nextStrip = createPlainAssistantStrip();
            this.transcriptActiveAssistantStrip.set(nextStrip);
            appendTranscriptRow(nextStrip.root);
            this.transcriptList.revalidate();
            this.transcriptList.repaint();
            scrollTranscriptToBottom();
        }
    }

    private void scheduleAdjustInputAreaHeight() {
        if (this.inputScroll == null) {
            return;
        }
        SwingUtilities.invokeLater(this::adjustInputAreaHeight);
    }

    private void addMessageBubble(String title, String body) {
        appendBubble(createMessageBubble(title, body).panel());
    }

    private void appendBubble(RoundedPanel bubble) {
        appendTranscriptRow(bubble);
    }

    /**
     * Drops a pending assistant row when a tool runs before any streamed text, so the tool card sits
     * directly under the user message without an empty strip.
     */
    private void removeAssistantStripRowFromTranscript(JPanel root) {
        this.transcriptList.remove(this.transcriptBottomGlue);
        int n = this.transcriptList.getComponentCount();
        int idx = -1;
        for (int i = 0; i < n; i++) {
            if (this.transcriptList.getComponent(i) == root) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            this.transcriptList.add(this.transcriptBottomGlue);
            return;
        }
        if (idx > 0) {
            Component prev = this.transcriptList.getComponent(idx - 1);
            if (isTranscriptInterRowStrut(prev)) {
                this.transcriptList.remove(idx - 1);
                idx--;
            }
        }
        this.transcriptList.remove(idx);
        this.transcriptList.add(this.transcriptBottomGlue);
        this.transcriptList.revalidate();
        this.transcriptList.repaint();
    }

    private void appendTranscriptRow(JComponent row) {
        this.transcriptList.remove(this.transcriptBottomGlue);
        if (this.transcriptList.getComponentCount() > 0) {
            Component strut = Box.createVerticalStrut(6);
            if (strut instanceof JComponent jc) {
                jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            this.transcriptList.add(strut);
        }
        this.transcriptList.add(row);
        this.transcriptList.add(this.transcriptBottomGlue);
        this.transcriptList.revalidate();
        this.transcriptList.repaint();
        scrollTranscriptToBottom();
    }

    private AssistantStrip createPlainAssistantStrip() {
        JPanel root = new JPanel(new BorderLayout(0, 4));
        root.setOpaque(false);
        root.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel waitingIndicator = new JLabel("...");
        waitingIndicator.setAlignmentX(Component.LEFT_ALIGNMENT);
        Color waitMuted = UIManager.getColor("Label.disabledForeground");
        if (waitMuted != null) {
            waitingIndicator.setForeground(waitMuted);
        }

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.PAGE_AXIS));
        column.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(waitingIndicator);
        root.add(column, BorderLayout.NORTH);

        return new AssistantStrip(root, column, waitingIndicator);
    }

    /** Lazily creates the assistant {@link JEditorPane} (HTML) when the first token arrives. */
    private JEditorPane ensureAssistantBody(AssistantStrip strip) {
        if (strip.body != null) {
            return strip.body;
        }
        JEditorPane body = new JEditorPane() {
            @Override
            public Dimension getPreferredSize() {
                java.awt.Container p = getParent();
                if (p != null && p.getWidth() > 0) {
                    setSize(p.getWidth(), Short.MAX_VALUE);
                }
                return super.getPreferredSize();
            }
        };
        body.setContentType("text/html");
        body.setMinimumSize(new Dimension(0, 0));
        body.setEditable(false);
        body.setOpaque(false);
        body.setBorder(null);
        body.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null) {
                try {
                    java.awt.Desktop.getDesktop().browse(e.getURL().toURI());
                } catch (Exception ignored) {
                }
            }
        });
        strip.column.add(body);
        strip.body = body;
        this.renderedStrips.add(strip);
        return body;
    }

    private void removeAssistantWaitingIndicator(AssistantStrip strip) {
        JLabel w = strip.waitingIndicator;
        if (w == null || w.getParent() == null) {
            return;
        }
        Container parent = w.getParent();
        parent.remove(w);
        parent.revalidate();
        parent.repaint();
    }

    /** Status line plus No / OK controls; {@code onResolved} receives {@code true} for OK and {@code false} for No. */
    private RoundedPanel buildToolApprovalCard(String statusLine, Consumer<Boolean> onResolved) {
        String line = statusLine != null ? statusLine.trim() : "";
        if (line.isEmpty()) {
            line = "Working…";
        }

        RoundedPanel card = new RoundedPanel();
        card.putClientProperty(TOOL_USAGE_BORDER_MARK, Boolean.TRUE);
        card.setLayout(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        card.setOpaque(false);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        applyToolUsageBorderOnlyTheme(card);

        JLabel label = new JLabel(line);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        Font lf = UIManager.getFont("Label.font");
        if (lf != null) {
            label.setFont(lf.deriveFont(Font.ITALIC));
        }
        Color muted = UIManager.getColor("Label.disabledForeground");
        if (muted != null) {
            label.setForeground(muted);
        }
        card.add(label, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        StyledButton noBtn = new StyledButton("No");
        StyledButton okBtn = new StyledButton("OK");
        noBtn.setStyle(StyledButton.Style.DEFAULT);
        okBtn.setStyle(StyledButton.Style.AI);
        AtomicBoolean resolved = new AtomicBoolean(false);
        Runnable resolveNo =
                () -> {
                    if (resolved.compareAndSet(false, true)) {
                        noBtn.setEnabled(false);
                        okBtn.setEnabled(false);
                        onResolved.accept(false);
                    }
                };
        Runnable resolveOk =
                () -> {
                    if (resolved.compareAndSet(false, true)) {
                        noBtn.setEnabled(false);
                        okBtn.setEnabled(false);
                        onResolved.accept(true);
                    }
                };
        noBtn.addActionListener(e -> resolveNo.run());
        okBtn.addActionListener(e -> resolveOk.run());
        buttons.add(noBtn);
        buttons.add(okBtn);
        card.add(buttons, BorderLayout.SOUTH);
        return card;
    }

    private static final int MARKDOWN_RENDER_DELAY_MS = 30;

    private void appendAssistantReplyDelta(AssistantStrip strip, String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        strip.textAccumulator.append(delta);
        scheduleMarkdownRender(strip);
    }

    private void scheduleMarkdownRender(AssistantStrip strip) {
        if (strip.renderTimer == null) {
            strip.renderTimer = new Timer(MARKDOWN_RENDER_DELAY_MS, e -> renderMarkdown(strip));
            strip.renderTimer.setRepeats(false);
        }
        strip.renderTimer.restart();
    }

    private void renderMarkdown(AssistantStrip strip) {
        if (strip.body == null) {
            return;
        }
        String html = MarkdownRenderer.renderToHtml(strip.textAccumulator.toString());
        strip.body.setText(html);
        this.transcriptList.revalidate();
        this.transcriptList.repaint();
        scrollTranscriptToBottom();
    }

    /** Stops the debounce timer and performs a final render if there is accumulated text. */
    private void flushMarkdownRender(AssistantStrip strip) {
        if (strip.renderTimer != null) {
            strip.renderTimer.stop();
        }
        if (strip.textAccumulator.length() > 0 && strip.body != null) {
            renderMarkdown(strip);
        }
    }

    private MessageBubble createMessageBubble(String title, String body) {
        RoundedPanel card = new RoundedPanel();
        card.setLayout(new BorderLayout(0, 4));
        card.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        card.setOpaque(false);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        applyMessageBubbleTheme(card);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea bodyArea = new JTextArea(body);
        bodyArea.setMinimumSize(new Dimension(0, 0));
        bodyArea.setEditable(false);
        bodyArea.setOpaque(false);
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.setBorder(null);
        bodyArea.setColumns(1);
        bodyArea.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel bubbleColumn = new JPanel();
        bubbleColumn.setOpaque(false);
        bubbleColumn.setLayout(new BoxLayout(bubbleColumn, BoxLayout.PAGE_AXIS));
        bubbleColumn.setAlignmentX(Component.LEFT_ALIGNMENT);
        bubbleColumn.add(titleLabel);
        bubbleColumn.add(Box.createVerticalStrut(4));
        bubbleColumn.add(bodyArea);
        card.add(bubbleColumn, BorderLayout.NORTH);

        return new MessageBubble(card, bodyArea);
    }

    private void refreshTranscriptRowTheme(Component c) {
        if (c instanceof RoundedPanel rp) {
            if (Boolean.TRUE.equals(rp.getClientProperty(TOOL_USAGE_BORDER_MARK))) {
                applyToolUsageBorderOnlyTheme(rp);
            } else {
                applyMessageBubbleTheme(rp);
            }
            return;
        }
        if (c instanceof Container co) {
            for (Component child : co.getComponents()) {
                this.refreshTranscriptRowTheme(child);
            }
        }
    }

    private void scrollTranscriptToBottom() {
        SwingUtilities.invokeLater(
                () -> {
                    JScrollBar bar = this.transcriptScroll.getVerticalScrollBar();
                    bar.setValue(bar.getMaximum());
                });
    }

    /**
     * Vertical glue with left X alignment so {@link BoxLayout} horizontal alignment uses the full viewport
     * width from {@link AITranscriptListPanel}'s {@link javax.swing.Scrollable} contract.
     */
    private static Component leftAlignedVerticalGlue() {
        Component g = Box.createVerticalGlue();
        if (g instanceof JComponent jc) {
            jc.setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        return g;
    }

    private static boolean isTranscriptInterRowStrut(Component c) {
        if (!(c instanceof JComponent jc)) {
            return false;
        }
        Dimension p = jc.getPreferredSize();
        Dimension mn = jc.getMinimumSize();
        Dimension mx = jc.getMaximumSize();
        return p != null
                && p.width == 0
                && p.height >= 2
                && p.height <= 16
                && mn != null
                && mn.height == p.height
                && mx != null
                && mx.height == p.height;
    }

    /** Rounded outline only; no fill ({@link RoundedPanel} skips painting fill when background is null). */
    private static void applyToolUsageBorderOnlyTheme(RoundedPanel card) {
        card.setBackgroundColor(null);
        Color line = UIManager.getColor("Colors.ui.background.3");
        if (line == null) {
            line = UIManager.getColor("Component.borderColor");
        }
        if (line != null) {
            card.setBorderColor(line);
        }
    }

    private static void applyMessageBubbleTheme(RoundedPanel card) {
        Color bg = UIManager.getColor("Colors.ui.background.3");
        card.setBackgroundColor(bg);
        card.setBorderColor(bg);
    }

    private static void applyInputPanelTheme(RoundedPanel panel) {
        Color bg = UIManager.getColor("Colors.ui.background.1");
        Color border = UIManager.getColor("Colors.ui.background.3");
        if (bg != null) {
            panel.setBackgroundColor(bg);
        }
        if (border != null) {
            panel.setBorderColor(border);
        }
    }

    private record MessageBubble(RoundedPanel panel, JTextArea body) {}

    /**
     * Assistant reply segment; {@link #body} is created and added on first {@link ChatStreamMessage.AssistantDelta}.
     * Tool usage rows sit between segments in the transcript list.
     */
    private static final class AssistantStrip {
        final JPanel root;
        final JPanel column;
        final JLabel waitingIndicator;
        final StringBuilder textAccumulator = new StringBuilder();
        JEditorPane body;
        Timer renderTimer;

        AssistantStrip(JPanel root, JPanel column, JLabel waitingIndicator) {
            this.root = root;
            this.column = column;
            this.waitingIndicator = waitingIndicator;
        }
    }
}
