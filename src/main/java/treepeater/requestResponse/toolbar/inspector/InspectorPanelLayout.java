package treepeater.requestResponse.toolbar.inspector;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;

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
import javax.swing.UIManager;

import treepeater.components.RoundedPanel;
import treepeater.requestResponse.toolbar.ToolbarTabTitle;

/** Static UI builders for the Inspector toolbar tab. */
final class InspectorPanelLayout {

    private InspectorPanelLayout() {}

    record Cards(RoundedPanel selectionCard, RoundedPanel decodedCard) {}

    record DecodedCard(RoundedPanel card, JLabel sectionTitle) {}

    static JPanel buildContent(Cards cards) {
        JPanel root = new JPanel(new BorderLayout());
        root.add(new ToolbarTabTitle("Inspector"), BorderLayout.NORTH);

        WidthTrackingScrollBody body = new WidthTrackingScrollBody();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        body.setOpaque(false);

        cards.selectionCard().setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(cards.selectionCard());
        body.add(Box.createVerticalStrut(16));
        cards.decodedCard().setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(cards.decodedCard());
        body.add(Box.createVerticalGlue());

        JScrollPane bodyScroll = new JScrollPane(body,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        bodyScroll.setBorder(BorderFactory.createEmptyBorder());
        bodyScroll.getViewport().setOpaque(false);
        bodyScroll.setOpaque(false);
        bodyScroll.getVerticalScrollBar().setUnitIncrement(16);

        root.add(bodyScroll, BorderLayout.CENTER);
        return root;
    }

    static RoundedPanel buildSelectionCard(JLabel sourceValue, JLabel detectedValue, JTextArea rawArea) {
        RoundedPanel card = createInfoCard();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        card.add(sectionLabelRow("Selection"));
        card.add(Box.createVerticalStrut(10));
        card.add(kvRow("Source", sourceValue));
        card.add(Box.createVerticalStrut(6));
        card.add(kvRow("Detected", detectedValue));
        card.add(Box.createVerticalStrut(6));

        rawArea.setEditable(false);
        card.add(areaRow("Raw", rawArea));

        return card;
    }

    static DecodedCard buildDecodedCard(
            JComboBox<InspectorEncoding> encodingSelector,
            JTextArea decodedArea,
            JButton applyButton,
            JLabel statusLine) {
        RoundedPanel card = createInfoCard();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel sectionTitle = new JLabel("Decoded");
        sectionTitle.setFont(sectionTitle.getFont().deriveFont(Font.BOLD).deriveFont(sectionTitle.getFont().getSize2D() + 2f));
        card.add(leftRow(sectionTitle));
        card.add(Box.createVerticalStrut(10));

        card.add(leftRow(keyLabel("Scheme")));
        card.add(Box.createVerticalStrut(2));
        encodingSelector.setAlignmentX(Component.LEFT_ALIGNMENT);
        encodingSelector.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, encodingSelector.getPreferredSize().height));
        card.add(encodingSelector);
        card.add(Box.createVerticalStrut(8));

        decodedArea.setEditable(true);
        card.add(areaRow("Value", decodedArea));
        card.add(Box.createVerticalStrut(4));

        applyButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(leftRow(applyButton));
        card.add(Box.createVerticalStrut(6));

        statusLine.setFont(statusLine.getFont().deriveFont(Font.ITALIC));
        card.add(leftRow(statusLine));

        return new DecodedCard(card, sectionTitle);
    }

    static void applyInfoCardTheme(RoundedPanel card) {
        card.setBackgroundColor(UIManager.getColor("Colors.ui.background.3"));
        card.setBorderColor(UIManager.getColor("Colors.ui.background.3"));
    }

    static JLabel valueLabel() {
        return new JLabel(emDash());
    }

    static String emDash() {
        return "\u2014";
    }

    private static RoundedPanel createInfoCard() {
        RoundedPanel card = new RoundedPanel();
        card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        card.setOpaque(false);
        applyInfoCardTheme(card);
        return card;
    }

    private static JComponent kvRow(String key, JLabel value) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel k = keyLabel(key);
        row.add(k);
        row.add(Box.createHorizontalStrut(6));
        value.setAlignmentY(Component.CENTER_ALIGNMENT);
        row.add(value);
        row.add(Box.createHorizontalGlue());
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private static JComponent areaRow(String key, JTextArea area) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        row.add(leftRow(keyLabel(key)));
        row.add(Box.createVerticalStrut(2));

        area.setLineWrap(true);
        area.setWrapStyleWord(false);
        area.setRows(2);
        area.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(area);
        return row;
    }

    private static JComponent leftRow(JComponent comp) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        comp.setAlignmentY(Component.CENTER_ALIGNMENT);
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
}
