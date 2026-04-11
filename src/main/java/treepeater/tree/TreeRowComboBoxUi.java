package treepeater.tree;

import java.awt.Graphics;

import javax.swing.JComboBox;
import javax.swing.JComponent;

import com.formdev.flatlaf.ui.FlatComboBoxUI;

/**
 * Installs a {@link ComboBoxUI} that skips painting a solid value-area background so a tree row
 * strip shows through. Uses a subclass of the LAF's ComboBoxUI, which in Burp Suite is {@link FlatComboBoxUI}.
 */
public final class TreeRowComboBoxUi {

    private TreeRowComboBoxUi() {
    }

    /**
     * Applies the tree-row combo UI. Call after {@link JComboBox#updateUI()} when the LAF/theme
     * changes so Burp does not leave the default combo box UI installed.
     */
    public static void install(JComboBox<?> box) {
        box.setUI(new FlatImpl());
        box.setOpaque(false);
    }

    /**
     * FlatLaf paints the combo background in {@link #update}; delegate to {@link #paint} only so
     * the value is drawn without the rounded fill.
     */
    private static final class FlatImpl extends FlatComboBoxUI {

        /**
         * Reduce the minimum width of the combobox.
         */
        @Override
        protected void installDefaults() {
            super.installDefaults();
            this.minimumWidth = 42;
        }


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
