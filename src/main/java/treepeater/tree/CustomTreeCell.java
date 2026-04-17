package treepeater.tree;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import treepeater.Treepeater;
import treepeater.icons.CloseIcon;
import treepeater.requestResponse.Status;
import treepeater.requestResponse.StatusComboBoxRenderer;


public class CustomTreeCell extends JPanel implements DocumentListener {
    static final int MIN_TREE_ROW_INNER_WIDTH = 160;

    private JComboBox<Status> box;
    private JLabel label;
    private JTextField field;
    private CardLayout card;
    private JPanel textPanel;
    private TreepeaterNode node;
    private JButton closeButton;
    private boolean noPropagation = false;

    public CustomTreeCell() {
        super(new BorderLayout());

        StatusComboBox box = new StatusComboBox();
        box.setRenderer(new StatusComboBoxRenderer());
        TreeRowComboBoxUi.install(box);
        box.setEnabled(true);
        box.addActionListener(e -> {
            Status selected = (Status) box.getSelectedItem();
            if (selected != null && this.node != null) {
                this.node.setStatus(selected);
            }
            CustomTreeCell.this.updateComponents();
        });

        this.setLayout(new GridBagLayout());
        this.setOpaque(false);
        this.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = 0;
        gc.insets = new Insets(2, 8, 2, 3);

        this.box = box;
        this.box.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        this.box.setOpaque(false);
        gc.gridx = 0;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        this.add(this.box, gc);
        
        this.label = new JLabel();
        this.label.setOpaque(false);
        this.field = new JTextField();
        this.field.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        this.field.setOpaque(false);

        this.field.getDocument().addDocumentListener(this);

        this.card = new CardLayout();
        this.textPanel = new JPanel(this.card);
        this.textPanel.setOpaque(false);

        this.textPanel.add(this.label, "label");
        this.textPanel.add(this.field, "field");
        int textRowHeight = Math.max(this.field.getPreferredSize().height, this.label.getPreferredSize().height);
        this.textPanel.setMinimumSize(new Dimension(0, textRowHeight));

        gc.gridx = 1;
        gc.weightx = 1;
        gc.fill =  GridBagConstraints.HORIZONTAL;
        this.add(this.textPanel, gc);

        gc.gridx = 2;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        this.closeButton = new JButton();
        this.closeButton.setIcon(new CloseIcon().withColor(UIManager.getColor("Label.foreground")));
        this.closeButton.setMargin(new Insets(0, 0, 0, 0));
        this.closeButton.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        this.closeButton.setOpaque(false);
        this.closeButton.setContentAreaFilled(false);
        this.closeButton.setFocusable(false);
        this.closeButton.addActionListener(e -> {
            if (this.node == null || this.node.getParent() == null) {
                return;
            }
            
            JTree host = (JTree) SwingUtilities.getAncestorOfClass(JTree.class, CustomTreeCell.this);
            if (host != null) {
                host.cancelEditing();
            }

            this.node.delete();
        });
        gc.gridx = 3;
        this.add(this.closeButton, gc);

        this.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        this.setOpaque(false);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        this.applyThemeLocalStyles();
    }

    private void applyThemeLocalStyles() {
        if (this.closeButton != null) {
            this.closeButton.setIcon(new CloseIcon().withColor(UIManager.getColor("Label.foreground")));
        }
    }

    public int getComboBoxWidth() {
        return this.box.getPreferredSize().width;
    }

    /**
     * Width of the trailing strip used for hit-testing the close control (strut + button + insets).
     */
    public int getCloseReservedWidth() {
        if (!this.closeButton.isVisible()) {
            return 0;
        }
        int strut = 4;
        int gridSlack = 10;
        return this.getInsets().right + strut + this.closeButton.getPreferredSize().width + gridSlack;
    }

    public void showField() {
        this.card.show(this.textPanel, "field");
    }

    public void showLabel() {
        this.card.show(this.textPanel, "label");
    }

    public void setNode(TreepeaterNode node) {
        this.node = node;
        this.updateComponents();
    }

    public void updateComponents() {
        this.noPropagation = true;
        // Rebuild combo model from the current registry so any user changes are reflected.
        List<Status> statuses = Treepeater.getStatusRegistry().getAll();
        DefaultComboBoxModel<Status> model = new DefaultComboBoxModel<>(
                statuses.toArray(new Status[0]));
        this.box.setModel(model);

        this.label.setText(this.node.getName());
        this.field.setText(this.node.getName());
        this.box.setSelectedItem(this.node.getStatus());
        boolean isRoot = this.node.getParent() == null;
        this.closeButton.setVisible(!isRoot);
        this.noPropagation = false;
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        this.updateNode();
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        this.updateNode();
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        this.updateNode();
    }

    private void updateNode() {
        if (this.noPropagation) {
            return;
        }
        this.node.setName(this.field.getText());
    }

    public void selectNode() {
        this.node.select();
    }

    /**
     * Requests focus on the status dropdown (combo box) and opens its popup.
     * Used to programmatically trigger status selection UI.
     */
    public void openStatusPopup() {
        this.box.requestFocusInWindow();
        this.box.showPopup();
    }

    /**
     * Requests focus on the name JTextField and selects all text for editing.
     * Used to programmatically put the name field into edit mode.
     */
    public void focusNameFieldForEditing() {
        this.field.requestFocusInWindow();
        this.field.selectAll();
    }

    /**
     * Re-applies {@link TreeRowComboBoxUi} after {@code super.updateUI()} whenever Burp's theme
     * changes; otherwise the LAF replaces our UI and the value area paints incorrectly (often fully
     * transparent).
     */
    private static final class StatusComboBox extends JComboBox<Status> {
        @Override
        public void updateUI() {
            super.updateUI();
            TreeRowComboBoxUi.install(this);
            ListCellRenderer<? super Status> r = getRenderer();
            if (r instanceof JComponent) {
                SwingUtilities.updateComponentTreeUI((JComponent) r);
            }
        }
    }
}

