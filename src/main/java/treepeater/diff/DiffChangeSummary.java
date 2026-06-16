package treepeater.diff;

import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Inline removed/added character counts using the same red/green palette as the diff views. */
public final class DiffChangeSummary extends JPanel {

    private final JLabel removedLabel;
    private final JLabel addedLabel;

    public DiffChangeSummary() {
        super(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 8, 2, 8));

        this.removedLabel = createCountLabel();
        this.addedLabel = createCountLabel();
        add(this.removedLabel);
        add(this.addedLabel);
        clear();
    }

    public void setCounts(int removed, int added) {
        this.removedLabel.setText(formatCount(removed, true));
        this.addedLabel.setText(formatCount(added, false));
        refreshTheme();
    }

    public void clear() {
        setCounts(0, 0);
    }

    public void refreshTheme() {
        styleCountLabel(this.removedLabel, true);
        styleCountLabel(this.addedLabel, false);
    }

    private static JLabel createCountLabel() {
        JLabel label = new JLabel();
        label.setOpaque(true);
        label.setBorder(BorderFactory.createEmptyBorder(1, 5, 1, 5));
        return label;
    }

    private static void styleCountLabel(JLabel label, boolean removed) {
        if (removed) {
            label.setBackground(DiffTheme.removedBackground());
            label.setForeground(DiffTheme.removedForeground());
        } else {
            label.setBackground(DiffTheme.addedBackground());
            label.setForeground(DiffTheme.addedForeground());
        }
    }

    private static String formatCount(int count, boolean removed) {
        return (removed ? "−" : "+") + count + " characters";
    }
}
