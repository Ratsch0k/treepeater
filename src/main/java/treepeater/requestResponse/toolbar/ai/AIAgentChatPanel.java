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
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.formdev.flatlaf.FlatClientProperties;

import burp.api.montoya.http.message.requests.HttpRequest;

import treepeater.Treepeater;
import treepeater.ai.AgentChatMessagesJsonLogger;
import treepeater.ai.AgentChatSession;
import treepeater.ai.AgentMode;
import treepeater.ai.AgentSystemPrompt;
import treepeater.ai.MarkdownRenderer;
import treepeater.ai.ChatErrors;
import treepeater.ai.ChatMessage;
import treepeater.ai.ChatRole;
import treepeater.ai.ChatStreamMessage;
import treepeater.ai.ChatStreamSession;
import treepeater.ai.ChatToolCall;
import treepeater.ai.ChatTooling;
import treepeater.ai.AgentTabMention;
import treepeater.ai.RepeaterTabQueryMatcher;
import treepeater.ai.AgentToolContext;
import treepeater.ai.CoalescingChatStreamOutbound;
import treepeater.ai.HttpTargetTools;
import treepeater.ai.LineDiffer;
import treepeater.ai.model.BooleanOption;
import treepeater.ai.model.EnumOption;
import treepeater.ai.model.LlmModelDefinition;
import treepeater.ai.model.LlmModelOptionValues;
import treepeater.ai.model.LlmModelRef;
import treepeater.ai.model.LlmProvider;
import treepeater.ai.model.LlmRegistry;
import treepeater.ai.model.ModelOption;
import treepeater.components.RoundedPanel;
import treepeater.components.StyledButton;
import treepeater.icons.GearIcon;

/**
 * One nested “agent” chat tab: transcript, draft input, model choice, and any in-flight request for this
 * tab only.
 */
public final class AIAgentChatPanel extends JPanel {
    private static final Object TOOL_USAGE_BORDER_MARK = new Object();

    /** {@link JComponent#putClientProperty} key; value {@code "removed"} / {@code "added"} / {@code "meta"} for tool-card diff blocks (see {@link #refreshTranscriptRowTheme}). */
    private static final String TOOL_CARD_DIFF_STYLE_KEY = "Treepeater.toolCardDiffStyle";

    private static final int INPUT_AREA_MIN_ROWS = 1;
    private static final int INPUT_AREA_MAX_ROWS = 14;
    private static final int HISTORY_MENTION_MAX_VISIBLE_ROWS = 8;
    private static final int HISTORY_MENTION_PREFERRED_WIDTH = 320;

    /** Attribute on {@link StyledDocument} character runs for an embedded tab mention. */
    private static final String REQUEST_NODE_MENTION_ATTR = "Treepeater.requestNodeId";

    private static final Pattern REQUEST_NODE_ID_TOKEN = Pattern.compile("request_node_id:(\\d+)(\\s*)");

