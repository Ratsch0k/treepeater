package treepeater.requestResponse.toolbar.inspector;

import java.awt.BorderLayout;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import burp.api.montoya.core.Range;
import burp.api.montoya.ui.editor.HttpRequestEditor;

import treepeater.components.RoundedPanel;
import treepeater.icons.InspectorIcon;
import treepeater.requestResponse.RequestResponsePanel;
import treepeater.requestResponse.toolbar.ToolbarIconButton;
import treepeater.requestResponse.toolbar.inspector.EditorSelectionWatcher.Source;

/**
 * Repeater-style "Inspector" tab.
 *
 * <p>Watches the current text selection inside the request and response editors of the active tab. When the selected
 * text looks like it carries a well-known encoding (URL percent-encoding, HTML entities, or Base64) the decoded value
 * is shown in an editable field. Editing the decoded value and pressing <em>Apply</em> re-encodes it with the selected
 * scheme and writes it back into the request editor at the original selection offsets &mdash; mirroring the behaviour of
 * Burp Repeater's Inspector.</p>
 *
 * <p>When the selection is plain text with no detectable encoding, the Inspector switches to encoding mode: the raw
 * plain text is shown read-only, the value field shows the encoded form for the chosen scheme (URL by default), and
 * pressing <em>Apply</em> writes the encoded value back.</p>
 *
 * <p>The response editor is read-only, so decoded or encoded response selections are shown for inspection but cannot
 * be applied back.</p>
 */
public class InspectorToolbarTab {
    private final ToolbarIconButton button;
    private final JPanel content;

    private final JLabel sourceValue = InspectorPanelLayout.valueLabel();
    private final JLabel detectedValue = InspectorPanelLayout.valueLabel();
    private final JTextArea rawArea = new RevalidatingTextArea();
    private final JTextArea decodedArea = new RevalidatingTextArea();
    private final JComboBox<InspectorEncoding> encodingSelector = new JComboBox<>(InspectorEncoding.values());
    private final JButton applyButton = new JButton("Apply");
    private final JLabel statusLine = InspectorPanelLayout.valueLabel();
    private final JLabel valueCardTitle;

    private final RoundedPanel selectionCard;
    private final RoundedPanel decodedCard;
    private final EditorSelectionWatcher selectionWatcher = new EditorSelectionWatcher();

    private RequestResponsePanel activePanel;
    private SelectionSnapshot snapshot = SelectionSnapshot.empty();

    // True while we programmatically mutate fields, to suppress reactive listeners.
    private boolean updatingFromSelection;

    public InspectorToolbarTab() {
        this.button = new ToolbarIconButton(new InspectorIcon());
        this.selectionCard = InspectorPanelLayout.buildSelectionCard(this.sourceValue, this.detectedValue, this.rawArea);
        InspectorPanelLayout.DecodedCard decodedCardParts = InspectorPanelLayout.buildDecodedCard(
                this.encodingSelector, this.decodedArea, this.applyButton, this.statusLine);
        this.decodedCard = decodedCardParts.card();
        this.valueCardTitle = decodedCardParts.sectionTitle();
        this.content = new JPanel(new BorderLayout());
        this.content.add(InspectorPanelLayout.buildContent(
                new InspectorPanelLayout.Cards(this.selectionCard, this.decodedCard)), BorderLayout.CENTER);

        this.applyButton.addActionListener(e -> this.applyDecodedValue());
        this.encodingSelector.addActionListener(e -> {
            if (this.updatingFromSelection) {
                return;
            }
            this.refreshValueForScheme();
        });

        this.refreshApplyEnabled();
        this.clearDisplay();
    }

    public JButton getButton() {
        return this.button;
    }

    public JPanel getContent() {
        return this.content;
    }

    public void applyLocalTheme() {
        this.button.applyLocalTheme();
        InspectorPanelLayout.applyInfoCardTheme(this.selectionCard);
        InspectorPanelLayout.applyInfoCardTheme(this.decodedCard);
    }

    /** Binds the Inspector to the currently selected request/response tab. */
    public void setActivePanel(RequestResponsePanel panel) {
        this.selectionWatcher.unbind();
        this.activePanel = panel;
        if (panel == null) {
            this.clearDisplay();
        } else {
            this.selectionWatcher.bind(panel, this::handleSelectionChanged);
        }
    }

    /** Resets the panel to its empty state. */
    public void clearDisplay() {
        runWithoutReactiveUpdates(() -> {
            this.snapshot = SelectionSnapshot.empty();
            this.sourceValue.setText(InspectorPanelLayout.emDash());
            this.detectedValue.setText(InspectorPanelLayout.emDash());
            this.rawArea.setText("");
            this.decodedArea.setText("");
            this.encodingSelector.setSelectedItem(InspectorEncoding.PLAIN);
            this.valueCardTitle.setText("Decoded");
            this.statusLine.setText("Select text in the request or response to inspect it.");
        });
        this.refreshApplyEnabled();
        this.relayoutAreas();
    }

