package treepeater.requestResponse;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;

import treepeater.Utilities;
import treepeater.icons.CloseIcon;
import treepeater.tree.RequestTreeNode;
import treepeater.tree.TreepeaterNode;
import treepeater.tree.TreepeaterNodeListener;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;



public class RequestResponseTab extends JPanel {

    private static final float PATH_FONT_DELTA = 3f;
    private static final int TEXT_NUDGE_UP_PX = 2;
    private static final int LABEL_STACK_OVERLAP_PX = 2;
    private static final int TITLE_BOTTOM_PADDING_PX = 4;
    private static final int CLOSE_NUDGE_DOWN_PX = 4;



    private final RequestTreeNode node;
    private final JLabel pathLabel;
    private final JLabel nameLabel;
    private final JButton closeButton;
    private final JPanel textPanel;

    public HashSet<ActionListener> listeners;

    public RequestResponseTab(RequestTreeNode node) {

        this.node = node;

        this.listeners = new HashSet<>();

        this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        this.setOpaque(false);

        this.textPanel = new JPanel();
        this.textPanel.setLayout(new BoxLayout(this.textPanel, BoxLayout.PAGE_AXIS));
        this.textPanel.setOpaque(false);
        this.textPanel.setAlignmentY(Component.CENTER_ALIGNMENT);
        this.textPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, -TEXT_NUDGE_UP_PX, 0));

        this.pathLabel = new JLabel();
        this.pathLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        this.pathLabel.setBorder(BorderFactory.createEmptyBorder(-2, 0, -LABEL_STACK_OVERLAP_PX, 0));
        this.pathLabel.setVisible(false);

        this.nameLabel = new JLabel(node.getName());
        this.nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        this.nameLabel.setBorder(BorderFactory.createEmptyBorder(-1, 0, TITLE_BOTTOM_PADDING_PX, 0));

        this.textPanel.add(this.pathLabel);
        this.textPanel.add(this.nameLabel);
        this.add(this.textPanel);

        this.closeButton = new JButton();
        this.closeButton.setIcon(new CloseIcon().withColor(UIManager.getColor("Label.foreground")));
        this.closeButton.addActionListener(
                e -> RequestResponseTab.this.listeners.forEach(l -> l.actionPerformed(e)));
        this.closeButton.setBorderPainted(false);
        this.closeButton.setContentAreaFilled(false);
        this.closeButton.setFocusPainted(false);
        this.closeButton.setOpaque(false);
        this.closeButton.setMargin(new Insets(0, 2, 0, 2));
        this.closeButton.setBorder(BorderFactory.createEmptyBorder(CLOSE_NUDGE_DOWN_PX, 4, 0, 0));
        this.closeButton.setAlignmentY(0.38f);

        this.add(Box.createHorizontalStrut(2));
        this.add(this.closeButton);

        this.refreshFromNode();

        node.addListener(
                new TreepeaterNodeListener() {
                    @Override
                    public void onSelect(TreepeaterNode n) {}

                    @Override
                    public void onNameChange(String newName) {
                        RequestResponseTab.this.refreshFromNode();
                    }

                    @Override
                    public void onDelete(TreepeaterNode n) {}
                });
    }

    /** Re-reads tree path and tab title from the bound node. */
    public void refreshFromNode() {
        String name = this.node.getName() != null ? this.node.getName() : "";
        this.nameLabel.setText(name);
        this.applyLabelTheme();

        String parentPath = Utilities.parentSlashPathForNode(this.node);
        if (parentPath.isEmpty()) {
            this.pathLabel.setText("");
            this.pathLabel.setVisible(false);
        } else {
            // Calculate if the path label should be truncated
            // Calculation is based on the width of the name label and the width of the path label
            // Utilities function implements this calculation and truncates the path if necesssary 
            FontMetrics nameFm = this.nameLabel.getFontMetrics(this.nameLabel.getFont());
            int maxWidth = Math.max(nameFm.stringWidth(name), 1);
            FontMetrics pathFm = this.pathLabel.getFontMetrics(this.pathLabel.getFont());
            this.pathLabel.setText(Utilities.truncatePathMiddle(parentPath, maxWidth, pathFm));
            this.pathLabel.setVisible(true);
        }

        revalidate();
        repaint();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (this.nameLabel == null) {
            return;
        }
        this.applyThemeLocalStyles();
    }

    private void applyLabelTheme() {
        Font base = UIManager.getFont("Label.font");
        if (base == null) {
            base = this.nameLabel.getFont();
        }

        this.nameLabel.setFont(base);
        float pathSize = Math.max(9f, base.getSize2D() - PATH_FONT_DELTA);
        this.pathLabel.setFont(base.deriveFont(pathSize));

        Color muted = UIManager.getColor("Label.disabledForeground");
        if (muted != null) {
            this.pathLabel.setForeground(muted);
        }

        Color normal = UIManager.getColor("Label.foreground");
        if (normal != null) {
            this.nameLabel.setForeground(normal);
        }
    }

    private void applyThemeLocalStyles() {
        this.applyLabelTheme();

        if (this.closeButton != null) {
            this.closeButton.setIcon(new CloseIcon().withColor(UIManager.getColor("Label.foreground")));
        }

        this.refreshFromNode();
    }


    public void addActionListener(ActionListener listener) {
        this.listeners.add(listener);

    }

    private static final int DRAG_THRESHOLD = 8;

    /** Enables dragging this tab; labels receive mouse events first. */

    public void enableDrag(TransferHandler handler, Runnable onActivate) {
        installThresholdDrag(this, handler, onActivate);
        installThresholdDrag(this.pathLabel, handler, onActivate);
        installThresholdDrag(this.nameLabel, handler, onActivate);
    }

    public void installPopupMenu(JPopupMenu menu, Runnable beforeShow) {
        installPopupTrigger(this, menu, beforeShow);
        installPopupTrigger(this.pathLabel, menu, beforeShow);
        installPopupTrigger(this.nameLabel, menu, beforeShow);
    }

    private static void installPopupTrigger(JComponent component, JPopupMenu menu, Runnable beforeShow) {
        component.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (e.isPopupTrigger()) {
                            showMenu(e);
                        }
                    }

                    @Override
                    public void mouseReleased(MouseEvent e) {
                        if (e.isPopupTrigger()) {
                            showMenu(e);
                        }
                    }

                    private void showMenu(MouseEvent e) {
                        if (beforeShow != null) {
                            beforeShow.run();
                        }

                        menu.show(component, e.getX(), e.getY());
                    }
                });
    }

    private static void installThresholdDrag(
            JComponent component, TransferHandler handler, Runnable onActivate) {
        component.setTransferHandler(handler);
        MouseAdapter adapter =
                new MouseAdapter() {
                    private Point pressPoint;

                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (SwingUtilities.isLeftMouseButton(e)) {
                            this.pressPoint = e.getPoint();
                        }
                    }

                    @Override
                    public void mouseDragged(MouseEvent e) {
                        if (this.pressPoint == null || !SwingUtilities.isLeftMouseButton(e)) {
                            return;
                        }

                        if (this.pressPoint.distance(e.getPoint()) >= DRAG_THRESHOLD) {
                            this.pressPoint = null;
                            handler.exportAsDrag(component, e, TransferHandler.MOVE);
                        }
                    }

                    @Override
                    public void mouseReleased(MouseEvent e) {
                        if (this.pressPoint != null
                                && SwingUtilities.isLeftMouseButton(e)
                                && this.pressPoint.distance(e.getPoint()) < DRAG_THRESHOLD
                                && onActivate != null) {
                            onActivate.run();
                        }
                        this.pressPoint = null;
                    }
                };

        component.addMouseListener(adapter);
        component.addMouseMotionListener(adapter);
    }
}