    /**
     * Contiguous character range that shares one {@link #REQUEST_NODE_MENTION_ATTR} id, or {@code null}.
     */
    private static int[] findMentionRunBounds(StyledDocument sd, int p, int docLen) {
        if (p < 0 || p >= docLen) {
            return null;
        }
        Element el0 = sd.getCharacterElement(p);
        Object ido = el0.getAttributes().getAttribute(REQUEST_NODE_MENTION_ATTR);
        if (!(ido instanceof Integer id)) {
            return null;
        }
        int lo = el0.getStartOffset();
        int hi = el0.getEndOffset();
        while (lo > 0) {
            Element l = sd.getCharacterElement(lo - 1);
            Object o = l.getAttributes().getAttribute(REQUEST_NODE_MENTION_ATTR);
            if (o instanceof Integer j && j.equals(id)) {
                int ls = l.getStartOffset();
                if (ls < lo) {
                    lo = ls;
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        while (hi < docLen) {
            Element h = sd.getCharacterElement(hi);
            Object o = h.getAttributes().getAttribute(REQUEST_NODE_MENTION_ATTR);
            if (o instanceof Integer j && j.equals(id)) {
                int he = h.getEndOffset();
                if (he > hi) {
                    hi = he;
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        return new int[] {lo, Math.min(hi, docLen)};
    }

    /**
     * If {@code [offset, offset+length)} intersects a tab mention, expand to full mention run(s) so the
     * user cannot delete only part of a chip.
     *
     * @return {@code { start, length }} to pass to {@link DocumentFilter.FilterBypass#remove}
     */
    private static int[] expandDeleteForTabMentions(StyledDocument sd, int offset, int length) {
        int docLen = sd.getLength();
        if (length <= 0) {
            return new int[] {offset, 0};
        }
        if (offset < 0) {
            offset = 0;
        }
        if (offset >= docLen) {
            return new int[] {offset, 0};
        }
        int e = offset + length;
        if (e > docLen) {
            e = docLen;
        }
        if (e <= offset) {
            return new int[] {offset, 0};
        }
        int s = offset;
        int endEx = e;
        boolean changed;
        int guard = 0;
        do {
            changed = false;
            for (int p = s; p < endEx; p++) {
                int[] run = findMentionRunBounds(sd, p, docLen);
                if (run == null) {
                    continue;
                }
                int rs = run[0];
                int re = run[1];
                if (rs < s) {
                    s = rs;
                    changed = true;
                }
                if (re > endEx) {
                    endEx = re;
                    changed = true;
                }
            }
        } while (changed && ++guard < 32);
        return new int[] {s, endEx - s};
    }

    /**
     * {@code true} if an insert at {@code offset} would split a tab-mention (strictly between the run’s
     * start and end, exclusive end of the run).
     */
    private static boolean isInsertOffsetInsideMention(StyledDocument sd, int offset) {
        int docLen = sd.getLength();
        if (offset <= 0 || offset > docLen) {
            return false;
        }
        int[] run = findMentionRunBounds(sd, offset, docLen);
        if (run == null) {
            return false;
        }
        int lo = run[0];
        int hi = run[1];
        return lo < offset && offset < hi;
    }

    /**
     * {@code true} if an insert at {@code offset} is immediately after the last character of a
     * tab-mention (offset equals the run’s exclusive end). New text at that position would
     * otherwise inherit the mention’s cell styling from the styled editor’s input attributes.
     */
    private static boolean isInsertAtEndOfMentionRun(StyledDocument sd, int offset) {
        if (offset <= 0) {
            return false;
        }
        int docLen = sd.getLength();
        if (offset > docLen) {
            return false;
        }
        int[] run = findMentionRunBounds(sd, offset - 1, docLen);
        if (run == null) {
            return false;
        }
        return offset == run[1];
    }

    private static final String SEND_BUTTON_LABEL = "Send";
    private static final String STOP_BUTTON_LABEL = "Stop";

    private final AIChatHost host;

    private final AITranscriptListPanel transcriptList;
    private final JScrollPane transcriptScroll;
    private final Component transcriptBottomGlue = leftAlignedVerticalGlue();
    private final JTextPane inputArea;
    private JScrollPane inputScroll;
    private RoundedPanel inputPanel;
    private final StyledButton sendButton;
    private final JComboBox<AgentMode> agentModeCombo;
    private final JComboBox<LlmModelDefinition> modelCombo;
    private final JButton modelOptionsButton;
    private final JPopupMenu modelOptionsMenu = new JPopupMenu();
    /** In-input {@code @}-mention popup for open repeater tabs; {@code -1} when none. */
    private int historyMentionAt = -1;
    /**
     * Backup for the "@" position after {@link #dismissHistoryMentionPopup} clears
     * {@link #historyMentionAt} in some focus/click orderings, so a list choice can still
     * replace the correct character.
     */
    private int historyMentionReplaceAt = -1;
    private static final String HISTORY_MENTION_IM_ENTER_ID = "Treepeater.historyMentionEnter";
    /** Replaced by {@value #HISTORY_MENTION_IM_ENTER_ID} in {@code inputArea} while the mention is open. */
    private Object historyMentionSavedImEnter;
    private AbstractAction historyMentionEnterAction;
    private JWindow historyMentionWindow;
    private JList<AgentTabMention> historyMentionList;
    private JScrollPane historyMentionScroll;
    private final DefaultListCellRenderer historyMentionListCell = new DefaultListCellRenderer();
    /**
     * Per-tab option-value bag persisted across model switches. When the user toggles a knob in the
     * gear menu we replace this with an immutable copy via {@link LlmModelOptionValues#with}.
     */
    private LlmModelOptionValues currentOptionValues;
    private final Runnable onPersistState;
    private boolean blockPersist;
    private final List<ChatMessage> conversation = new ArrayList<>();
    private final List<AssistantStrip> renderedStrips = new ArrayList<>();
    private final AtomicReference<SwingWorker<List<ChatMessage>, Void>> activeChatWorker =
            new AtomicReference<>();

    private final AtomicReference<AssistantStrip> transcriptActiveAssistantStrip = new AtomicReference<>();

    private final AtomicReference<ChatStreamSession> activeSession = new AtomicReference<>();

    /**
     * Stable per-panel id passed to {@link ChatStreamSession} so streaming clients can use it as a
     * backend prompt-prefix cache routing hint that is consistent across user turns of the same
     * conversation. Lifecycle is the panel itself; a new panel (e.g. after restart) gets a new id.
     */
    private final String conversationKey = UUID.randomUUID().toString();

    /** Coalesces repeated scroll-to-bottom requests to a single {@link SwingUtilities#invokeLater}. */
    private boolean transcriptScrollCoalescePending;

    public AIAgentChatPanel(AIChatHost host) {
        this(host, null);
    }

    public AIAgentChatPanel(AIChatHost host, Runnable onPersistState) {
        super(new BorderLayout());
        this.host = host;
        this.onPersistState = onPersistState;
        setOpaque(false);

        this.transcriptList = new AITranscriptListPanel();
        this.transcriptScroll = new JScrollPane(this.transcriptList);
        this.transcriptScroll.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        this.transcriptScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        this.transcriptScroll.getVerticalScrollBar().setUnitIncrement(16);
        this.transcriptScroll.setPreferredSize(new Dimension(0, 200));

        this.inputArea =
                new JTextPane() {
                    @Override
                    public boolean getScrollableTracksViewportWidth() {
                        return true;
                    }
                };
        this.inputArea.putClientProperty(FlatClientProperties.STYLE, "background: $Colors.ui.background.1;");
        this.inputArea.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        this.inputArea.setOpaque(true);
        {
            Color ibg = UIManager.getColor("Colors.ui.background.1");
            if (ibg != null) {
                this.inputArea.setBackground(ibg);
            }
            Color ifg = UIManager.getColor("Label.foreground");
            if (ifg != null) {
                this.inputArea.setForeground(ifg);
            }
            Font f = UIManager.getFont("TextArea.font");
            if (f != null) {
                this.inputArea.setFont(f);
            }
        }
        if (this.inputArea.getDocument() instanceof AbstractDocument ad
                && this.inputArea.getDocument() instanceof StyledDocument sd) {
            ad.setDocumentFilter(
                    new DocumentFilter() {
                        @Override
                        public void insertString(
                                DocumentFilter.FilterBypass fb,
                                int offset,
                                String text,
                                AttributeSet attrs)
                                throws BadLocationException {
                            if (text == null || text.isEmpty()) {
                                return;
                            }
                            if (isInsertOffsetInsideMention(sd, offset)) {
                                return;
                            }
                            AttributeSet a = attrs;
                            if (isInsertAtEndOfMentionRun(sd, offset)) {
                                a = AIAgentChatPanel.this.createPlainInputAttributes();
                            }
                            fb.insertString(offset, text, a);
                        }

                        @Override
                        public void remove(DocumentFilter.FilterBypass fb, int offset, int length)
                                throws BadLocationException {
                            if (length <= 0) {
                                return;
                            }
                            int[] ex = expandDeleteForTabMentions(sd, offset, length);
                            fb.remove(ex[0], ex[1]);
                        }

                        @Override
                        public void replace(
                                DocumentFilter.FilterBypass fb,
                                int offset,
                                int length,
                                String text,
                                AttributeSet attrs)
                                throws BadLocationException {
                            if (length == 0) {
                                String t = text != null ? text : "";
                                if (!t.isEmpty() && isInsertOffsetInsideMention(sd, offset)) {
                                    return;
                                }
                                AttributeSet a = attrs;
                                if (isInsertAtEndOfMentionRun(sd, offset)) {
                                    a = AIAgentChatPanel.this.createPlainInputAttributes();
                                }
                                fb.insertString(offset, t, a);
                                return;
                            }
                            int[] ex = expandDeleteForTabMentions(sd, offset, length);
                            AttributeSet a = attrs;
                            if (text != null
                                    && !text.isEmpty()
                                    && isInsertAtEndOfMentionRun(sd, ex[0])) {
                                a = AIAgentChatPanel.this.createPlainInputAttributes();
                            }
                            fb.replace(ex[0], ex[1], text, a);
                        }
                    });
        }

        this.sendButton = new StyledButton(SEND_BUTTON_LABEL);
        this.sendButton.setStyle(StyledButton.Style.AI);
        this.sendButton.setPreferredSize(new Dimension(80, 22));

        this.agentModeCombo =
                new JComboBox<>(new DefaultComboBoxModel<>(new Vector<>(Arrays.asList(AgentMode.values()))));
        this.agentModeCombo.setSelectedItem(AgentMode.ASK);
        this.agentModeCombo.setMaximumRowCount(6);

        this.modelCombo =
                new JComboBox<>(new DefaultComboBoxModel<>(new Vector<>(LlmRegistry.allModels())));
        this.modelCombo.setSelectedIndex(0);
        this.modelCombo.setMaximumRowCount(12);
        LlmModelDefinition initial = (LlmModelDefinition) this.modelCombo.getSelectedItem();
        this.currentOptionValues = initial != null ? initial.defaults() : LlmModelOptionValues.EMPTY;
        this.modelCombo.addActionListener(
                e -> {
                    this.updateModelOptionsButtonVisibility();
                    this.notifyPersist();
                });
        this.agentModeCombo.addActionListener(e -> this.notifyPersist());

        this.modelOptionsButton = new JButton(new GearIcon().withColor(UIManager.getColor("Label.foreground")));
        this.modelOptionsButton.putClientProperty(FlatClientProperties.STYLE, "background: $Colors.ui.background.2; border: 4,4,4,4,$ComboBox.buttonSeparatorColor,1,8;");
        this.modelOptionsButton.setToolTipText("Model options");
        this.modelOptionsButton.addActionListener(e -> this.showModelOptionsMenu());

        this.sendButton.addActionListener(e -> AIAgentChatPanel.this.onSendOrStopAction());

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
                                if (e.getLength() == 1) {
                                    try {
                                        String ins = e.getDocument().getText(e.getOffset(), e.getLength());
                                        if ("@".equals(ins)
                                                && AIAgentChatPanel.this.isAtMentionWordStart(
                                                        e.getDocument(), e.getOffset())) {
                                            AIAgentChatPanel.this.historyMentionAt = e.getOffset();
                                        }
                                    } catch (BadLocationException ex) {
                                        // ignore
                                    }
                                }
                                AIAgentChatPanel.this.onInputDocumentForHistoryMention();
                            }

                            @Override
                            public void removeUpdate(DocumentEvent e) {
                                AIAgentChatPanel.this.scheduleAdjustInputAreaHeight();
                                AIAgentChatPanel.this.onInputDocumentForHistoryMention();
                            }

                            @Override
                            public void changedUpdate(DocumentEvent e) {
                                AIAgentChatPanel.this.scheduleAdjustInputAreaHeight();
                                AIAgentChatPanel.this.onInputDocumentForHistoryMention();
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
        this.installHistoryMentionPopupControls();

        this.agentModeCombo.setPreferredSize(new Dimension(80, this.agentModeCombo.getPreferredSize().height));
        this.modelCombo.setPreferredSize(new Dimension(100, this.modelCombo.getPreferredSize().height));

        JPanel sendControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        sendControls.setOpaque(false);
        sendControls.add(this.agentModeCombo);
        sendControls.add(this.modelOptionsButton);
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

        this.updateModelOptionsButtonVisibility();
    }

    void applyInputPanelTheme() {
        if (this.inputPanel == null) {
            return;
        }
        applyInputPanelTheme(this.inputPanel);
        if (this.inputArea != null) {
            this.inputArea.putClientProperty(FlatClientProperties.STYLE, "background: $Colors.ui.background.1;");
            Color ibg = UIManager.getColor("Colors.ui.background.1");
            if (ibg != null) {
                this.inputArea.setBackground(ibg);
            }
            Color ifg = UIManager.getColor("Label.foreground");
            if (ifg != null) {
                this.inputArea.setForeground(ifg);
            }
        }

        if (this.modelOptionsButton != null) {
            this.modelOptionsButton.setIcon(new GearIcon().withColor(UIManager.getColor("Label.foreground")));
        }
    }

    private void updateModelOptionsButtonVisibility() {
        LlmModelDefinition def = (LlmModelDefinition) this.modelCombo.getSelectedItem();
        boolean configurable = def != null && !def.supportedOptions().isEmpty();
        this.modelOptionsButton.setVisible(configurable);
    }

    private void showModelOptionsMenu() {
        this.modelOptionsMenu.removeAll();
        LlmModelDefinition def = (LlmModelDefinition) this.modelCombo.getSelectedItem();
        if (def == null) {
            return;
        }
        for (ModelOption<?> opt : def.supportedOptions()) {
            JComponent item = renderOptionMenu(opt, def);
            if (item != null) {
                this.modelOptionsMenu.add(item);
            }
        }
        if (this.modelOptionsMenu.getComponentCount() == 0) {
            return;
        }
        this.modelOptionsMenu.validate();
        int ph = this.modelOptionsMenu.getPreferredSize().height;
        this.modelOptionsMenu.show(this.modelOptionsButton, 0, -ph);
    }

    private JComponent renderOptionMenu(ModelOption<?> opt, LlmModelDefinition def) {
        if (opt instanceof EnumOption<?> eo) {
            return buildEnumOptionMenu(eo, def);
        }
        if (opt instanceof BooleanOption bo) {
            return buildBooleanOptionItem(bo);
        }
        return null;
    }

    private <E extends Enum<E>> JMenu buildEnumOptionMenu(EnumOption<E> opt, LlmModelDefinition def) {
        JMenu menu = new JMenu(opt.menuLabel());
        ButtonGroup group = new ButtonGroup();
        List<E> allowed = def.allowedValues(opt);
        if (allowed.isEmpty()) {
            allowed = Arrays.asList(opt.type().getEnumConstants());
        }
        E current = this.currentOptionValues.get(opt);
        if (current == null) {
            current = def.defaults().get(opt);
        }
        for (E value : allowed) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(opt.valueLabel(value));
            group.add(item);
            if (value == current) {
                item.setSelected(true);
            }
            final E chosen = value;
            item.addActionListener(
                    a -> {
                        this.currentOptionValues = this.currentOptionValues.with(opt, chosen);
                        this.notifyPersist();
                    });
            menu.add(item);
        }
        return menu;
    }

    private JCheckBoxMenuItem buildBooleanOptionItem(BooleanOption opt) {
        Boolean current = this.currentOptionValues.get(opt);
        boolean checked = current != null && current.booleanValue();
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(opt.menuLabel(), checked);
        item.addActionListener(
                a -> {
                    this.currentOptionValues = this.currentOptionValues.with(opt, item.isSelected());
                    this.notifyPersist();
                });
        return item;
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
        this.inputArea.setSize(new Dimension(textWidth, Short.MAX_VALUE));

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
        LlmModelDefinition choice = (LlmModelDefinition) this.modelCombo.getSelectedItem();
        if (choice != null) {
            Optional<LlmProvider.UnavailableReason> reason = choice.provider().unavailableReason(choice);
            if (reason.isPresent()) {
                JOptionPane.showMessageDialog(
                        this.host.dialogParent(),
                        reason.get().message(),
                        reason.get().title(),
                        JOptionPane.INFORMATION_MESSAGE);
                return;
            }
        }

        String displayText = this.inputArea.getText().trim();
        if (displayText.isEmpty()) {
            return;
        }
        String agentText = compileUserMessageForAgent();
        if (agentText.isEmpty()) {
            return;
        }

        SwingWorker<List<ChatMessage>, Void> running = this.activeChatWorker.get();
        if (running != null && !running.isDone()) {
            return;
        }

        this.inputArea.setText("");
        addMessageBubble("You", displayText);

        List<ChatMessage> messages = new ArrayList<>(this.conversation);
        messages.add(new ChatMessage(ChatRole.USER, agentText));
        AgentSystemPrompt.prependDefault(messages);

        setSendButtonWorking(true);
        this.agentModeCombo.setEnabled(false);
        this.modelCombo.setEnabled(false);
        this.modelOptionsButton.setEnabled(false);

        ChatTooling requestTooling = this.host.chatTooling(selectedAgentMode());

        AssistantStrip firstStrip = createPlainAssistantStrip();
        this.transcriptActiveAssistantStrip.set(firstStrip);
        appendTranscriptRow(firstStrip.root);

        AtomicReference<ChatStreamSession> sessionRef = new AtomicReference<>();
        final CoalescingChatStreamOutbound streamCoalescer =
                new CoalescingChatStreamOutbound(
                        m -> SwingUtilities.invokeLater(() -> handleStreamMessageOnEdt(m, sessionRef.get())));
        ChatStreamSession session = new ChatStreamSession(streamCoalescer, this.conversationKey);
        sessionRef.set(session);
        this.activeSession.set(session);

        SwingWorker<List<ChatMessage>, Void> worker = new SwingWorker<>() {
            private final List<ChatMessage> requestMessages = messages;

            @Override
            protected List<ChatMessage> doInBackground() throws Exception {
                try {
                    LlmModelDefinition selected =
                            (LlmModelDefinition) AIAgentChatPanel.this.modelCombo.getSelectedItem();
                    return AIAgentChatPanel.this
                            .host
                            .clientForSelectedModel(selected, AIAgentChatPanel.this.currentOptionValues)
                            .streamChat(this.requestMessages, requestTooling, session);
                } finally {
                    streamCoalescer.shutdown();
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
                    AIAgentChatPanel.this.setSendButtonWorking(false);
                    AIAgentChatPanel.this.agentModeCombo.setEnabled(true);
                    AIAgentChatPanel.this.modelCombo.setEnabled(true);
                    AIAgentChatPanel.this.modelOptionsButton.setEnabled(true);
                    if (AIAgentChatPanel.this.activeChatWorker.get() == this) {
                        AIAgentChatPanel.this.activeChatWorker.set(null);
                    }
                    AIAgentChatPanel.this.notifyPersist();
                }
            }
        };

        this.activeChatWorker.set(worker);
        worker.execute();
    }

    /**
     * Sends the draft when idle, or stops the in-flight agent request (same as the Stop control) when a run is active.
     */
    private void onSendOrStopAction() {
        SwingWorker<List<ChatMessage>, Void> w = this.activeChatWorker.get();
        if (w != null && !w.isDone()) {
            cancelInFlightChat();
            return;
        }
        startSend();
    }

    private void setSendButtonWorking(boolean working) {
        this.sendButton.setText(working ? STOP_BUTTON_LABEL : SEND_BUTTON_LABEL);
        this.sendButton.setEnabled(true);
    }

    private void notifyPersist() {
        if (this.blockPersist || this.onPersistState == null) {
            return;
        }
        this.onPersistState.run();
    }

    public void loadFromSession(AgentChatSession session) {
        this.blockPersist = true;
        try {
            this.conversation.clear();
            this.conversation.addAll(session.conversation());
            this.agentModeCombo.setSelectedItem(session.agentMode());
            this.applyModelFromRef(session.modelRef());
            this.updateModelOptionsButtonVisibility();
            this.clearTranscriptForReload();
            this.rebuildTranscriptFromPersistedMessages();
        } finally {
            this.blockPersist = false;
        }
    }

    public AgentChatSession toSessionSnapshot(String tabTitle) {
        LlmModelDefinition def = (LlmModelDefinition) this.modelCombo.getSelectedItem();
        LlmModelRef ref = LlmModelRef.capture(def, this.currentOptionValues);
        return new AgentChatSession(
                tabTitle,
                new ArrayList<>(this.conversation),
                this.selectedAgentMode(),
                ref);
    }

    /**
     * Resolves a persisted ref to a live {@link LlmModelDefinition} via {@link LlmRegistry}, then
     * either selects it in the combo (adding it if the registry synthesized a new entry, e.g. for a
     * user-typed Ollama model) or falls back to the first combo entry.
     */
    private void applyModelFromRef(LlmModelRef ref) {
        javax.swing.DefaultComboBoxModel<LlmModelDefinition> m =
                (javax.swing.DefaultComboBoxModel<LlmModelDefinition>) this.modelCombo.getModel();
        Optional<LlmModelDefinition> resolved = LlmRegistry.resolve(ref);
        if (resolved.isEmpty()) {
            this.modelCombo.setSelectedIndex(0);
            LlmModelDefinition fallback = (LlmModelDefinition) this.modelCombo.getSelectedItem();
            this.currentOptionValues =
                    fallback != null ? fallback.defaults() : LlmModelOptionValues.EMPTY;
            return;
        }
        LlmModelDefinition target = resolved.get();
        for (int i = 0; i < m.getSize(); i++) {
            LlmModelDefinition def = m.getElementAt(i);
            if (matchesRef(def, ref)) {
                this.modelCombo.setSelectedIndex(i);
                this.currentOptionValues = LlmModelRef.materializeValues(def, ref);
                return;
            }
        }
        m.addElement(target);
        this.modelCombo.setSelectedItem(target);
        this.currentOptionValues = LlmModelRef.materializeValues(target, ref);
    }

    private static boolean matchesRef(LlmModelDefinition def, LlmModelRef ref) {
        if (def == null || ref == null) {
            return false;
        }
        return def.provider().id().equals(ref.providerId())
                && def.modelId().equals(ref.modelId());
    }

    private void clearTranscriptForReload() {
        this.transcriptList.removeAll();
        this.transcriptList.add(this.transcriptBottomGlue);
        this.renderedStrips.clear();
    }

    private void rebuildTranscriptFromPersistedMessages() {
        for (ChatMessage m : this.conversation) {
            if (m.role() == ChatRole.SYSTEM) {
                continue;
            }
            if (m.role() == ChatRole.USER) {
                this.addMessageBubble("You", formatUserMessageForTranscript(m.content()));
                continue;
            }
            if (m.role() == ChatRole.TOOL) {
                continue;
            }
            if (m.role() == ChatRole.ASSISTANT) {
                boolean hasText = m.content() != null && !m.content().isEmpty();
                if (hasText) {
                    this.appendRestoredAssistantText(m.content());
                }
                if (m.hasAssistantToolCalls()) {
                    for (ChatToolCall tc : m.assistantToolCalls()) {
                        this.appendRestoredToolCard(tc);
                    }
                }
            }
        }
        this.transcriptList.revalidate();
        this.transcriptList.repaint();
        this.scrollTranscriptToBottom();
    }

    private void appendRestoredAssistantText(String text) {
        AssistantStrip strip = this.createPlainAssistantStrip();
        this.removeAssistantWaitingIndicator(strip);
        this.ensureAssistantBody(strip);
        strip.textAccumulator.append(text);
        this.renderMarkdown(strip);
        this.appendTranscriptRow(strip.root);
    }

    private void appendRestoredToolCard(ChatToolCall tc) {
        ChatTooling tooling = this.host.chatTooling(this.selectedAgentMode());
        HttpTargetTools.HumanToolUsage label =
                HttpTargetTools.humanToolUsage(
                        tc.name(), tc.argumentsJson(), tooling.currentHistoryIndexForToolStatus());
        ChatStreamMessage.ToolApprovalRequest req =
                new ChatStreamMessage.ToolApprovalRequest(
                        tc.id(),
                        tc.name(),
                        tc.argumentsJson(),
                        label.title(),
                        label.detail(),
                        false);
        this.appendTranscriptRow(this.buildToolUsageCard(req));
    }

    private AgentMode selectedAgentMode() {
        Object o = this.agentModeCombo.getSelectedItem();
        return o instanceof AgentMode ? (AgentMode) o : AgentMode.ASK;
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
            if (m instanceof ChatStreamMessage.ToolApprovalRequest req
                    && session != null
                    && req.requiresApproval()) {
                session.postReply(new ChatStreamMessage.ToolApprovalResponse(req.toolCallId(), false));
            }
            return;
        }
        if (m instanceof ChatStreamMessage.ThinkingDelta td) {
            if (td.text().isEmpty()) {
                return;
            }
            AssistantStrip strip = this.transcriptActiveAssistantStrip.get();
            if (strip == null) {
                return;
            }
            removeAssistantWaitingIndicator(strip);
            ensureThinkingSection(strip);
            appendThinkingDelta(strip, td.text());
            scrollTranscriptToBottom();
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
        if (m instanceof ChatStreamMessage.ToolCallStarted ts) {
            AssistantStrip strip = this.transcriptActiveAssistantStrip.get();
            if (strip == null) {
                return;
            }
            updateWaitingIndicatorForToolCall(strip, ts.toolCallId(), ts.toolName());
            return;
        }
        if (m instanceof ChatStreamMessage.ToolApprovalRequest req) {
            if (session == null) {
                return;
            }
            AssistantStrip strip = this.transcriptActiveAssistantStrip.get();
            if (strip == null) {
                if (req.requiresApproval()) {
                    session.postReply(new ChatStreamMessage.ToolApprovalResponse(req.toolCallId(), false));
                }
                return;
            }
            removeAssistantWaitingIndicator(strip);
            flushMarkdownRender(strip);
            if (strip.body == null) {
                removeAssistantStripRowFromTranscript(strip.root);
            }
            RoundedPanel toolCard =
                    req.requiresApproval()
                            ? buildToolApprovalCard(
                                    req,
                                    approved ->
                                            session.postReply(
                                                    new ChatStreamMessage.ToolApprovalResponse(
                                                            req.toolCallId(), approved)))
                            : buildToolUsageCard(req);
            appendTranscriptRow(toolCard);
            AssistantStrip nextStrip = createPlainAssistantStrip();
            this.transcriptActiveAssistantStrip.set(nextStrip);
            appendTranscriptRow(nextStrip.root);
            this.transcriptList.revalidate();
            this.transcriptList.repaint();
            scrollTranscriptToBottom();
        }
    }

    private void installHistoryMentionPopupControls() {
        this.inputArea.addCaretListener(
                e -> {
                    if (this.historyMentionAt < 0) {
                        return;
                    }
                    if (!isHistoryMentionContextValid()) {
                        dismissHistoryMentionPopup();
                    } else {
                        SwingUtilities.invokeLater(this::refreshHistoryMentionIfOpen);
                    }
                });
        this.inputArea.addFocusListener(
                new FocusAdapter() {
                    @Override
                    public void focusLost(FocusEvent e) {
                        if (e.isTemporary()) {
                            return;
                        }
                        Component o = e.getOppositeComponent();
                        if (o != null
                                && historyMentionWindow != null
                                && (o == historyMentionWindow
                                        || SwingUtilities.isDescendingFrom(o, historyMentionWindow))) {
                            return;
                        }
                        if (o == null) {
                            /*
                             * LAFs often set opposite to null; defer so a click on the list
                             * can register as focus owner before we clear state.
                             */
                            SwingUtilities.invokeLater(
                                    () -> {
                                        if (AIAgentChatPanel.this.historyMentionAt < 0
                                                && AIAgentChatPanel.this.historyMentionReplaceAt
                                                        < 0) {
                                            return;
                                        }
                                        Component f =
                                                KeyboardFocusManager.getCurrentKeyboardFocusManager()
                                                        .getPermanentFocusOwner();
                                        if (f != null
                                                && historyMentionWindow != null
                                                && (f == historyMentionWindow
                                                        || SwingUtilities.isDescendingFrom(
                                                                f, historyMentionWindow))) {
                                            return;
                                        }
                                        if (AIAgentChatPanel.this.inputArea.isFocusOwner()) {
                                            return;
                                        }
                                        AIAgentChatPanel.this.dismissHistoryMentionPopup();
                                    });
                            return;
                        }
                        AIAgentChatPanel.this.dismissHistoryMentionPopup();
                    }
                });
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(this::dispatchHistoryMentionKeyEvent);
        AdjustmentListener scrollListener =
                e -> {
                    if (e.getValueIsAdjusting()) {
                        return;
                    }
                    if (AIAgentChatPanel.this.historyMentionAt >= 0
                            && AIAgentChatPanel.this.historyMentionWindow != null
                            && AIAgentChatPanel.this.historyMentionWindow.isVisible()) {
                        SwingUtilities.invokeLater(
                                AIAgentChatPanel.this::positionHistoryMentionWindow);
                    }
                };
        this.inputScroll.getVerticalScrollBar().addAdjustmentListener(scrollListener);
        this.inputScroll.getHorizontalScrollBar().addAdjustmentListener(scrollListener);
        this.inputArea.addHierarchyListener(
                e -> {
                    if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
                        if (!this.inputArea.isDisplayable()) {
                            this.dismissHistoryMentionPopup();
                        }
                    }
                });
    }

    private boolean isAtMentionWordStart(Document d, int at) {
        try {
            if (at <= 0) {
                return true;
            }
            return Character.isWhitespace(d.getText(at - 1, 1).charAt(0));
        } catch (BadLocationException e) {
            return false;
        }
    }

    /**
     * Defer to the end of the EDT so the caret has caught up to the last insert, and the text area
     * has had a chance to position the caret after {@code "@"} (the document listener can run
     * before the caret is updated, which would reject the mention every time).
     */
    private void onInputDocumentForHistoryMention() {
        SwingUtilities.invokeLater(this::updateHistoryMentionFromInputDocument);
    }

    private void updateHistoryMentionFromInputDocument() {
        if (this.historyMentionAt < 0) {
            return;
        }
        if (!isHistoryMentionContextValid()) {
            dismissHistoryMentionPopup();
        } else {
            refreshAndShowHistoryMentionPopup();
        }
    }

    private void refreshHistoryMentionIfOpen() {
        if (this.historyMentionAt < 0) {
            return;
        }
        if (!isHistoryMentionContextValid()) {
            dismissHistoryMentionPopup();
            return;
        }
        if (this.historyMentionWindow == null || !this.historyMentionWindow.isVisible()) {
            return;
        }
        this.fillHistoryMentionFromHost();
        this.historyMentionWindow.pack();
        this.positionHistoryMentionWindow();
        restoreMentionInputFocus();
    }

    /**
     * Pop-up repack and {@link JList} model updates can move focus off the composer; keep typing
     * in the text field after every mention UI refresh.
     */
    private void restoreMentionInputFocus() {
        SwingUtilities.invokeLater(
                () -> {
                    if (this.historyMentionAt >= 0) {
                        this.inputArea.requestFocusInWindow();
                    }
                });
    }

    private boolean isHistoryMentionContextValid() {
        if (this.historyMentionAt < 0) {
            return false;
        }
        Document d = this.inputArea.getDocument();
        int at = this.historyMentionAt;
        if (at < 0 || at >= d.getLength()) {
            return false;
        }
        try {
            if (!d.getText(at, 1).equals("@")) {
                return false;
            }
            int end = mentionTokenExclusiveEnd(d, at);
            int caret = this.inputArea.getCaretPosition();
            return caret >= at && caret <= end;
        } catch (BadLocationException e) {
            return false;
        }
    }

    /**
     * {@code @…} is followed by non-spacing characters until the first whitespace. Exclusive end
     * index, same semantics as end index in String for {@code d.getText(s, l)}.
     */
    private static int mentionTokenExclusiveEnd(Document d, int at) throws BadLocationException {
        if (at < 0
                || at >= d.getLength()
                || !d.getText(at, 1).equals("@")) {
            return at;
        }
        int i = at + 1;
        int len = d.getLength();
        while (i < len) {
            if (Character.isWhitespace(d.getText(i, 1).charAt(0))) {
                break;
            }
            i++;
        }
        return i;
    }

    /**
     * Filter text: typed characters after the {@code @}, up to the caret (or end of the token, if
     * the caret is past the typed segment).
     */
    private String getCurrentMentionQuery() {
        try {
            if (this.historyMentionAt < 0) {
                return "";
            }
            Document d = this.inputArea.getDocument();
            int at = this.historyMentionAt;
            if (at < 0
                    || at >= d.getLength()
                    || !d.getText(at, 1).equals("@")) {
                return "";
            }
            int end = mentionTokenExclusiveEnd(d, at);
            int caret = Math.min(this.inputArea.getCaretPosition(), end);
            int from = at + 1;
            if (from >= caret) {
                return "";
            }
            return d.getText(from, caret - from);
        } catch (BadLocationException e) {
            return "";
        }
    }

    private static boolean atPopupMentionMatches(AgentTabMention m, String qRaw) {
        String path = m.pathLabel() != null ? m.pathLabel() : "";
        if (RepeaterTabQueryMatcher.matches(qRaw, "", "", path)) {
            return true;
        }
        if (qRaw == null) {
            return true;
        }
        String t = qRaw.trim();
        if (t.isEmpty()) {
            return true;
        }
        String idStr = String.valueOf(m.requestNodeId());
        if (idStr.toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT))) {
            return true;
        }
        return ("#" + idStr).toLowerCase(Locale.ROOT).contains(t.toLowerCase(Locale.ROOT));
    }

    private void ensureHistoryMentionWindow() {
        if (this.historyMentionWindow != null) {
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        this.historyMentionWindow = owner != null ? new JWindow(owner) : new JWindow();
        /*
         * The text field must keep focus while the user types; the list/scroll is not
         * focus-traversal so that refresh does not steal focus. Mouse still selects rows;
         * navigation keys are handled from the key dispatcher while the input is focused.
         */
        this.historyMentionWindow.setFocusableWindowState(true);
        this.historyMentionWindow.setAutoRequestFocus(false);
        this.historyMentionWindow.setAlwaysOnTop(true);
        this.historyMentionList = new JList<>();
        this.historyMentionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        /*
         * Non-focusable: refresh runs on every typed character; a focusable JList can steal
         * focus from the text area. Arrow keys and Enter are still handled on the text field
         * via the key dispatcher; mouse selection still works.
         */
        this.historyMentionList.setFocusable(false);
        this.historyMentionList.setCellRenderer(
                (list, value, index, isSelected, cellHasFocus) -> {
                    String line =
                            value != null ? formatTabMentionLine((AgentTabMention) value) : "";
                    return this.historyMentionListCell.getListCellRendererComponent(
                            list, line, index, isSelected, cellHasFocus);
                });
        this.historyMentionList.setFixedCellHeight(20);
        this.historyMentionList.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (!SwingUtilities.isLeftMouseButton(e)) {
                            return;
                        }
                        int atSnap =
                                AIAgentChatPanel.this.historyMentionAt >= 0
                                        ? AIAgentChatPanel.this.historyMentionAt
                                        : AIAgentChatPanel.this.historyMentionReplaceAt;
                        if (atSnap < 0) {
                            return;
                        }
                        int i = AIAgentChatPanel.this.historyMentionList.locationToIndex(e.getPoint());
                        if (i < 0) {
                            return;
                        }
                        AIAgentChatPanel.this.historyMentionList.setSelectedIndex(i);
                        Object v = AIAgentChatPanel.this.historyMentionList.getModel().getElementAt(i);
                        if (v instanceof AgentTabMention info) {
                            AIAgentChatPanel.this.insertTabMentionText(info, atSnap);
                        }
                    }
                });
        this.historyMentionScroll = new JScrollPane(this.historyMentionList);
        this.historyMentionScroll.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        this.historyMentionScroll.setFocusable(false);
        this.historyMentionScroll.getVerticalScrollBar().setFocusable(false);
        this.historyMentionScroll.getHorizontalScrollBar().setFocusable(false);
        JPanel root = new JPanel(new BorderLayout());
        Color line = UIManager.getColor("Component.borderColor");
        if (line == null) {
            line = UIManager.getColor("Separator.foreground");
        }
        if (line != null) {
            root.setBorder(BorderFactory.createLineBorder(line, 1));
        }
        root.setFocusable(false);
        this.historyMentionWindow.setContentPane(root);
    }

