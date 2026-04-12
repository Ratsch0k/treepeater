package treepeater.requestResponse.toolbar;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.FontMetrics;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.formdev.flatlaf.FlatClientProperties;

import treepeater.Treepeater;
import treepeater.ai.AiModelOption;
import treepeater.ai.ChatErrors;
import treepeater.ai.ChatMessage;
import treepeater.ai.ChatRole;
import treepeater.ai.ChatStreamMessage;
import treepeater.ai.ChatToolExecutor;
import treepeater.ai.ChatTooling;
import treepeater.ai.HttpTargetSnapshot;
import treepeater.ai.HttpTargetTools;
import treepeater.ai.StreamingChatClient;
import treepeater.ai.anthropic.AnthropicClientConfig;
import treepeater.ai.anthropic.AnthropicStreamingChatClient;
import treepeater.ai.burp.BurpAiStreamingChatClient;
import treepeater.ai.ollama.OllamaClientConfig;
import treepeater.ai.ollama.OllamaStreamingChatClient;
import treepeater.settings.TreepeaterSettings;
import treepeater.components.RoundedPanel;
import treepeater.components.StyledButton;
import treepeater.icons.WandIcon;

public class AIToolbarTab {
    private static final Object TOOL_USAGE_BORDER_MARK = new Object();

    private static final int INPUT_AREA_MIN_ROWS = 1;
    private static final int INPUT_AREA_MAX_ROWS = 14;

    private static final String DISABLED_INFO_TEXT =
            "The Burp extension API is not available. Load Treepeater as a Burp extension to use the AI tab.";

    private final ToolbarIconButton button;
    private final JPanel content;
    private final JTextArea disabledInfoArea;
    private final TranscriptListPanel transcriptList;
    private final JScrollPane transcriptScroll;
    private final Component transcriptBottomGlue = Box.createVerticalGlue();
    private final JTextArea inputArea;
    private JScrollPane inputScroll;
    private RoundedPanel inputPanel;
    private final StyledButton sendButton;
    private final JComboBox<AiModelOption> modelCombo;
    private final List<ChatMessage> conversation;
    private final AtomicReference<SwingWorker<List<ChatMessage>, Void>> activeChatWorker;

    private final Supplier<HttpTargetSnapshot> targetSnapshotSupplier;

