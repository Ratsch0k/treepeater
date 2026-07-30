package treepeater.requestResponse.toolbar.inspector;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.CaretListener;
import javax.swing.plaf.basic.BasicTextUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.DefaultHighlighter.DefaultHighlightPainter;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.core.Range;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;

import treepeater.Treepeater;
import treepeater.Utilities;
import treepeater.components.RoundedPanel;
import treepeater.icons.InspectorIcon;
import treepeater.requestResponse.RequestResponsePanel;
import treepeater.requestResponse.toolbar.ToolbarIconButton;
import treepeater.requestResponse.toolbar.ToolbarTabTitle;

/**
 * Repeater-style "Inspector" tab.
 *
 * <p>Watches the current text selection inside the request and response editors of the active tab. When the selected
 * text looks like it carries a well-known encoding (URL percent-encoding, HTML entities, or Base64) the decoded value
 * is shown in an editable field. Editing the decoded value and pressing <em>Apply</em> re-encodes it with the detected
 * scheme and writes it back into the request editor at the original selection offsets &mdash; mirroring the behaviour of
 * Burp Repeater's Inspector.</p>
 *
 * <p>The response editor is read-only, so decoded response selections are shown for inspection but cannot be applied
 * back.</p>
 */
public class InspectorToolbarTab {
    /** How the selected bytes were interpreted. */
    private enum Encoding {
        PLAIN("Plain text"),
        URL("URL-encoded"),
        HTML("HTML entities"),
        BASE64("Base64");

        private final String label;

        Encoding(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return this.label;
        }
    }

    /** Which editor a selection came from. */
    private enum Source {
        REQUEST,
        RESPONSE,
        NONE
    }

    private static final String SYNTAX_TEXT_AREA_NAME = "syntaxTextArea";

    private final ToolbarIconButton button;
    private final JPanel content;

    private final JLabel sourceValue = valueLabel();
    private final JLabel detectedValue = valueLabel();
    private final JTextArea rawArea = new RevalidatingTextArea();
    private final JTextArea decodedArea = new RevalidatingTextArea();
    private final JComboBox<Encoding> encodingSelector = new JComboBox<>(Encoding.values());
    private final JButton applyButton = new JButton("Apply");
    private final JLabel statusLine = valueLabel();

    private RoundedPanel selectionCard;
    private RoundedPanel decodedCard;

    private final List<EditorCaretBinding> editorCaretBindings = new ArrayList<>();

    // Bound active tab (set by TreepeaterUI on tab-selection change).
    private RequestResponsePanel activePanel;

    // Snapshot of the selection currently reflected in the UI.
    private Source currentSource = Source.NONE;
    private Range currentOffsets;
    private String lastRawSeen;
    private Encoding autoEncoding = Encoding.PLAIN;

    // True while we programmatically mutate fields, to suppress reactive listeners.
    private boolean updatingFromSelection;

    public InspectorToolbarTab() {
        this.button = new ToolbarIconButton(new InspectorIcon());
        this.content = new JPanel(new BorderLayout());
        this.content.add(this.buildContent(), BorderLayout.CENTER);

        this.applyButton.addActionListener(e -> this.applyDecodedValue());
        this.encodingSelector.addActionListener(e -> {
            if (this.updatingFromSelection) {
                return;
            }
            // Manual scheme override: re-decode the raw selection with the chosen scheme.
            this.reDecodeWithSelectedScheme();
        });

        this.refreshApplyEnabled();
    }

    public JButton getButton() {
        return this.button;
    }

    public JPanel getContent() {
        return this.content;
    }

    public void applyLocalTheme() {
        this.button.applyLocalTheme();
        if (this.selectionCard != null) {
            applyInfoCardTheme(this.selectionCard);
        }
        if (this.decodedCard != null) {
            applyInfoCardTheme(this.decodedCard);
        }
    }

    /** Binds the Inspector to the currently selected request/response tab. */
    public void setActivePanel(RequestResponsePanel panel) {
        this.unbindEditorCaretListeners();
        this.activePanel = panel;
        if (panel == null) {
            this.clearDisplay();
        } else {
            this.bindEditorCaretListeners(panel);
        }
    }

    /** Resets the panel to its empty state. */
    public void clearDisplay() {
        this.updatingFromSelection = true;
        this.currentSource = Source.NONE;
        this.currentOffsets = null;
        this.lastRawSeen = null;
        this.autoEncoding = Encoding.PLAIN;
        this.sourceValue.setText(emDash());
        this.detectedValue.setText(emDash());
        this.rawArea.setText("");
        this.decodedArea.setText("");
        this.encodingSelector.setSelectedItem(Encoding.PLAIN);
        this.statusLine.setText("Select text in the request or response to inspect it.");
        this.updatingFromSelection = false;
        this.refreshApplyEnabled();
        if (this.content != null) {
            this.relayoutAreas();
        }
    }

