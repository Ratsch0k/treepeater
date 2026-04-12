package treepeater.requestResponse.toolbar;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.exceptions.OllamaBaseException;
import io.github.ollama4j.models.chat.OllamaChatMessage;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatRequestBuilder;
import io.github.ollama4j.models.chat.OllamaChatResult;
import io.github.ollama4j.models.generate.OllamaTokenHandler;

import treepeater.Treepeater;
import treepeater.components.RoundedPanel;
import treepeater.components.StyledButton;
import treepeater.icons.WandIcon;

public class MagicToolbarTab {
    private static final String OLLAMA_URL = "http://127.0.0.1:11434";
    private static final String OLLAMA_MODEL = "qwen3.5";

    private final ToolbarIconButton button;
    private final JPanel content;
    private final TranscriptListPanel transcriptList;
    private final JScrollPane transcriptScroll;
    private final Component transcriptBottomGlue = Box.createVerticalGlue();
    private final JTextArea inputArea;
    private final StyledButton sendButton;
    private final OllamaAPI ollamaApi;
    private final List<OllamaChatMessage> conversation;
    private final AtomicReference<SwingWorker<OllamaChatResult, Void>> activeChatWorker;

    public MagicToolbarTab() {
        this.button = new ToolbarIconButton(new WandIcon());
        this.content = new JPanel(new BorderLayout());
        this.transcriptList = new TranscriptListPanel();

        this.transcriptScroll = new JScrollPane(this.transcriptList);
        this.transcriptScroll.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        this.transcriptScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        this.transcriptScroll.getVerticalScrollBar().setUnitIncrement(16);
        this.transcriptScroll.setPreferredSize(new Dimension(0, 200));
        this.transcriptScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                MagicToolbarTab.this.constrainAllMessageBubbles();
            }
        });

        this.inputArea = new JTextArea(4, 0);
        this.inputArea.setLineWrap(true);
        this.inputArea.setWrapStyleWord(true);

        this.sendButton = new StyledButton("Send");
        this.sendButton.setStyle(StyledButton.Style.AI);
        this.sendButton.setPreferredSize(new Dimension(80, 22));

        this.ollamaApi = new OllamaAPI(OLLAMA_URL);
        this.conversation = new ArrayList<>();
        this.activeChatWorker = new AtomicReference<>();

        this.sendButton.setAction(new AbstractAction("Send") {
            @Override
            public void actionPerformed(ActionEvent e) {
                MagicToolbarTab.this.onSend();
            }
        });

        this.content.add(this.buildContent(), BorderLayout.CENTER);
    }

    public JButton getButton() {
        return this.button;
    }

    public JPanel getContent() {
        return this.content;
    }

    public void applyLocalTheme() {
        this.button.applyLocalTheme();
        refreshMessageBubbleThemes();
    }

    private JPanel buildContent() {
        JPanel panel = new JPanel(new BorderLayout());

        panel.add(new ToolbarTabTitle("Magic"), BorderLayout.NORTH);

        JPanel inputRow = new JPanel(new GridBagLayout());
        inputRow.setOpaque(false);
        inputRow.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.FIRST_LINE_START;
        constraints.insets = new Insets(0, 0, 0, 0);
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.weighty = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        JSeparator separator = new JSeparator(JSeparator.HORIZONTAL);
        inputRow.add(separator, constraints);

        constraints.gridy = 1;
        constraints.gridwidth = 1;
        constraints.fill = GridBagConstraints.BOTH;
        JScrollPane inputScroll = new JScrollPane(this.inputArea);
        inputScroll.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 0));
        inputRow.add(inputScroll, constraints);

        constraints.gridx = 1;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.EAST;
        constraints.insets = new Insets(0, 0, 0, 8);
        inputRow.add(this.sendButton, constraints);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(this.transcriptScroll, BorderLayout.CENTER);
        body.add(inputRow, BorderLayout.SOUTH);

        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private void onSend() {
        String text = this.inputArea.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        SwingWorker<OllamaChatResult, Void> running = this.activeChatWorker.get();
        if (running != null && !running.isDone()) {
            return;
        }

        this.inputArea.setText("");
        addMessageBubble("You", text);

        List<OllamaChatMessage> messages = new ArrayList<>(this.conversation);
        messages.add(new OllamaChatMessage(OllamaChatMessageRole.USER, text));

        this.sendButton.setEnabled(false);

        MessageBubble assistantBubble = createMessageBubble("Assistant", "");
        appendBubble(assistantBubble.panel());
        final JTextArea streamingBody = assistantBubble.body();
        final RoundedPanel streamingCard = assistantBubble.panel();

        SwingWorker<OllamaChatResult, Void> worker = new SwingWorker<>() {
            private final List<OllamaChatMessage> requestMessages = messages;

            private final OllamaTokenHandler streamHandler = chunk -> {
                SwingWorker<OllamaChatResult, Void> w = MagicToolbarTab.this.activeChatWorker.get();
                if (w == null || w.isCancelled()) {
                    return;
                }
                OllamaChatMessage msg = chunk.getMessage();
                if (msg == null) {
                    return;
                }
                String delta = msg.getContent();
                if (delta == null || delta.isEmpty()) {
                    return;
                }
                SwingUtilities.invokeLater(() -> {
                    SwingWorker<OllamaChatResult, Void> w2 = MagicToolbarTab.this.activeChatWorker.get();
                    if (w2 == null || w2.isCancelled()) {
                        return;
                    }
                    streamingBody.append(delta);
                    MagicToolbarTab.this.applyBubbleWidthConstraint(streamingCard);
                    MagicToolbarTab.this.transcriptList.revalidate();
                    MagicToolbarTab.this.scrollTranscriptToBottom();
                });
            };

            @Override
            protected OllamaChatResult doInBackground() throws Exception {
                OllamaChatRequest request =
                        OllamaChatRequestBuilder.getInstance(OLLAMA_MODEL)
                                .withMessages(this.requestMessages)
                                .withStreaming()
                                .build();
                return MagicToolbarTab.this.ollamaApi.chatStreaming(request, this.streamHandler);
            }

            @Override
            protected void done() {
                try {
                    if (isCancelled()) {
                        return;
                    }
                    OllamaChatResult result = get();
                    List<OllamaChatMessage> history = result.getChatHistory();
                    MagicToolbarTab.this.conversation.clear();
                    if (history != null) {
                        MagicToolbarTab.this.conversation.addAll(history);
                    }
                } catch (Exception ex) {
                    logError(ex);
                    addMessageBubble("Error", errorMessage(ex));
                } finally {
                    MagicToolbarTab.this.sendButton.setEnabled(true);
                    if (MagicToolbarTab.this.activeChatWorker.get() == this) {
                        MagicToolbarTab.this.activeChatWorker.set(null);
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

    private static String errorMessage(Throwable ex) {
        if (ex instanceof OllamaBaseException) {
            return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        }
        if (ex instanceof IOException) {
            return ex.getMessage() != null ? ex.getMessage() : "I/O error talking to Ollama.";
        }
        Throwable c = ex.getCause();
        if (c != null && c.getMessage() != null) {
            return c.getMessage();
        }
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
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
