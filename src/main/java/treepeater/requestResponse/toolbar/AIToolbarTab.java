package treepeater.requestResponse.toolbar;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicReference;

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
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.formdev.flatlaf.FlatClientProperties;

import treepeater.Treepeater;
import treepeater.ai.AiModelOption;
import treepeater.ai.ChatErrors;
import treepeater.ai.ChatMessage;
import treepeater.ai.ChatRole;
import treepeater.ai.StreamingChatClient;
import treepeater.ai.burp.BurpAiStreamingChatClient;
import treepeater.ai.ollama.OllamaClientConfig;
import treepeater.ai.ollama.OllamaStreamingChatClient;
import treepeater.components.RoundedPanel;
import treepeater.components.StyledButton;
import treepeater.icons.WandIcon;

public class AIToolbarTab {
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

    public AIToolbarTab() {
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
        if (opt.kind() == AiModelOption.Kind.BURP) {
            return new BurpAiStreamingChatClient(Treepeater.api);
        }
        return new OllamaStreamingChatClient(
                new OllamaClientConfig(AiModelOption.DEFAULT_OLLAMA_BASE_URL, opt.ollamaModel()));
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
            refreshMessageBubbleThemes();
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

        MessageBubble assistantBubble = createMessageBubble("Assistant", "");
        appendBubble(assistantBubble.panel());
        final JTextArea streamingBody = assistantBubble.body();
        final RoundedPanel streamingCard = assistantBubble.panel();

        SwingWorker<List<ChatMessage>, Void> worker = new SwingWorker<>() {
            private final List<ChatMessage> requestMessages = messages;

            @Override
            protected List<ChatMessage> doInBackground() throws Exception {
                return AIToolbarTab.this.clientForSelectedModel()
                        .streamChat(this.requestMessages, delta -> {
                    SwingWorker<List<ChatMessage>, Void> w = AIToolbarTab.this.activeChatWorker.get();
                    if (w == null || w.isCancelled()) {
                        return;
                    }
                    if (delta == null || delta.isEmpty()) {
                        return;
                    }
                    SwingUtilities.invokeLater(() -> {
                        SwingWorker<List<ChatMessage>, Void> w2 = AIToolbarTab.this.activeChatWorker.get();
                        if (w2 == null || w2.isCancelled()) {
                            return;
                        }
                        streamingBody.append(delta);
                        AIToolbarTab.this.applyBubbleWidthConstraint(streamingCard);
                        AIToolbarTab.this.transcriptList.revalidate();
                        AIToolbarTab.this.scrollTranscriptToBottom();
                    });
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

    private void appendBubble(RoundedPanel bubble) {
        this.transcriptList.remove(this.transcriptBottomGlue);
        if (this.transcriptList.getComponentCount() > 0) {
            this.transcriptList.add(Box.createVerticalStrut(6));
        }
        this.transcriptList.add(bubble);
        this.transcriptList.add(this.transcriptBottomGlue);
        constrainAllMessageBubbles();
        this.transcriptList.revalidate();
        this.transcriptList.repaint();
        SwingUtilities.invokeLater(this::constrainAllMessageBubbles);
        scrollTranscriptToBottom();
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

    private void applyBubbleWidthConstraint(RoundedPanel card) {
        int maxW = bubbleMaxWidth();
        if (maxW >= Integer.MAX_VALUE) {
            return;
        }
        card.setMaximumSize(new Dimension(maxW, Integer.MAX_VALUE));
    }

    private void constrainAllMessageBubbles() {
        int maxW = bubbleMaxWidth();
        if (maxW >= Integer.MAX_VALUE) {
            return;
        }
        for (Component c : this.transcriptList.getComponents()) {
            if (c instanceof RoundedPanel) {
                ((RoundedPanel) c).setMaximumSize(new Dimension(maxW, Integer.MAX_VALUE));
            }
        }
        this.transcriptList.revalidate();
    }

    private static void applyMessageBubbleTheme(RoundedPanel card) {
        Color bg = UIManager.getColor("Colors.ui.background.3");
        card.setBackgroundColor(bg);
        card.setBorderColor(bg);
    }

    private void refreshMessageBubbleThemes() {
        for (Component c : this.transcriptList.getComponents()) {
            if (c instanceof RoundedPanel) {
                applyMessageBubbleTheme((RoundedPanel) c);
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