    private void unbindEditorCaretListeners() {
        for (EditorCaretBinding binding : this.editorCaretBindings) {
            binding.component().removeCaretListener(binding.listener());
        }
        this.editorCaretBindings.clear();
    }

    private void bindEditorCaretListeners(RequestResponsePanel panel) {
        HttpRequestEditor requestEditor = panel.getRequestEditor();
        if (requestEditor != null) {
            this.bindSyntaxTextAreaCaretListener(requestEditor.uiComponent(), Source.REQUEST);
        }
        HttpResponseEditor responseEditor = panel.getResponseEditor();
        if (responseEditor != null) {
            this.bindSyntaxTextAreaCaretListener(responseEditor.uiComponent(), Source.RESPONSE);
        }
    }

    private void bindSyntaxTextAreaCaretListener(Component root, Source source) {
        JTextComponent syntaxTextArea = findSyntaxTextArea(root);
        if (syntaxTextArea == null) {
            return;
        }
        CaretListener listener = e -> this.handleEditorSelection(syntaxTextArea, source);
        syntaxTextArea.addCaretListener(listener);
        this.editorCaretBindings.add(new EditorCaretBinding(syntaxTextArea, listener));
    }

    private static JTextComponent findSyntaxTextArea(Component root) {
        if (root instanceof JTextComponent textComponent && SYNTAX_TEXT_AREA_NAME.equals(textComponent.getName())) {
            return textComponent;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                JTextComponent found = findSyntaxTextArea(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void handleEditorSelection(JTextComponent textComponent, Source source) {
        if (this.activePanel == null) {
            return;
        }
        String raw = textComponent.getSelectedText();
        if (raw == null || raw.isEmpty()) {
            // No selection: keep the last inspected value visible so the user can still edit/apply it.
            return;
        }

        Range offsets = this.resolveSelectionOffsets(textComponent, source);
        if (offsets == null) {
            return;
        }

        boolean sameSelection = source == this.currentSource && raw.equals(this.lastRawSeen);
        if (sameSelection) {
            return;
        }

        this.currentSource = source;
        this.currentOffsets = offsets;
        this.lastRawSeen = raw;
        this.showSelection(raw, source);
    }

    private Range resolveSelectionOffsets(JTextComponent textComponent, Source source) {
        RequestResponsePanel panel = this.activePanel;
        if (panel == null) {
            return null;
        }
        Selection sel = null;
        if (source == Source.REQUEST) {
            HttpRequestEditor editor = panel.getRequestEditor();
            if (editor != null) {
                sel = editor.selection().orElse(null);
            }
        } else if (source == Source.RESPONSE) {
            HttpResponseEditor editor = panel.getResponseEditor();
            if (editor != null) {
                sel = editor.selection().orElse(null);
            }
        }
        if (sel != null) {
            Range offsets = sel.offsets();
            if (offsets != null) {
                return offsets;
            }
        }
        int start = textComponent.getSelectionStart();
        int end = textComponent.getSelectionEnd();
        if (start < 0 || end < start) {
            return null;
        }
        return Range.range(start, end);
    }

    private record EditorCaretBinding(JTextComponent component, CaretListener listener) {}

    private void showSelection(String raw, Source source) {
        this.updatingFromSelection = true;

        this.sourceValue.setText(source == Source.REQUEST ? "Request" : "Response");
        this.rawArea.setText(raw);
        this.rawArea.setCaretPosition(0);

        Encoding detected = detectEncoding(raw);
        this.autoEncoding = detected;
        String decoded = decode(raw, detected);

        this.detectedValue.setText(detected == Encoding.PLAIN
                ? detected.toString() + " (no encoding detected)"
                : detected.toString());
        this.encodingSelector.setSelectedItem(detected);
        this.decodedArea.setText(decoded);
        this.decodedArea.setCaretPosition(0);

        if (source == Source.RESPONSE) {
            this.statusLine.setText("Response is read-only \u2013 decoded value shown for inspection only.");
        } else if (detected == Encoding.PLAIN) {
            this.statusLine.setText("Edit the value and press Apply to write it back to the request.");
        } else {
            this.statusLine.setText("Edit the decoded value and press Apply to re-encode and write it back.");
        }

        this.updatingFromSelection = false;
        this.refreshApplyEnabled();
        this.relayoutAreas();
    }

    /**
     * After the fields' contents change, reset their scroll position to the top and revalidate so a wrapping text
     * area recomputes its (width-dependent) preferred height for the new text.
     */
    private void relayoutAreas() {
        SwingUtilities.invokeLater(() -> {
            this.rawArea.setCaretPosition(0);
            this.decodedArea.setCaretPosition(0);
            this.rawArea.revalidate();
            this.decodedArea.revalidate();
            this.content.revalidate();
            this.content.repaint();
        });
    }

    private void reDecodeWithSelectedScheme() {
        if (this.lastRawSeen == null) {
            return;
        }
        Encoding scheme = (Encoding) this.encodingSelector.getSelectedItem();
        if (scheme == null) {
            return;
        }
        this.autoEncoding = scheme;
        this.updatingFromSelection = true;
        this.decodedArea.setText(decode(this.lastRawSeen, scheme));
        this.decodedArea.setCaretPosition(0);
        this.updatingFromSelection = false;
        this.relayoutAreas();
    }

    private void refreshApplyEnabled() {
        boolean canApply = this.currentSource == Source.REQUEST && this.currentOffsets != null;
        this.applyButton.setEnabled(canApply);
    }

    // ------------------------------------------------------------------
    // Apply edited value back into the request editor
    // ------------------------------------------------------------------

    private void applyDecodedValue() {
        RequestResponsePanel panel = this.activePanel;
        if (panel == null || this.currentSource != Source.REQUEST || this.currentOffsets == null) {
            return;
        }
        HttpRequestEditor editor = panel.getRequestEditor();
        if (editor == null) {
            return;
        }

        Encoding scheme = (Encoding) this.encodingSelector.getSelectedItem();
        if (scheme == null) {
            scheme = this.autoEncoding;
        }
        String reEncoded = encode(this.decodedArea.getText(), scheme);

        ByteArray current = editor.getRequest().toByteArray();
        if (current == null) {
            this.statusLine.setText("Could not read the current request to apply the change.");
            return;
        }

        int start = this.currentOffsets.startIndexInclusive();
        int end = this.currentOffsets.endIndexExclusive();
        if (start < 0 || end > current.length() || start > end) {
            this.statusLine.setText("Selection is no longer valid \u2013 reselect the text and try again.");
            return;
        }

        String full = current.toString();
        String updated = full.substring(0, start) + reEncoded + full.substring(end);

        editor.setRequest(burp.api.montoya.http.message.requests.HttpRequest.httpRequest(
                editor.getRequest().httpService(), updated));

        // Reflect the new selection window so a subsequent edit round-trips correctly.
        int newEnd = start + reEncoded.length();
        this.currentOffsets = Range.range(start, newEnd);
        this.lastRawSeen = reEncoded;
        this.updatingFromSelection = true;
        this.rawArea.setText(reEncoded);
        this.rawArea.setCaretPosition(0);
        this.updatingFromSelection = false;

        this.statusLine.setText("Applied to request.");
        this.relayoutAreas();
        SwingUtilities.invokeLater(() -> {
            if (editor.uiComponent() != null) {
                editor.setCaretPosition(newEnd);
            }
        });
    }

    private static Encoding detectEncoding(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Encoding.PLAIN;
        }
        // Percent-escaped URL encoding is unambiguous, so check it first.
        if (containsPercentEscape(raw)) {
            return Encoding.URL;
        }
        if (looksHtmlEncoded(raw)) {
            return Encoding.HTML;
        }
        // Base64 before the plus-only URL case: Base64 also uses '+', so a valid Base64 string must not be mistaken
        // for URL-encoded form data just because it contains a '+'.
        if (looksBase64(raw)) {
            return Encoding.BASE64;
        }
        if (looksUrlEncoded(raw)) {
            return Encoding.URL;
        }
        return Encoding.PLAIN;
    }

    /** True if the string contains at least one valid %XX escape (and no malformed one). */
    private static boolean containsPercentEscape(String s) {
        boolean any = false;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '%') {
                if (i + 2 >= s.length() || !isHex(s.charAt(i + 1)) || !isHex(s.charAt(i + 2))) {
                    return false;
                }
                any = true;
            }
        }
        return any;
    }

