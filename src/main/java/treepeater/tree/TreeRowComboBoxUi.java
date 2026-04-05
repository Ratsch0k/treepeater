package treepeater.tree;

import java.awt.Color;
import java.awt.Graphics;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.plaf.ComboBoxUI;
import javax.swing.plaf.basic.BasicComboBoxUI;

import com.formdev.flatlaf.ui.FlatComboBoxUI;

/**
 * Installs a {@link ComboBoxUI} that skips painting a solid value-area background so a tree row
 * strip shows through. Uses a subclass of the LAF's ComboBoxUI, which in Burp Suite is {@link FlatComboBoxUI}.
 */
public final class TreeRowComboBoxUi {

    private TreeRowComboBoxUi() {
    }

    public static void install(JComboBox<?> box) {
        box.updateUI();
        ComboBoxUI replacement = new FlatImpl();
        if (replacement != null) {
            box.setUI(replacement);
        }
        box.setOpaque(false);
        box.setBackground(new Color(0, 0, 0, 0));
    }

    /**
     * FlatLaf paints the combo background in {@link #update}; delegate to {@link #paint} only so
     * {@link BasicComboBoxUI#paint} draws the value without the rounded fill.
     */
    private static final class FlatImpl extends FlatComboBoxUI {
        @Override
        public void update(Graphics g, JComponent c) {
            if (comboBox.isEditable()) {
                super.update(g, c);
                return;
            }
            paint(g, c);
        }
    }
}
