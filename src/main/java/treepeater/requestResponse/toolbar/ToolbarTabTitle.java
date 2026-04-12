package treepeater.requestResponse.toolbar;

import java.awt.Component;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;

public class ToolbarTabTitle extends JPanel {
    public ToolbarTabTitle(String text) {
        super();
        this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
        this.setAlignmentX(Component.LEFT_ALIGNMENT);
        this.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() + 4f));
        label.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        this.add(Box.createVerticalStrut(8));
        this.add(label);
        this.add(Box.createVerticalStrut(8));
        this.add(new JSeparator(JSplitPane.VERTICAL_SPLIT));
        this.add(Box.createVerticalStrut(8));
    }
}
