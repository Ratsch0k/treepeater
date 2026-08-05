package treepeater.components;

import static com.formdev.flatlaf.util.UIScale.scale;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.plaf.ComboBoxUI;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.ui.FlatComboBoxUI;

/**
 * Installs a compact {@link ComboBoxUI} for status pickers in tree rows and dialogs. The value
 * area is painted without its own background fill so the parent background shows through, and the
 * drop-down arrow button is narrowed so the combo stays small when only an icon is displayed.
 *
 * <p>In Burp Suite the LAF implementation is {@link FlatComboBoxUI}.
 */
public final class StatusComboBoxUi {

    private StatusComboBoxUi() {
    }

    /**
     * Applies the custom combo UI. Call after {@link JComboBox#updateUI()} when the LAF/theme
     * changes so Burp does not leave the default combo box UI installed.
     */
    public static void install(JComboBox<?> box) {
        box.setUI(new FlatImpl());
        box.setOpaque(false);
        box.putClientProperty(FlatClientProperties.STYLE, "padding: none; arrowType: triangle;");
    }

    /**
     * FlatLaf paints the combo background in {@code update}; for non-editable boxes we delegate to
     * {@code paint} only so the value is drawn without the rounded fill.
     */
    private static final class FlatImpl extends FlatComboBoxUI {

        /** Target preferred width for the drop-down arrow button (unscaled px). */
        private static final int ARROW_BUTTON_WIDTH = 10;

        /** Width of the painted chevron/triangle glyph inside the arrow button. */
        private static final int ARROW_GLYPH_WIDTH = 6;

        /**
         * Reduce the minimum width of the combobox and stop the arrow button from being sized
         * as a square (by default FlatLaf reserves a button as wide as the row is tall, which
         * makes the combo much wider than the icon it displays).
         */
        @Override
        protected void installDefaults() {
            super.installDefaults();
            this.minimumWidth = 16;
            this.padding = new Insets(0, 0, 0, 0);
            // BasicComboBoxUI.getMinimumSize() uses the row height as button width when
            // squareButton is true; disabling it falls back to the arrow button's own
            // preferredSize.width, which we also shrink below.
            this.squareButton = false;
            this.arrowType = "triangle";
        }

        /**
         * Replace FlatLaf's default arrow button with a narrower one so the combobox's
         * preferred width stays small in the tree cell.
         */
        @Override
        protected JButton createArrowButton() {
            FlatComboBoxButton button = new FlatComboBoxButton() {
                @Override
                public Dimension getPreferredSize() {
                    Dimension d = super.getPreferredSize();
                    return new Dimension(scale(ARROW_BUTTON_WIDTH), d.height);
                }
            };
            button.setArrowWidth(ARROW_GLYPH_WIDTH);
            return button;
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