    private void handleSelectionChanged(Source source, String raw, Range offsets) {
        InspectorEncoding detected = InspectorEncoding.detect(raw);
        boolean encodeMode = detected == InspectorEncoding.PLAIN;
        InspectorEncoding selected = encodeMode ? InspectorEncoding.URL : detected;
        this.snapshot = new SelectionSnapshot(source, offsets, raw, detected, selected, encodeMode);
        this.showSelection(raw, source);
    }

    private void showSelection(String raw, Source source) {
        InspectorEncoding detected = this.snapshot.detectedEncoding();
        InspectorEncoding selected = this.snapshot.selectedEncoding();
        boolean encodeMode = this.snapshot.encodeMode();

        runWithoutReactiveUpdates(() -> {
            this.sourceValue.setText(source == Source.REQUEST ? "Request" : "Response");
            this.rawArea.setText(raw);
            this.rawArea.setCaretPosition(0);

            String value = valueForScheme(raw, selected, encodeMode);
            this.detectedValue.setText(encodeMode
                    ? "Plain text (encoding mode)"
                    : detected == InspectorEncoding.PLAIN
                            ? detected.toString() + " (no encoding detected)"
                            : detected.toString());
            this.encodingSelector.setSelectedItem(selected);
            this.decodedArea.setText(value);
            this.decodedArea.setCaretPosition(0);
            this.valueCardTitle.setText(encodeMode ? "Encoded" : "Decoded");

            if (source == Source.RESPONSE) {
                this.statusLine.setText(encodeMode
                        ? "Response is read-only \u2013 encoded value shown for inspection only."
                        : "Response is read-only \u2013 decoded value shown for inspection only.");
            } else if (encodeMode) {
                this.statusLine.setText(
                        "Choose a scheme to encode the selection. Edit the encoded value and press Apply to write it back.");
            } else if (detected == InspectorEncoding.PLAIN) {
                this.statusLine.setText("Edit the value and press Apply to write it back to the request.");
            } else {
                this.statusLine.setText("Edit the decoded value and press Apply to re-encode and write it back.");
            }
        });

        this.refreshApplyEnabled();
        this.relayoutAreas();
    }

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

    private void refreshValueForScheme() {
        if (this.snapshot.raw() == null) {
            return;
        }
        InspectorEncoding scheme = selectedEncoding();
        if (scheme == null) {
            return;
        }
        this.snapshot = new SelectionSnapshot(
                this.snapshot.source(),
                this.snapshot.offsets(),
                this.snapshot.raw(),
                this.snapshot.detectedEncoding(),
                scheme,
                this.snapshot.encodeMode());
        runWithoutReactiveUpdates(() -> {
            this.decodedArea.setText(valueForScheme(this.snapshot.raw(), scheme, this.snapshot.encodeMode()));
            this.decodedArea.setCaretPosition(0);
        });
        this.relayoutAreas();
    }

    private static String valueForScheme(String raw, InspectorEncoding scheme, boolean encodeMode) {
        return encodeMode
                ? InspectorEncoding.encode(raw, scheme)
                : InspectorEncoding.decode(raw, scheme);
    }

    private void refreshApplyEnabled() {
        this.applyButton.setEnabled(this.snapshot.canApply());
    }

    private void applyDecodedValue() {
        RequestResponsePanel panel = this.activePanel;
        if (panel == null || !this.snapshot.canApply()) {
            return;
        }
        HttpRequestEditor editor = panel.getRequestEditor();
        if (editor == null) {
            return;
        }

        InspectorEncoding scheme = selectedEncoding();
        if (scheme == null) {
            scheme = this.snapshot.selectedEncoding();
        }

        InspectorRequestApplier.Result result = InspectorRequestApplier.apply(
                editor,
                this.snapshot.offsets(),
                this.decodedArea.getText(),
                scheme,
                this.snapshot.encodeMode());
        this.statusLine.setText(result.statusMessage());
        if (!result.success()) {
            return;
        }

        this.snapshot = new SelectionSnapshot(
                this.snapshot.source(),
                result.newOffsets(),
                result.newRaw(),
                this.snapshot.detectedEncoding(),
                scheme,
                this.snapshot.encodeMode());
        runWithoutReactiveUpdates(() -> {
            this.rawArea.setText(result.newRaw());
            this.rawArea.setCaretPosition(0);
        });

        this.relayoutAreas();
        int newEnd = result.newOffsets().endIndexExclusive();
        SwingUtilities.invokeLater(() -> {
            if (editor.uiComponent() != null) {
                editor.setCaretPosition(newEnd);
            }
        });
    }

    private InspectorEncoding selectedEncoding() {
        return (InspectorEncoding) this.encodingSelector.getSelectedItem();
    }

    private void runWithoutReactiveUpdates(Runnable action) {
        this.updatingFromSelection = true;
        try {
            action.run();
        } finally {
            this.updatingFromSelection = false;
        }
    }
}
