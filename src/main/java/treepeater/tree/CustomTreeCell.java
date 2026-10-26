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
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import treepeater.Treepeater;
import treepeater.components.StatusComboBoxUi;
import treepeater.icons.CloseIcon;
import treepeater.requestResponse.Status;
import treepeater.components.StatusComboBox;
import treepeater.components.StatusComboBoxRenderer;


public class CustomTreeCell extends JPanel implements DocumentListener {
    static final int MIN_TREE_ROW_INNER_WIDTH = 160;

    private JComboBox<Status> box;
    private HighlightedTitleLabel label;
    private JTextField field;
    private CardLayout card;
    private JPanel textPanel;
    private TreepeaterNode node;
    private FilteredTreeModel.NodeMatch nodeMatch;
    private JButton closeButton;
    private boolean noPropagation = false;

    public CustomTreeCell() {
        super(new BorderLayout());

        StatusComboBox box = new StatusComboBox();
        box.setRenderer(new StatusComboBoxRenderer());
        StatusComboBoxUi.install(box);
        box.setEnabled(true);
        box.addActionListener(e -> {
            Status selected = (Status) box.getSelectedItem();
            if (selected != null && this.node != null) {
                this.node.setStatus(selected);
            }
            CustomTreeCell.this.updateComponents();
            CustomTreeCell.this.repaintHostingTreeRowStrip();
        });

        this.setLayout(new GridBagLayout());
        this.setOpaque(false);
        this.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = 0;
        gc.insets = new Insets(3, 8, 3, 2);

        this.box = box;
        this.box.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 0));
        this.box.setOpaque(false);
        gc.gridx = 0;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        this.add(this.box, gc);

        this.label = new HighlightedTitleLabel();
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
        this.setNode(node, null);
    }

    /**
     * Binds the cell to {@code node} with optional search-filter display state
     */
    public void setNode(TreepeaterNode node, FilteredTreeModel.NodeMatch match) {
        this.node = node;
        this.nodeMatch = match;
        this.updateComponents();
    }

    /**
     * Row background and border are painted by {@link CustomTreeUI}, not this panel. After status
     * changes, repaint the full-width row so that chrome matches the combo/text styling.
     */
    private void repaintHostingTreeRowStrip() {
        if (this.node == null) {
            return;
        }
        JTree tree = (JTree) SwingUtilities.getAncestorOfClass(JTree.class, this);
        if (tree == null) {
            return;
        }
        TreePath path = new TreePath(this.node.getPath());
        int row = tree.getRowForPath(path);
        CustomTreeUI.repaintRowStrip(tree, row);
    }

    public void updateComponents() {
        this.noPropagation = true;
        // Rebuild combo model from the current registry so any user changes are reflected.
        List<Status> statuses = Treepeater.getStatusRegistry().getAll();
        DefaultComboBoxModel<Status> model = new DefaultComboBoxModel<Status>(
                statuses.toArray(new Status[0]));
        this.box.setModel(model);

        String name = this.node.getName();
        this.field.setText(name);
        this.applyTitleHighlight(name);
        this.box.setSelectedItem(this.node.getStatus());
        boolean isRoot = this.node.getParent() == null;
        this.closeButton.setVisible(!isRoot);
        this.noPropagation = false;
    }

    /**
     * Renders the title card according to the active search filter: direct match with bold
     * matched ranges, inherited (inside a matching folder) muted, path-only or unfiltered plain.
     */
    private void applyTitleHighlight(String name) {
        FilteredTreeModel.NodeMatch match = this.nodeMatch;
        if (match == null) {
            this.label.setTitle(name, null, false);
            return;
        }
        switch (match.kind()) {
            case DIRECT -> this.label.setTitle(name,
                    match.match() != null ? match.match().ranges() : null, false);
            case INHERITED -> this.label.setTitle(name, null, true);
            case PATH -> this.label.setTitle(name, null, false);
        }
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
}