    private static String formatTabMentionLine(AgentTabMention e) {
        String p = e.pathLabel() == null ? "" : e.pathLabel();
        if (p.isEmpty()) {
            return "#" + e.requestNodeId();
        }
        return p;
    }

    private static SimpleAttributeSet newMentionAttributeSet(int requestNodeId) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        a.addAttribute(REQUEST_NODE_MENTION_ATTR, requestNodeId);
        Color bg = UIManager.getColor("TextField.selectionBackground");
        if (bg == null) {
            bg = UIManager.getColor("TextArea.selectionBackground");
        }
        if (bg == null) {
            bg = new Color(0xC8D4F0);
        }
        Color fg = UIManager.getColor("TextField.selectionForeground");
        if (fg == null) {
            fg = UIManager.getColor("TextArea.foreground");
        }
        if (fg == null) {
            fg = UIManager.getColor("Label.foreground");
        }
        if (fg == null) {
            fg = Color.BLACK;
        }
        StyleConstants.setBackground(a, bg);
        StyleConstants.setForeground(a, fg);
        return a;
    }

    /** Attributes for normal body text and for the space after a mention (so typing does not stay “highlighted”). */
    private SimpleAttributeSet createPlainInputAttributes() {
        SimpleAttributeSet a = new SimpleAttributeSet();
        Color fg = this.inputArea.getForeground();
        Color bg = this.inputArea.getBackground();
        if (fg != null) {
            StyleConstants.setForeground(a, fg);
        }
        if (bg != null) {
            StyleConstants.setBackground(a, bg);
        }
        StyleConstants.setBold(a, false);
        StyleConstants.setItalic(a, false);
        StyleConstants.setUnderline(a, false);
        return a;
    }

    /** After inserting a mention, force the caret’s typing style to plain (not the chip style). */
    private void resetInputAttributesForFollowingText() {
        this.inputArea.setCharacterAttributes(createPlainInputAttributes(), true);
    }

    /**
     * Plain visible text; tab mentions are shown as in the composer, while {@link #compileUserMessageForAgent}
     * is what we persist and send to the model.
     */
    private String formatUserMessageForTranscript(String agentContent) {
        if (agentContent == null || agentContent.isEmpty() || !agentContent.contains("request_node_id:")) {
            return agentContent;
        }
        Map<Integer, String> labels = new HashMap<>();
        for (AgentTabMention t : this.host.agentTabMentionsForAtPopup()) {
            labels.put(t.requestNodeId(), formatTabMentionLine(t));
        }
        return REQUEST_NODE_ID_TOKEN
                .matcher(agentContent)
                .replaceAll(
                        (MatchResult mr) -> {
                            int id = Integer.parseInt(mr.group(1));
                            String label = labels.getOrDefault(id, "tab " + id);
                            String sp = mr.group(2);
                            if (sp == null || sp.isEmpty()) {
                                sp = " ";
                            }
                            return Matcher.quoteReplacement("@" + label + sp);
                        });
    }

    private String compileUserMessageForAgent() {
        Document d = this.inputArea.getDocument();
        if (!(d instanceof StyledDocument sd)) {
            return this.inputArea.getText().trim();
        }
        int len = sd.getLength();
        if (len == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        try {
            while (i < len) {
                Element el = sd.getCharacterElement(i);
                int s = el.getStartOffset();
                int e = Math.min(el.getEndOffset(), len);
                int from = Math.max(i, s);
                if (from >= e) {
                    i = e;
                    continue;
                }
                Object idObj = el.getAttributes().getAttribute(REQUEST_NODE_MENTION_ATTR);
                if (idObj instanceof Integer id) {
                    out.append("request_node_id:").append(id).append(" ");
                    int j = e;
                    while (j < len) {
                        Element el2 = sd.getCharacterElement(j);
                        Object id2 = el2.getAttributes().getAttribute(REQUEST_NODE_MENTION_ATTR);
                        if (!(id2 instanceof Integer i2) || !id.equals(i2)) {
                            break;
                        }
                        int e2 = Math.min(el2.getEndOffset(), len);
                        if (e2 > j) {
                            j = e2;
                        } else {
                            j++;
                        }
                    }
                    i = j;
                } else {
                    out.append(sd.getText(from, e - from));
                    i = e;
                }
            }
        } catch (BadLocationException ex) {
            this.host.logError(ex);
            return this.inputArea.getText().trim();
        }
        return out.toString().trim();
    }

    private void fillHistoryMentionFromHost() {
        Container root = (Container) this.historyMentionWindow.getContentPane();
        root.removeAll();
        List<AgentTabMention> all = this.host.agentTabMentionsForAtPopup();
        if (all == null || all.isEmpty()) {
            this.historyMentionList.setModel(new DefaultListModel<>());
            JLabel empty = new JLabel("No open repeater tabs");
            empty.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            root.add(empty, BorderLayout.CENTER);
            return;
        }
        String q = getCurrentMentionQuery();
        List<AgentTabMention> rows = new ArrayList<>();
        for (AgentTabMention m : all) {
            if (atPopupMentionMatches(m, q)) {
                rows.add(m);
            }
        }
        if (rows.isEmpty()) {
            this.historyMentionList.setModel(new DefaultListModel<>());
            JLabel empty = new JLabel("No matching open tabs");
            empty.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            root.add(empty, BorderLayout.CENTER);
            return;
        }
        DefaultListModel<AgentTabMention> m = new DefaultListModel<>();
        for (AgentTabMention row : rows) {
            m.addElement(row);
        }
        this.historyMentionList.setModel(m);
        if (m.getSize() > 0) {
            this.historyMentionList.setSelectedIndex(0);
        }
        int vis = Math.min(HISTORY_MENTION_MAX_VISIBLE_ROWS, Math.max(1, m.getSize()));
        this.historyMentionList.setVisibleRowCount(vis);
        this.historyMentionScroll.setViewportView(this.historyMentionList);
        int approxH = Math.min(200, 20 * vis + 8);
        this.historyMentionScroll.setPreferredSize(
                new Dimension(HISTORY_MENTION_PREFERRED_WIDTH, approxH));
        root.add(this.historyMentionScroll, BorderLayout.CENTER);
    }

    private void refreshAndShowHistoryMentionPopup() {
        this.ensureHistoryMentionWindow();
        this.historyMentionReplaceAt = this.historyMentionAt;
        this.fillHistoryMentionFromHost();
        this.historyMentionWindow.pack();
        this.positionHistoryMentionWindow();
        this.historyMentionWindow.setVisible(true);
        this.installHistoryMentionEnterKeyOverride();
        restoreMentionInputFocus();
    }

    /**
     * {@link JTextPane} maps Enter to {@link DefaultEditorKit#insertBreakAction} in the
     * focus {@link InputMap}; that path can bypass our {@link KeyEventDispatcher} so Enter
     * never completed the mention. We shadow that binding only while the popup is up.
     */
    private void installHistoryMentionEnterKeyOverride() {
        KeyStroke enterKs = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false);
        InputMap im = this.inputArea.getInputMap(JComponent.WHEN_FOCUSED);
        Object current = im.get(enterKs);
        if (!HISTORY_MENTION_IM_ENTER_ID.equals(current)) {
            this.historyMentionSavedImEnter = current;
        }
        if (this.historyMentionEnterAction == null) {
            this.historyMentionEnterAction =
                    new AbstractAction() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            AIAgentChatPanel.this.onHistoryMentionEnterFromInputMap(e);
                        }
                    };
        }
        im.put(enterKs, HISTORY_MENTION_IM_ENTER_ID);
        this.inputArea.getActionMap().put(HISTORY_MENTION_IM_ENTER_ID, this.historyMentionEnterAction);
    }

    private void onHistoryMentionEnterFromInputMap(ActionEvent e) {
        if (this.historyMentionAt < 0
                || this.historyMentionWindow == null
                || !this.historyMentionWindow.isVisible()) {
            this.uninstallHistoryMentionEnterKeyOverride();
            javax.swing.Action insertBreak =
                    this.inputArea.getActionMap().get(DefaultEditorKit.insertBreakAction);
            if (insertBreak != null) {
                insertBreak.actionPerformed(
                        new ActionEvent(
                                this.inputArea, ActionEvent.ACTION_PERFORMED, e.getActionCommand(), e.getWhen(), e.getModifiers()));
            }
            return;
        }
        int n =
                this.historyMentionList != null && this.historyMentionList.getModel() != null
                        ? this.historyMentionList.getModel().getSize()
                        : 0;
        if (n <= 0) {
            dismissHistoryMentionPopup();
        } else {
            applySelectedHistoryMention();
        }
    }

    private void uninstallHistoryMentionEnterKeyOverride() {
        KeyStroke enterKs = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false);
        InputMap im = this.inputArea.getInputMap(JComponent.WHEN_FOCUSED);
        if (HISTORY_MENTION_IM_ENTER_ID.equals(im.get(enterKs))) {
            if (this.historyMentionSavedImEnter != null) {
                im.put(enterKs, this.historyMentionSavedImEnter);
            } else {
                im.remove(enterKs);
            }
        }
        this.inputArea.getActionMap().remove(HISTORY_MENTION_IM_ENTER_ID);
        this.historyMentionSavedImEnter = null;
    }

    private void positionHistoryMentionWindow() {
        if (this.historyMentionAt < 0 || this.historyMentionWindow == null) {
            return;
        }
        try {
            @SuppressWarnings("deprecation")
            Rectangle r0 = this.inputArea.modelToView(this.historyMentionAt);
            this.historyMentionWindow.setSize(this.historyMentionWindow.getPreferredSize());
            int gap = 4;
            int w = this.historyMentionWindow.getWidth();
            int h = this.historyMentionWindow.getHeight();
            Point topLeft = new Point(r0.x, r0.y - h - gap);
            SwingUtilities.convertPointToScreen(topLeft, this.inputArea);
            java.awt.Toolkit tk = java.awt.Toolkit.getDefaultToolkit();
            java.awt.Dimension screen = tk.getScreenSize();
            if (topLeft.y < 0) {
                Point below = new Point(r0.x, r0.y + r0.height + gap);
                SwingUtilities.convertPointToScreen(below, this.inputArea);
                topLeft = below;
            }
            if (topLeft.x + w > screen.width) {
                topLeft.x = Math.max(0, screen.width - w);
            }
            if (topLeft.y + h > screen.height) {
                topLeft.y = Math.max(0, screen.height - h);
            }
            if (topLeft.x < 0) {
                topLeft.x = 0;
            }
            this.historyMentionWindow.setLocation(topLeft);
        } catch (BadLocationException e) {
            this.dismissHistoryMentionPopup();
        }
    }

    /**
     * Pre-dispatch for list navigation. {@link #installHistoryMentionEnterKeyOverride} only applies
     * when the {@link #inputArea} has focus, so when the list has the caret we handle Enter here.
     */
    private boolean dispatchHistoryMentionKeyEvent(KeyEvent e) {
        if (e.getID() != KeyEvent.KEY_PRESSED) {
            return false;
        }
        if (this.historyMentionAt < 0
                || this.historyMentionWindow == null
                || !this.historyMentionWindow.isVisible()) {
            return false;
        }
        if (e.isControlDown() || e.isMetaDown()) {
            return false;
        }
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
        if (focus == null) {
            return false;
        }
        if (focus != this.inputArea
                && (this.historyMentionWindow == null
                        || !SwingUtilities.isDescendingFrom(focus, this.historyMentionWindow))) {
            return false;
        }
        int code = e.getKeyCode();
        int n =
                this.historyMentionList != null && this.historyMentionList.getModel() != null
                        ? this.historyMentionList.getModel().getSize()
                        : 0;
        if (code == KeyEvent.VK_ENTER) {
            if (n <= 0) {
                if (this.historyMentionWindow != null
                        && focus != this.inputArea
                        && SwingUtilities.isDescendingFrom(focus, this.historyMentionWindow)) {
                    e.consume();
                    dismissHistoryMentionPopup();
                    return true;
                }
                return false;
            }
            if (focus == this.inputArea) {
                return false;
            }
            e.consume();
            applySelectedHistoryMention();
            return true;
        }
        if (code == KeyEvent.VK_ESCAPE) {
            e.consume();
            dismissHistoryMentionPopup();
            return true;
        }
        if (n <= 0) {
            return false;
        }
        if (code == KeyEvent.VK_TAB) {
            e.consume();
            applySelectedHistoryMention();
            return true;
        }
        if (code == KeyEvent.VK_UP) {
            e.consume();
            int i = this.historyMentionList.getSelectedIndex();
            if (i < 0) {
                i = 0;
            } else {
                i = Math.max(0, i - 1);
            }
            this.historyMentionList.setSelectedIndex(i);
            this.historyMentionList.ensureIndexIsVisible(i);
            return true;
        }
        if (code == KeyEvent.VK_DOWN) {
            e.consume();
            int i = this.historyMentionList.getSelectedIndex();
            if (i < 0) {
                i = 0;
            } else {
                i = Math.min(n - 1, i + 1);
            }
            this.historyMentionList.setSelectedIndex(i);
            this.historyMentionList.ensureIndexIsVisible(i);
            return true;
        }
        return false;
    }

    private void applySelectedHistoryMention() {
        if (this.historyMentionList == null) {
            return;
        }
        int n = this.historyMentionList.getModel().getSize();
        if (n == 0) {
            return;
        }
        int i = this.historyMentionList.getSelectedIndex();
        if (i < 0) {
            i = 0;
        }
        Object v = this.historyMentionList.getModel().getElementAt(i);
        if (!(v instanceof AgentTabMention info)) {
            return;
        }
        int at =
                this.historyMentionAt >= 0
                        ? this.historyMentionAt
                        : this.historyMentionReplaceAt;
        insertTabMentionText(info, at);
    }

    private void insertTabMentionText(AgentTabMention info, int at) {
        if (at < 0) {
            at = this.historyMentionAt >= 0 ? this.historyMentionAt : this.historyMentionReplaceAt;
        }
        if (at < 0) {
            return;
        }
        try {
            Document d = this.inputArea.getDocument();
            if (at > d.getLength() - 1) {
                dismissHistoryMentionPopup();
                return;
            }
            if (!d.getText(at, 1).equals("@")) {
                dismissHistoryMentionPopup();
                return;
            }
            int end = mentionTokenExclusiveEnd(d, at);
            int span = end - at;
            if (span < 1) {
                dismissHistoryMentionPopup();
                return;
            }
            if (!(d instanceof StyledDocument doc)) {
                dismissHistoryMentionPopup();
                return;
            }
            int queryStart = at + 1;
            int qLen = end - queryStart;
            if (qLen > 0) {
                doc.remove(queryStart, qLen);
            }
            String display = formatTabMentionLine(info);
            SimpleAttributeSet mentionAttrs = newMentionAttributeSet(info.requestNodeId());
            doc.remove(at, 1);
            String atAndName = "@" + display;
            doc.insertString(at, atAndName, mentionAttrs);
            int afterMention = at + atAndName.length();
            doc.insertString(afterMention, " ", createPlainInputAttributes());
            this.inputArea.setCaretPosition(afterMention + 1);
            resetInputAttributesForFollowingText();
            dismissHistoryMentionPopup();
            this.inputArea.requestFocusInWindow();
        } catch (BadLocationException e) {
            this.host.logError(e);
            dismissHistoryMentionPopup();
        }
    }

    private void dismissHistoryMentionPopup() {
        this.uninstallHistoryMentionEnterKeyOverride();
        this.historyMentionAt = -1;
        this.historyMentionReplaceAt = -1;
        if (this.historyMentionWindow != null) {
            this.historyMentionWindow.setVisible(false);
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

    /**
     * Replaces the generic "..." waiting indicator with a concrete "calling tool: name" cue when the
     * model has begun streaming a tool call but the round has not yet finished. Multiple tool calls
     * in the same round are joined with commas in announcement order. No-op once the indicator is
     * gone (i.e. real content or the actual tool card has already been rendered).
     */
    private void updateWaitingIndicatorForToolCall(AssistantStrip strip, String toolCallId, String toolName) {
        JLabel w = strip.waitingIndicator;
        if (w == null || w.getParent() == null) {
            return;
        }
        String key = toolCallId != null && !toolCallId.isBlank() ? toolCallId : toolName;
        if (key == null || key.isBlank()) {
            return;
        }
        String displayName = toolName != null && !toolName.isBlank() ? toolName : "tool";
        String prev = strip.announcedToolCalls.put(key, displayName);
        if (displayName.equals(prev)) {
            return;
        }
        StringBuilder names = new StringBuilder();
        for (String n : strip.announcedToolCalls.values()) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(n);
        }
        String label =
                strip.announcedToolCalls.size() == 1
                        ? "Calling tool: " + names + "..."
                        : "Calling tools: " + names + "...";
        w.setText(label);
    }

    /**
     * Tool usage card: title is the action summary. For request mutations, diffs a preview of the tool against the
     * current in-editor request (in memory only); otherwise shows {@code humanDetail} when non-blank.
     */
    private RoundedPanel buildToolUsageCard(ChatStreamMessage.ToolApprovalRequest req) {
        return buildToolUsageCardShell(req, null);
    }

    private RoundedPanel buildToolUsageCardShell(
            ChatStreamMessage.ToolApprovalRequest req, Consumer<Boolean> onApprovalResolved) {
        String t = req.humanTitle() != null ? req.humanTitle().trim() : "";
        if (t.isEmpty()) {
            t = "Working…";
        }
        String d = req.humanDetail() != null ? req.humanDetail().trim() : "";

        RoundedPanel card = new RoundedPanel();
        card.putClientProperty(TOOL_USAGE_BORDER_MARK, Boolean.TRUE);
        card.setLayout(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        card.setOpaque(false);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        applyToolUsageBorderOnlyTheme(card);

        JPanel textCol = new JPanel();
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.PAGE_AXIS));
        textCol.setOpaque(false);
        textCol.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel(t);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        Font lf = UIManager.getFont("Label.font");
        Color muted = UIManager.getColor("Label.disabledForeground");
        if (lf != null) {
            titleLabel.setFont(lf.deriveFont(Font.ITALIC));
        }
        if (muted != null) {
            titleLabel.setForeground(muted);
        }
        textCol.add(titleLabel);

        JComponent detailComp = buildToolCardDetailFromPreview(req, lf, muted, d);
        if (detailComp != null) {
            textCol.add(Box.createVerticalStrut(4));
            textCol.add(detailComp);
            textCol.add(Box.createVerticalStrut(4));
        }

        card.add(textCol, BorderLayout.CENTER);
        if (onApprovalResolved != null) {
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            buttons.setOpaque(false);
            StyledButton noBtn = new StyledButton("Cancel");
            StyledButton okBtn = new StyledButton("OK");
            noBtn.setStyle(StyledButton.Style.DEFAULT);
            okBtn.setStyle(StyledButton.Style.AI);
            AtomicBoolean resolved = new AtomicBoolean(false);
            Runnable removeButtons =
                    () -> {
                        if (buttons.getParent() == card) {
                            card.remove(buttons);
                            card.revalidate();
                            card.repaint();
                        }
                    };
            Runnable resolveNo =
                    () -> {
                        if (resolved.compareAndSet(false, true)) {
                            removeButtons.run();
                            onApprovalResolved.accept(false);
                        }
                    };
            Runnable resolveOk =
                    () -> {
                        if (resolved.compareAndSet(false, true)) {
                            removeButtons.run();
                            onApprovalResolved.accept(true);
                        }
                    };
            noBtn.addActionListener(e -> resolveNo.run());
            okBtn.addActionListener(e -> resolveOk.run());
            buttons.add(noBtn);
            buttons.add(Box.createHorizontalStrut(8));
            buttons.add(okBtn);
            card.add(buttons, BorderLayout.SOUTH);
        }
        return card;
    }

    private JComponent buildToolCardDetailFromPreview(
            ChatStreamMessage.ToolApprovalRequest req, Font lf, Color muted, String humanDetail) {
        AgentToolContext actx = this.host.agentToolContextForToolPreview();
        if (actx != null) {
            try {
                final HttpRequest[] beforeBox = {null};
                this.host.runOnEdtAndWait(
                        () -> {
                            int i = actx.currentHistoryIndex();
                            if (i < 0 || i >= actx.historySize()) {
                                return;
                            }
                            beforeBox[0] = actx.requestForHistoryIndex().apply(i);
                        });
                HttpRequest beforeR = beforeBox[0];
                if (beforeR != null) {
                    HttpRequest afterR =
                            HttpTargetTools.tryPreviewRequestMutation(
                                    req.toolName(), req.argumentsJson(), beforeR);
                    if (afterR != null) {
                        String wBefore = HttpTargetTools.requestWireTextForDiff(beforeR);
                        String wAfter = HttpTargetTools.requestWireTextForDiff(afterR);
                        List<LineDiffer.UnifiedLineRow> unified = LineDiffer.unifiedLineDiffData(wBefore, wAfter);
                        if (unified == null) {
                            LineDiffer.LineDiffData data = LineDiffer.lineDiffData(wBefore, wAfter);
                            if (!data.removed().isEmpty() || !data.added().isEmpty()) {
                                return buildToolCardSplitDiffPanel(
                                        LineDiffer.formatWithLineGutter(
                                                LineDiffer.truncateDisplayRowsForToolCard(
                                                        data.removed(), 6, 6)),
                                        LineDiffer.formatWithLineGutter(
                                                LineDiffer.truncateDisplayRowsForToolCard(
                                                        data.added(), 6, 6)),
                                        lf);
                            }
                        } else if (!unified.isEmpty()) {
                            return buildToolCardUnifiedDiffPanel(
                                    LineDiffer.truncateUnifiedForToolCard(unified, 6, 6), lf);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (humanDetail != null && !humanDetail.isEmpty()) {
            return buildToolCardDetailText(humanDetail, lf, muted);
        }
        return null;
    }

    /** Plain text; wraps at a target width in characters (roughly) so long change summaries do not force a wide card. */
    private static JComponent buildToolCardDetailText(String text, Font baseFont, Color fg) {
        JTextArea area = new JTextArea(text);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (baseFont != null) {
            area.setFont(baseFont.deriveFont(Font.PLAIN, baseFont.getSize2D() - 1f));
        }
        if (fg != null) {
            area.setForeground(fg);
        }
        int columns = 52;
        if (baseFont != null) {
            columns = Math.max(36, Math.min(64, (int) (baseFont.getSize2D() * 2.0)));
        }
        area.setRows(0);
        area.setColumns(columns);
        return area;
    }

    private static int toolCardDetailColumns(Font baseFont) {
        if (baseFont != null) {
            return Math.max(36, Math.min(64, (int) (baseFont.getSize2D() * 2.0)));
        }
        return 52;
    }

    /**
     * Extra width for 1-based line number column, box-drawing separator, and space before request body text, on top
     * of {@link #toolCardDetailColumns(Font)}.
     */
    private static int toolCardDiffTextColumns(Font baseFont) {
        return toolCardDetailColumns(baseFont) + 5;
    }

    private static boolean isDarkLaf() {
        return UIManager.getBoolean("laf.dark");
    }

    /** Red then green blocks; used when the LCS table is too large for a unified walk. */
    private static JComponent buildToolCardSplitDiffPanel(String before, String after, Font baseFont) {
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.PAGE_AXIS));
        column.setOpaque(false);
        column.setAlignmentX(Component.LEFT_ALIGNMENT);
        if (before != null && !before.isEmpty()) {
            column.add(
                    buildToolCardDiffTextArea(
                            before, true, baseFont, toolCardDiffTextColumns(baseFont)));
        }
        if (before != null
                && !before.isEmpty()
                && after != null
                && !after.isEmpty()) {
            column.add(Box.createVerticalStrut(4));
        }
        if (after != null && !after.isEmpty()) {
            column.add(
                    buildToolCardDiffTextArea(
                            after, false, baseFont, toolCardDiffTextColumns(baseFont)));
        }
        return column;
    }

    /**
     * Single column: old line, new line, and ellipses interleaved in file order (unified diff); hunk / omitted rows use
     * a neutral style.
     */
    private static JComponent buildToolCardUnifiedDiffPanel(
            List<LineDiffer.UnifiedLineRow> rows, Font baseFont) {
        int w = LineDiffer.unifiedGutterWidth(rows);
        int cols = toolCardDiffTextColumns(baseFont);
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.PAGE_AXIS));
        column.setOpaque(false);
        column.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (LineDiffer.UnifiedLineRow r : rows) {
            if (r instanceof LineDiffer.UnifiedLineRow.Old o) {
                column.add(
                        buildToolCardDiffTextArea(
                                LineDiffer.formatGutterForUnifiedLine(w, o.line1Before(), o.text()),
                                true,
                                baseFont,
                                cols));
            } else if (r instanceof LineDiffer.UnifiedLineRow.New n) {
                column.add(
                        buildToolCardDiffTextArea(
                                LineDiffer.formatGutterForUnifiedLine(w, n.line1After(), n.text()),
                                false,
                                baseFont,
                                cols));
            } else if (r instanceof LineDiffer.UnifiedLineRow.HunkSeparator) {
                column.add(
                        buildToolCardDiffMetaTextArea(
                                LineDiffer.formatGutterForUnifiedHunk(w), baseFont, cols));
            } else if (r instanceof LineDiffer.UnifiedLineRow.MiddleOmitted mo) {
                column.add(
                        buildToolCardDiffMetaTextArea(
                                LineDiffer.formatGutterForUnifiedMiddleOmitted(w, mo.rowCount()),
                                baseFont,
                                cols));
            }
        }
        return column;
    }

    private static JTextArea buildToolCardDiffMetaTextArea(String text, Font baseFont, int columns) {
        JTextArea area = new JTextArea(text);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(true);
        area.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        float size = 12f;
        if (baseFont != null) {
            size = baseFont.getSize2D() - 1f;
        }
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Math.max(10, (int) size)));
        area.setRows(0);
        area.setColumns(columns);
        area.setMinimumSize(new Dimension(0, 0));
        area.setBackground(toolCardDiffMetaBackground());
        area.setForeground(toolCardDiffMetaForeground());
        area.putClientProperty(TOOL_CARD_DIFF_STYLE_KEY, "meta");
        return area;
    }

    private static JTextArea buildToolCardDiffTextArea(
            String line, boolean originalDeleted, Font baseFont, int columns) {
        JTextArea area = new JTextArea(line);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(true);
        area.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        float size = 12f;
        if (baseFont != null) {
            size = baseFont.getSize2D() - 1f;
        }
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Math.max(10, (int) size)));
        area.setRows(0);
        area.setColumns(columns);
        area.setMinimumSize(new Dimension(0, 0));
        if (originalDeleted) {
            area.setBackground(toolCardDiffRedBackground());
            area.setForeground(toolCardDiffRedForeground());
            area.putClientProperty(TOOL_CARD_DIFF_STYLE_KEY, "removed");
        } else {
            area.setBackground(toolCardDiffGreenBackground());
            area.setForeground(toolCardDiffGreenForeground());
            area.putClientProperty(TOOL_CARD_DIFF_STYLE_KEY, "added");
        }
        return area;
    }

    private static Color toolCardDiffRedBackground() {
        return isDarkLaf() ? new Color(64, 36, 36) : new Color(255, 230, 230);
    }

    private static Color toolCardDiffRedForeground() {
        return isDarkLaf() ? new Color(255, 170, 170) : new Color(140, 30, 30);
    }

    private static Color toolCardDiffGreenBackground() {
        return isDarkLaf() ? new Color(36, 64, 40) : new Color(230, 255, 230);
    }

    private static Color toolCardDiffGreenForeground() {
        return isDarkLaf() ? new Color(170, 255, 190) : new Color(25, 100, 45);
    }

    private static Color toolCardDiffMetaBackground() {
        return isDarkLaf() ? new Color(48, 48, 48) : new Color(245, 245, 245);
    }

    private static Color toolCardDiffMetaForeground() {
        return isDarkLaf() ? new Color(160, 160, 160) : new Color(90, 90, 90);
    }

    /** Status line plus Cancel / OK controls; {@code onResolved} receives {@code true} for OK and {@code false} for Cancel. */
    private RoundedPanel buildToolApprovalCard(
            ChatStreamMessage.ToolApprovalRequest req, Consumer<Boolean> onResolved) {
        return buildToolUsageCardShell(req, onResolved);
    }

    private static final int MARKDOWN_RENDER_DELAY_MS = 30;

    /**
     * Collapsible "Thinking" block above the assistant markdown body; created on first {@link
     * treepeater.ai.ChatStreamMessage.ThinkingDelta}. Starts expanded.
     */
    private void ensureThinkingSection(AssistantStrip strip) {
        if (strip.thinkingSection != null) {
            return;
        }
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.PAGE_AXIS));
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        Font lf = UIManager.getFont("Label.font");
        Color muted = UIManager.getColor("Label.disabledForeground");

        JButton toggle = new JButton("▼ Thinking");
        toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        toggle.setBorderPainted(false);
        toggle.setContentAreaFilled(false);
        toggle.setFocusPainted(false);
        toggle.setHorizontalAlignment(SwingConstants.LEFT);
        if (lf != null) {
            toggle.setFont(lf.deriveFont(Font.PLAIN, lf.getSize2D() - 0.5f));
        }
        if (muted != null) {
            toggle.setForeground(muted);
        }

        JTextArea thinkingArea = new JTextArea();
        thinkingArea.setEditable(false);
        thinkingArea.setOpaque(false);
        thinkingArea.setLineWrap(true);
        thinkingArea.setWrapStyleWord(true);
        thinkingArea.setBorder(BorderFactory.createEmptyBorder(4, 12, 0, 0));
        if (lf != null) {
            thinkingArea.setFont(lf.deriveFont(Font.PLAIN, lf.getSize2D() - 1f));
        }
        if (muted != null) {
            thinkingArea.setForeground(muted);
        }
        int columns = 56;
        if (lf != null) {
            columns = Math.max(44, Math.min(72, (int) (lf.getSize2D() * 2.2)));
        }
        thinkingArea.setColumns(columns);
        thinkingArea.setRows(5);

        JScrollPane scroll = new JScrollPane(thinkingArea);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setVisible(true);

        toggle.addActionListener(
                e -> {
                    strip.thinkingExpanded = !strip.thinkingExpanded;
                    scroll.setVisible(strip.thinkingExpanded);
                    toggle.setText(strip.thinkingExpanded ? "▼ Thinking" : "▶ Thinking");
                    section.revalidate();
                    section.repaint();
                    this.transcriptList.revalidate();
                    this.transcriptList.repaint();
                    scrollTranscriptToBottom();
                });

        section.add(toggle);
        section.add(scroll);
        strip.column.add(section, 0);

        strip.thinkingSection = section;
        strip.thinkingArea = thinkingArea;
        strip.thinkingExpanded = true;

        this.transcriptList.revalidate();
        this.transcriptList.repaint();
    }

    private void appendThinkingDelta(AssistantStrip strip, String delta) {
        if (delta == null || delta.isEmpty() || strip.thinkingArea == null) {
            return;
        }
        strip.thinkingAccumulator.append(delta);
        strip.thinkingArea.setText(strip.thinkingAccumulator.toString());
        strip.thinkingArea.setCaretPosition(strip.thinkingAccumulator.length());
        this.transcriptList.revalidate();
        this.transcriptList.repaint();
    }

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
        if (c instanceof JTextArea ta) {
            Object style = ta.getClientProperty(TOOL_CARD_DIFF_STYLE_KEY);
            if (style != null) {
                applyToolCardDiffTextAreaForStyle(ta, style);
                return;
            }
        }
        if (c instanceof RoundedPanel rp) {
            if (Boolean.TRUE.equals(rp.getClientProperty(TOOL_USAGE_BORDER_MARK))) {
                applyToolUsageBorderOnlyTheme(rp);
                for (Component child : rp.getComponents()) {
                    this.refreshTranscriptRowTheme(child);
                }
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

    private static void applyToolCardDiffTextAreaForStyle(JTextArea area, Object style) {
        if ("removed".equals(style)) {
            area.setBackground(toolCardDiffRedBackground());
            area.setForeground(toolCardDiffRedForeground());
        } else if ("added".equals(style)) {
            area.setBackground(toolCardDiffGreenBackground());
            area.setForeground(toolCardDiffGreenForeground());
        } else if ("meta".equals(style)) {
            area.setBackground(toolCardDiffMetaBackground());
            area.setForeground(toolCardDiffMetaForeground());
        }
    }

    private void scrollTranscriptToBottom() {
        if (this.transcriptScrollCoalescePending) {
            return;
        }
        this.transcriptScrollCoalescePending = true;
        SwingUtilities.invokeLater(
                () -> {
                    this.transcriptScrollCoalescePending = false;
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
        final StringBuilder thinkingAccumulator = new StringBuilder();
        /** Tool-call ids that have already updated the waiting indicator (avoid duplicates). */
        final java.util.LinkedHashMap<String, String> announcedToolCalls = new java.util.LinkedHashMap<>();
        JEditorPane body;
        Timer renderTimer;
        JPanel thinkingSection;
        JTextArea thinkingArea;
        boolean thinkingExpanded;

        AssistantStrip(JPanel root, JPanel column, JLabel waitingIndicator) {
            this.root = root;
            this.column = column;
            this.waitingIndicator = waitingIndicator;
        }
    }
}