    private static boolean looksUrlEncoded(String s) {
        // Fallback detection for URL-encoded form data that uses '+' for spaces but has no %XX escapes (those are
        // already handled earlier). A bare '+' only counts when there is no raw space and no whitespace/newline in the
        // string, since encoded data has its spaces encoded; this avoids treating plain text like "a + b" as encoded.
        boolean hasPlus = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '+') {
                hasPlus = true;
            } else if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                return false;
            }
        }
        return hasPlus;
    }

    private static boolean looksHtmlEncoded(String s) {
        return s.contains("&#") || s.contains("&amp;") || s.contains("&lt;") || s.contains("&gt;")
                || s.contains("&quot;") || s.contains("&#x") || s.contains("&apos;");
    }

    private static boolean looksBase64(String s) {
        String t = s.trim();
        if (t.length() < 8 || t.length() % 4 != 0) {
            return false;
        }
        int pad = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '=') {
                pad++;
                continue;
            }
            if (pad > 0) {
                return false; // padding only allowed at the very end
            }
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '+' || c == '/';
            if (!ok) {
                return false;
            }
        }
        if (pad > 2) {
            return false;
        }
        // Guard against ordinary lowercase words that happen to be multiples of 4 characters.
        String decoded = Treepeater.api.utilities().base64Utils().decode(t).toString();
        if (decoded == null || decoded.isEmpty()) {
            return false;
        }
        return isMostlyPrintable(decoded);
    }

    private static boolean isMostlyPrintable(String s) {
        int printable = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r' || (c >= 0x20 && c < 0x7f) || c >= 0xa0) {
                printable++;
            }
        }
        return printable >= (int) Math.ceil(s.length() * 0.9);
    }

    private static String decode(String raw, Encoding encoding) {
        if (raw == null) {
            return "";
        }
        try {
            switch (encoding) {
                case URL:
                    return Treepeater.api.utilities().urlUtils().decode(raw);
                case HTML:
                    return Treepeater.api.utilities().htmlUtils().decode(raw);
                case BASE64:
                    return Treepeater.api.utilities().base64Utils().decode(raw.trim()).toString();
                case PLAIN:
                default:
                    return raw;
            }
        } catch (Exception ex) {
            return raw;
        }
    }

    private static String encode(String value, Encoding encoding) {
        if (value == null) {
            return "";
        }
        try {
            switch (encoding) {
                case URL:
                    // Burp's urlUtils().encode() follows java.net.URLEncoder, which encodes spaces as "+". Replace those
                    // with "%20". This is safe: a literal '+' in the input is encoded as "%2B", so every remaining "+"
                    // in the output represents a space.
                    return Treepeater.api.utilities().urlUtils().encode(value).replace("+", "%20");
                case HTML:
                    return Treepeater.api.utilities().htmlUtils().encode(value);
                case BASE64:
                    return Treepeater.api.utilities().base64Utils()
                            .encodeToString(ByteArray.byteArray(value.getBytes(StandardCharsets.UTF_8)));
                case PLAIN:
                default:
                    return value;
            }
        } catch (Exception ex) {
            return value;
        }
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    // ------------------------------------------------------------------
    // UI construction
    // ------------------------------------------------------------------

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout());
        root.add(new ToolbarTabTitle("Inspector"), BorderLayout.NORTH);

        // Simple vertical stack of cards. Fields wrap to the available width and grow in height with their content;
        // the outer scroll pane provides a vertical scrollbar when the total gets tall. The body tracks the viewport
        // width so the fields always wrap to the visible width instead of overflowing.
        WidthTrackingBody body = new WidthTrackingBody();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        body.setOpaque(false);

        RoundedPanel selection = this.buildSelectionCard();
        selection.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        body.add(selection);
        body.add(Box.createVerticalStrut(16));
        RoundedPanel decoded = this.buildDecodedCard();
        decoded.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        body.add(decoded);
        body.add(Box.createVerticalGlue());

        JScrollPane bodyScroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        bodyScroll.setBorder(BorderFactory.createEmptyBorder());
        bodyScroll.getViewport().setOpaque(false);
        bodyScroll.setOpaque(false);
        bodyScroll.getVerticalScrollBar().setUnitIncrement(16);

        root.add(bodyScroll, BorderLayout.CENTER);

        this.clearDisplay();
        return root;
    }

    /**
     * A vertical BoxLayout panel that reports it tracks the scroll pane viewport's width. This makes the stacked
     * text fields wrap to the visible width (rather than keeping their natural, content-driven width and overflowing),
     * while a plain vertical scrollbar handles overall height. No manual width/height math is involved.
     */
    private static final class WidthTrackingBody extends JPanel implements javax.swing.Scrollable {
        @Override
        public java.awt.Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
            return orientation == javax.swing.SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
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

    /**
     * A minimal wrapping text area that revalidates whenever its width changes. A wrapping {@link JTextArea} derives
     * its preferred height from its width but does not recompute it on a plain resize; this one triggers the recompute
     * so the field's height follows the panel width in both directions (grows when narrowed, shrinks when widened).
     */
    private static final class RevalidatingTextArea extends JTextArea {
        private int lastWidth = -1;

        private RevalidatingTextArea() {
            setLineWrap(true);
            setWrapStyleWord(false);
            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    int w = getWidth();
                    if (w != RevalidatingTextArea.this.lastWidth) {
                        RevalidatingTextArea.this.lastWidth = w;
                        revalidate();
                    }
                }
            });
        }
    }

    private RoundedPanel buildSelectionCard() {
        this.selectionCard = createInfoCard();
        this.selectionCard.setLayout(new BoxLayout(this.selectionCard, BoxLayout.Y_AXIS));

        this.selectionCard.add(sectionLabelRow("Selection"));
        this.selectionCard.add(Box.createVerticalStrut(10));
        this.selectionCard.add(kvRow("Source", this.sourceValue));
        this.selectionCard.add(Box.createVerticalStrut(6));
        this.selectionCard.add(kvRow("Detected", this.detectedValue));
        this.selectionCard.add(Box.createVerticalStrut(6));

        this.rawArea.setEditable(false);
        this.selectionCard.add(areaRow("Raw", this.rawArea));

        return this.selectionCard;
    }

    private RoundedPanel buildDecodedCard() {
        this.decodedCard = createInfoCard();
        this.decodedCard.setLayout(new BoxLayout(this.decodedCard, BoxLayout.Y_AXIS));

        this.decodedCard.add(sectionLabelRow("Decoded"));
        this.decodedCard.add(Box.createVerticalStrut(10));

        this.decodedCard.add(leftRow(keyLabel("Scheme")));
        this.decodedCard.add(Box.createVerticalStrut(2));
        this.encodingSelector.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        this.encodingSelector.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, this.encodingSelector.getPreferredSize().height));
        this.decodedCard.add(this.encodingSelector);
        this.decodedCard.add(Box.createVerticalStrut(8));

        this.decodedArea.setEditable(true);
        this.decodedCard.add(areaRow("Value", this.decodedArea));
        this.decodedCard.add(Box.createVerticalStrut(4));

        this.applyButton.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        this.decodedCard.add(leftRow(this.applyButton));
        this.decodedCard.add(Box.createVerticalStrut(6));

        this.statusLine.setFont(this.statusLine.getFont().deriveFont(Font.ITALIC));
        this.decodedCard.add(leftRow(this.statusLine));

        return this.decodedCard;
    }

    /** A left-aligned single-line row (label + value) that stays compact and shrinks cleanly. */
    private static JComponent kvRow(String key, JLabel value) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        JLabel k = keyLabel(key);
        row.add(k);
        row.add(Box.createHorizontalStrut(6));
        value.setAlignmentY(java.awt.Component.CENTER_ALIGNMENT);
        row.add(value);
        row.add(Box.createHorizontalGlue());
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    /** A label above a text field. The field wraps to the available width and grows in height with its content. */
    private static JComponent areaRow(String key, JTextArea area) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        row.add(leftRow(keyLabel(key)));
        row.add(Box.createVerticalStrut(2));

        area.setLineWrap(true);
        area.setWrapStyleWord(false);
        area.setRows(2); // minimum visible height; grows with content
        area.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        area.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        row.add(area);
        return row;
    }

    /** Wraps a component in a left-aligned, full-width, single-line-height row for BoxLayout stacking. */
    private static JComponent leftRow(JComponent comp) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
        comp.setAlignmentY(java.awt.Component.CENTER_ALIGNMENT);
        row.add(comp);
        row.add(Box.createHorizontalGlue());
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private static JComponent sectionLabelRow(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD).deriveFont(l.getFont().getSize2D() + 2f));
        return leftRow(l);
    }

    private static JLabel keyLabel(String key) {
        JLabel l = new JLabel(key + ":");
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        return l;
    }

    private static RoundedPanel createInfoCard() {
        RoundedPanel card = new RoundedPanel();
        card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        card.setOpaque(false);
        applyInfoCardTheme(card);
        return card;
    }

    private static void applyInfoCardTheme(RoundedPanel card) {
        card.setBackgroundColor(UIManager.getColor("Colors.ui.background.3"));
        card.setBorderColor(UIManager.getColor("Colors.ui.background.3"));
    }

    private static JLabel valueLabel() {
        JLabel l = new JLabel(emDash());
        return l;
    }

    private static String emDash() {
        return "\u2014";
    }
}