    public AIToolbarTab(Supplier<HttpTargetSnapshot> targetSnapshotSupplier) {
        this.button = new ToolbarIconButton(new WandIcon());
        this.content = new JPanel(new BorderLayout());

        if (Treepeater.api == null) {
            this.transcriptList = null;
            this.transcriptScroll = null;
            this.inputArea = null;
            this.sendButton = null;
            this.modelCombo = null;
            this.conversation = null;
            this.activeChatWorker = null;
            this.targetSnapshotSupplier = null;

            this.disabledInfoArea = buildDisabledInfoArea();

            JPanel center = new JPanel(new GridBagLayout());
            center.setOpaque(false);
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx = 0;
            gc.gridwidth = 1;
            gc.weightx = 1.0;
            gc.anchor = GridBagConstraints.CENTER;

            gc.gridy = 0;
            gc.weighty = 1.0;
            gc.fill = GridBagConstraints.BOTH;
            gc.insets = new Insets(0, 0, 0, 0);
            center.add(Box.createVerticalGlue(), gc);

            gc.gridy = 1;
            gc.weighty = 0.0;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.insets = new Insets(8, 12, 8, 12);
            center.add(this.disabledInfoArea, gc);

            gc.gridy = 2;
            gc.weighty = 1.0;
            gc.fill = GridBagConstraints.BOTH;
            gc.insets = new Insets(0, 0, 0, 0);
            center.add(Box.createVerticalGlue(), gc);

            JPanel disabledPanel = new JPanel(new BorderLayout());
            disabledPanel.setOpaque(false);
            disabledPanel.add(new ToolbarTabTitle("AI"), BorderLayout.NORTH);
            disabledPanel.add(center, BorderLayout.CENTER);
            this.content.add(disabledPanel, BorderLayout.CENTER);
            return;
        }

        this.targetSnapshotSupplier = targetSnapshotSupplier;
        this.disabledInfoArea = null;
        this.transcriptList = new TranscriptListPanel();

        this.transcriptScroll = new JScrollPane(this.transcriptList);
        this.transcriptScroll.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        this.transcriptScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        this.transcriptScroll.getVerticalScrollBar().setUnitIncrement(16);
        this.transcriptScroll.setPreferredSize(new Dimension(0, 200));
        this.transcriptScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                AIToolbarTab.this.constrainAllMessageBubbles();
            }
        });

        this.inputArea = new JTextArea(1, 0);
        this.inputArea.setLineWrap(true);
        this.inputArea.setWrapStyleWord(true);
        this.inputArea.putClientProperty(FlatClientProperties.STYLE, "background: $Colors.ui.background.1;");
        

        this.sendButton = new StyledButton("Send");
        this.sendButton.setStyle(StyledButton.Style.AI);
        this.sendButton.setPreferredSize(new Dimension(80, 22));

        this.modelCombo = new JComboBox<>(new DefaultComboBoxModel<>(new Vector<>(AiModelOption.defaultChoices())));
        this.modelCombo.setSelectedIndex(0);
        this.modelCombo.setMaximumRowCount(12);

        this.conversation = new ArrayList<>();
        this.activeChatWorker = new AtomicReference<>();

        this.sendButton.addActionListener(e -> AIToolbarTab.this.onSend());

        InputMap inputMap = this.inputArea.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = this.inputArea.getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "aiSendPrompt");
        actionMap.put("aiSendPrompt", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                AIToolbarTab.this.onSend();
            }
        });

        this.content.add(this.buildContent(), BorderLayout.CENTER);
    }

    private static boolean isBurpAiEnabled() {
        return Treepeater.api != null && Treepeater.api.ai().isEnabled();
    }

    private StreamingChatClient clientForSelectedModel() {
        AiModelOption opt = (AiModelOption) this.modelCombo.getSelectedItem();
        if (opt == null || Treepeater.api == null) {
            throw new IllegalStateException("No model or API");
        }
        TreepeaterSettings settings = TreepeaterSettings.getInstance();
        if (opt.kind() == AiModelOption.Kind.BURP) {
            return new BurpAiStreamingChatClient(Treepeater.api);
        }
        if (opt.kind() == AiModelOption.Kind.ANTHROPIC) {
            String apiKey = settings.getLlmAnthropicApiKey();
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("Anthropic API key not configured");
            }
            String model = opt.anthropicModel();
            if (model == null || model.isBlank()) {
                throw new IllegalStateException("No Anthropic model id");
            }
            return new AnthropicStreamingChatClient(new AnthropicClientConfig(apiKey, model));
        }
        String baseUrl = settings.getLlmOllamaBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Ollama base URL not configured");
        }
        String model = opt.ollamaModel();
        if (model == null || model.isBlank()) {
            throw new IllegalStateException("No Ollama model id");
        }
        return new OllamaStreamingChatClient(new OllamaClientConfig(baseUrl, model));
    }

    /** Built-in HTTP target tools; stream UI uses {@link ChatStreamMessage.ToolUsage} from the client. */
    private ChatTooling chatTooling() {
        if (this.targetSnapshotSupplier == null) {
            return ChatTooling.none();
        }
        ChatToolExecutor exec =
                (name, argsJson) -> HttpTargetTools.execute(name, argsJson, this.targetSnapshotSupplier.get());
        return new ChatTooling(HttpTargetTools.definitions(), exec);
    }

    /**
     * Runs {@code r} on the EDT and blocks the current thread until it finishes. Used so tool UI is
     * actually laid out and painted before {@code inner.invoke} runs on a worker thread.
     */
    private void runOnEdtAndWait(Runnable r) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(r);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof Exception ex) {
                throw ex;
            }
            if (c instanceof Error err) {
                throw err;
            }
            throw new RuntimeException(c);
        }
    }

    private JTextArea buildDisabledInfoArea() {
        JTextArea area = new JTextArea(DISABLED_INFO_TEXT);
        area.setEditable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setColumns(1);
        area.setBorder(null);
        area.setFont(area.getFont().deriveFont(Font.ITALIC));
        applyDisabledInfoAreaTheme(area);
        return area;
    }

    private static void applyDisabledInfoAreaTheme(JTextArea area) {
        Color fg = UIManager.getColor("Label.foreground");
        if (fg != null) {
            area.setForeground(fg);
        }
    }

    public JButton getButton() {
        return this.button;
    }

    public JPanel getContent() {
        return this.content;
    }

    public void applyLocalTheme() {
        this.button.applyLocalTheme();
        if (this.disabledInfoArea != null) {
            applyDisabledInfoAreaTheme(this.disabledInfoArea);
        }
        if (this.transcriptList != null) {
            refreshTranscriptThemes();
        }
        applyInputPanelTheme();
        if (this.inputScroll != null) {
            SwingUtilities.invokeLater(this::adjustInputAreaHeight);
        }
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

    private void applyInputPanelTheme() {
        if (this.inputPanel == null) {
            return;
        }
        applyInputPanelTheme(this.inputPanel);
    }

    /**
     * Grows the input field with content up to a line-based maximum; beyond that the scroll pane shows
     * a vertical scrollbar. Uses viewport width so wrapped lines contribute to the height.
     */
    private void adjustInputAreaHeight() {
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

    private void scheduleAdjustInputAreaHeight() {
        if (this.inputScroll == null) {
            return;
        }
        SwingUtilities.invokeLater(this::adjustInputAreaHeight);
    }

    private JPanel buildContent() {
        JPanel panel = new JPanel(new BorderLayout());

        panel.add(new ToolbarTabTitle("AI"), BorderLayout.NORTH);

        this.inputPanel = new RoundedPanel();
        this.inputPanel.setLayout(new GridBagLayout());
        this.inputPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 0));
        applyInputPanelTheme(this.inputPanel);

        this.inputScroll = new JScrollPane(this.inputArea);
        this.inputScroll.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        this.inputScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        this.inputScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        this.inputArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                AIToolbarTab.this.scheduleAdjustInputAreaHeight();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                AIToolbarTab.this.scheduleAdjustInputAreaHeight();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                AIToolbarTab.this.scheduleAdjustInputAreaHeight();
            }
        });
        this.inputScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                AIToolbarTab.this.scheduleAdjustInputAreaHeight();
            }
        });

        this.modelCombo.setPreferredSize(new Dimension(120, this.modelCombo.getPreferredSize().height));

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

        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private void onSend() {
        if (Treepeater.api == null || this.modelCombo == null) {
            return;
        }
        AiModelOption choice = (AiModelOption) this.modelCombo.getSelectedItem();
        if (choice != null && choice.kind() == AiModelOption.Kind.BURP && !isBurpAiEnabled()) {
            JOptionPane.showMessageDialog(
                    this.content,
                    "Enable Burp's AI for this extension under Extensions (Use AI), or choose an Ollama model.",
                    "Burp AI unavailable",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        if (choice != null && choice.kind() == AiModelOption.Kind.ANTHROPIC) {
            String key = TreepeaterSettings.getInstance().getLlmAnthropicApiKey();
            if (key == null || key.isBlank()) {
                JOptionPane.showMessageDialog(
                        this.content,
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
                        this.content,
                        "Set the Ollama base URL under Extension settings for Treepeater (LLMs \u2192 Ollama).",
                        "Ollama base URL required",
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

        this.sendButton.setEnabled(false);
        this.modelCombo.setEnabled(false);

        final AtomicReference<AssistantStrip> activeAssistantStrip = new AtomicReference<>();
        AssistantStrip firstStrip = createPlainAssistantStrip();
        activeAssistantStrip.set(firstStrip);
        appendTranscriptRow(firstStrip.root);

        SwingWorker<List<ChatMessage>, Void> worker = new SwingWorker<>() {
            private final List<ChatMessage> requestMessages = messages;

            @Override
            protected List<ChatMessage> doInBackground() throws Exception {
                return AIToolbarTab.this.clientForSelectedModel()
                        .streamChat(
                                this.requestMessages,
                                AIToolbarTab.this.chatTooling(),
                                m -> {
                                    SwingWorker<List<ChatMessage>, Void> w =
                                            AIToolbarTab.this.activeChatWorker.get();
                                    if (w == null || w.isCancelled()) {
                                        return;
                                    }
                                    switch (m) {
                                        case ChatStreamMessage.AssistantDelta ad -> {
                                            if (ad.text().isEmpty()) {
                                                return;
                                            }
                                            SwingUtilities.invokeLater(
                                                    () -> {
                                                        SwingWorker<List<ChatMessage>, Void> w2 =
                                                                AIToolbarTab.this.activeChatWorker.get();
                                                        if (w2 == null || w2.isCancelled()) {
                                                            return;
                                                        }
                                                        AssistantStrip strip =
                                                                activeAssistantStrip.get();
                                                        if (strip == null) {
                                                            return;
                                                        }
                                                        AIToolbarTab.this.removeAssistantWaitingIndicator(strip);
                                                        JTextPane pane =
                                                                AIToolbarTab.this.ensureAssistantBody(strip);
                                                        AIToolbarTab.this.appendAssistantReplyDelta(
                                                                pane, ad.text());
                                                        AIToolbarTab.this.applyTranscriptRowWidth(strip.root);
                                                        AIToolbarTab.this.transcriptList.revalidate();
                                                        AIToolbarTab.this.scrollTranscriptToBottom();
                                                    });
                                        }
                                        case ChatStreamMessage.ToolUsage tu -> {
                                            try {
                                                AIToolbarTab.this.runOnEdtAndWait(
                                                        () -> {
                                                            SwingWorker<List<ChatMessage>, Void> w2 =
                                                                    AIToolbarTab.this.activeChatWorker.get();
                                                            if (w2 == null || w2.isCancelled()) {
                                                                return;
                                                            }
                                                            AssistantStrip strip =
                                                                    activeAssistantStrip.get();
                                                            if (strip == null) {
                                                                return;
                                                            }
                                                            AIToolbarTab.this.removeAssistantWaitingIndicator(
                                                                    strip);
                                                            if (strip.body == null) {
                                                                AIToolbarTab.this
                                                                        .removeAssistantStripRowFromTranscript(
                                                                                strip.root);
                                                            }
                                                            RoundedPanel toolCard =
                                                                    AIToolbarTab.this.buildToolUsageBorderPanel(
                                                                            tu.humanDescription());
                                                            AIToolbarTab.this.appendTranscriptRow(toolCard);
                                                            AssistantStrip nextStrip =
                                                                    AIToolbarTab.this
                                                                            .createPlainAssistantStrip();
                                                            activeAssistantStrip.set(nextStrip);
                                                            AIToolbarTab.this.appendTranscriptRow(
                                                                    nextStrip.root);
                                                            AIToolbarTab.this.applyTranscriptRowWidth(
                                                                    nextStrip.root);
                                                            AIToolbarTab.this.transcriptList.revalidate();
                                                            AIToolbarTab.this.transcriptList.repaint();
                                                            AIToolbarTab.this.scrollTranscriptToBottom();
                                                        });
                                            } catch (Exception e) {
                                                throw new RuntimeException(e);
                                            }
                                        }
                                    }
                                });
            }

            @Override
            protected void done() {
                try {
                    if (isCancelled()) {
                        return;
                    }
                    List<ChatMessage> history = get();
                    AIToolbarTab.this.conversation.clear();
                    AIToolbarTab.this.conversation.addAll(history);
                } catch (Exception ex) {
                    logError(ex);
                    addMessageBubble("Error", ChatErrors.formatUserMessage(ex));
                } finally {
                    AssistantStrip last = activeAssistantStrip.get();
                    if (last != null) {
                        AIToolbarTab.this.removeAssistantWaitingIndicator(last);
                    }
                    AIToolbarTab.this.sendButton.setEnabled(true);
                    if (AIToolbarTab.this.modelCombo != null) {
                        AIToolbarTab.this.modelCombo.setEnabled(true);
                    }
                    if (AIToolbarTab.this.activeChatWorker.get() == this) {
                        AIToolbarTab.this.activeChatWorker.set(null);
                    }
                }
            }
        };

        this.activeChatWorker.set(worker);
        worker.execute();
    }

    private void addMessageBubble(String title, String body) {
        appendBubble(createMessageBubble(title, body).panel());
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
        JTextPane body;

        AssistantStrip(JPanel root, JPanel column, JLabel waitingIndicator) {
            this.root = root;
            this.column = column;
            this.waitingIndicator = waitingIndicator;
        }
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

    private void appendTranscriptRow(JComponent row) {
        this.transcriptList.remove(this.transcriptBottomGlue);
        if (this.transcriptList.getComponentCount() > 0) {
            this.transcriptList.add(Box.createVerticalStrut(6));
        }
        this.transcriptList.add(row);
        this.transcriptList.add(this.transcriptBottomGlue);
        applyTranscriptRowWidth(row);
        this.transcriptList.revalidate();
        this.transcriptList.repaint();
        SwingUtilities.invokeLater(this::constrainAllMessageBubbles);
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

    /** Lazily creates the assistant {@link JTextPane} when the first token arrives. */
    private JTextPane ensureAssistantBody(AssistantStrip strip) {
        if (strip.body != null) {
            return strip.body;
        }
        JTextPane body = new JTextPane();
        body.setEditable(false);
        body.setOpaque(false);
        body.setBorder(null);
        body.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        Font lf = UIManager.getFont("Label.font");
        if (lf != null) {
            body.setFont(lf);
        }
        Style regular = body.addStyle("regular", null);
        Color fg = UIManager.getColor("Label.foreground");
        if (fg != null) {
            StyleConstants.setForeground(regular, fg);
        }
        strip.column.add(body);
        strip.body = body;
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

    /** One-line status only, e.g. {@code Getting target}. */
    private RoundedPanel buildToolUsageBorderPanel(String statusLine) {
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

        JLabel label = new JLabel("Tool: " + line);
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
        return card;
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

    private void appendAssistantReplyDelta(JTextPane pane, String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        StyledDocument doc = pane.getStyledDocument();
        Style regular = pane.getStyle("regular");
        try {
            doc.insertString(doc.getLength(), delta, regular);
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
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
        bodyArea.setEditable(false);
        bodyArea.setOpaque(false);
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.setBorder(null);
        bodyArea.setColumns(1);
        bodyArea.setAlignmentX(Component.LEFT_ALIGNMENT);

        // BorderLayout.CENTER would stretch the body to any extra height given to the card (e.g.
        // when the scroll view is taller than the stacked bubbles). Keep title + body in NORTH only.
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

    /**
     * BoxLayout uses each bubble's preferred width (from {@link JTextArea}) which grows with long
     * unbreakable lines. Capping maximum width lets wrapping use the viewport and keeps the strip
     * from growing horizontally.
     */
    private int bubbleMaxWidth() {
        int viewportW = this.transcriptScroll.getViewport().getWidth();
        if (viewportW <= 0) {
            viewportW = this.transcriptList.getWidth();
        }
        if (viewportW <= 0) {
            return Integer.MAX_VALUE;
        }
        int insets = this.transcriptList.getInsets().left + this.transcriptList.getInsets().right;
        return Math.max(1, viewportW - insets);
    }

    private void applyTranscriptRowWidth(JComponent row) {
        int maxW = bubbleMaxWidth();
        if (maxW >= Integer.MAX_VALUE) {
            return;
        }
        row.setMaximumSize(new Dimension(maxW, Integer.MAX_VALUE));
    }

    private void constrainAllMessageBubbles() {
        int maxW = bubbleMaxWidth();
        if (maxW >= Integer.MAX_VALUE) {
            return;
        }
        for (Component c : this.transcriptList.getComponents()) {
            if (c instanceof JComponent jc) {
                jc.setMaximumSize(new Dimension(maxW, Integer.MAX_VALUE));
            }
        }
        this.transcriptList.revalidate();
    }

    private static void applyMessageBubbleTheme(RoundedPanel card) {
        Color bg = UIManager.getColor("Colors.ui.background.3");
        card.setBackgroundColor(bg);
        card.setBorderColor(bg);
    }

    private void refreshTranscriptThemes() {
        for (Component c : this.transcriptList.getComponents()) {
            this.refreshTranscriptRowTheme(c);
        }
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
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = this.transcriptScroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private static void logError(Throwable ex) {
        if (Treepeater.api != null) {
            Treepeater.api.logging().logToError(ex);
        }
    }

    /**
     * Without {@link Scrollable}, {@link javax.swing.JViewport} forces the view to be at least as
     * tall as the viewport. {@link BoxLayout} then receives that extra height and stretches
     * flexible children so bubbles fill the scroll area. Tracking viewport width only fixes wrapping
     * while keeping the view's height at the sum of message sizes.
     */
    private static final class TranscriptListPanel extends JPanel implements Scrollable {
        TranscriptListPanel() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
            setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? 16 : 1;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
