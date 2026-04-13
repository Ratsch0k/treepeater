package treepeater.requestResponse.toolbar;

import java.awt.Component;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;

public class ToolbarTabTitle extends JPanel {
    public ToolbarTabTitle(String text) {
        this(text, null);
    }

    /**
     * @param trailing optional control shown on the right side of the title row (e.g. a toolbar button).
     */
    public ToolbarTabTitle(String text, JComponent trailing) {
        super();
        this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        this.setAlignmentX(Component.LEFT_ALIGNMENT);
        this.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() + 4f));
        label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        this.add(Box.createVerticalStrut(8));
        Box titleRow = Box.createHorizontalBox();
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.add(label);
        titleRow.add(Box.createHorizontalGlue());
        if (trailing != null) {
            titleRow.add(trailing);
            titleRow.add(Box.createHorizontalStrut(8));
        }
        this.add(titleRow);
        this.add(Box.createVerticalStrut(8));
        this.add(new JSeparator(JSplitPane.VERTICAL_SPLIT));
        this.add(Box.createVerticalStrut(8));
    }
}
