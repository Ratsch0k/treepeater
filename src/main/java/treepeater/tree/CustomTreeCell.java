package treepeater.tree;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import treepeater.components.RoundedPanel;
import treepeater.requestResponse.Status;
import treepeater.requestResponse.StatusComboBoxRenderer;
import treepeater.Treepeater;


public class CustomTreeCell extends JPanel implements DocumentListener {
    static final int MIN_TREE_ROW_INNER_WIDTH = 160;

    private JComboBox<Status> box;
    private JLabel label;
    private JTextField field;
    private CardLayout card;
    private JPanel textPanel;
    private RequestTreeNode node;
    private RoundedPanel cellContent;

    public CustomTreeCell() {
        super(new BorderLayout());

        JButton button = new JButton("Click");
        Status[] statuStrings = {Status.TODO, Status.DONE, Status.FINDING, Status.COLLECTION};
        JComboBox<Status> box = new JComboBox<>(statuStrings);
        box.setRenderer(new StatusComboBoxRenderer());
        box.setEnabled(true);
        box.addActionListener(e -> {
            Treepeater.api.logging().logToOutput("Status changed: " + box.getSelectedItem());
            switch (box.getSelectedIndex()) {
                case 0: {
                    this.node.setStatus(Status.TODO);
                    break;
                }
                case 1: {
                    this.node.setStatus(Status.DONE);
                    break;
                }
                case 2: {
                    this.node.setStatus(Status.FINDING);
                    break;
                }
                case 3: {
                    this.node.setStatus(Status.COLLECTION);
                    break;
                }
            }
            CustomTreeCell.this.updateComponents();
        });
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Treepeater.api.logging().logToOutput("Clicked " + CustomTreeCell.this.node.getName());
            }
            
        });

        this.cellContent = new RoundedPanel();
        this.cellContent.setLayout(new GridBagLayout());
        this.cellContent.setOpaque(true);
        this.cellContent.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = 0;
        gc.insets = new Insets(2, 3, 2, 3);

        button.setEnabled(true);
        this.box = box;
        this.box.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        gc.gridx = 0;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        this.cellContent.add(this.box, gc);

        gc.gridx = 1;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        this.cellContent.add(Box.createHorizontalStrut(4), gc);
        
        this.label = new JLabel();
        this.label.setOpaque(true);
        this.field = new JTextField();
        this.field.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        this.field.setOpaque(true);

        this.field.getDocument().addDocumentListener(this);

        this.card = new CardLayout();
        this.textPanel = new JPanel(this.card);
        this.textPanel.setOpaque(true);

        this.textPanel.add(this.label, "label");
        this.textPanel.add(this.field, "field");
        int textRowHeight = Math.max(this.field.getPreferredSize().height, this.label.getPreferredSize().height);
        this.textPanel.setPreferredSize(new Dimension(0, textRowHeight));
        this.textPanel.setMinimumSize(new Dimension(MIN_TREE_ROW_INNER_WIDTH, textRowHeight));

        gc.gridx = 2;
        gc.weightx = 1;
        gc.fill =  GridBagConstraints.HORIZONTAL;
        this.cellContent.add(this.textPanel, gc);

        this.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 8));
        this.add(this.cellContent, BorderLayout.CENTER);
    }

    public int getButtonWidth() {
        return this.box.getPreferredSize().width;
    }

    public void showField() {
        this.card.show(this.textPanel, "field");
    }

    public void showLabel() {
        this.card.show(this.textPanel, "label");
    }

    public void setNode(RequestTreeNode node) {
        this.node = node;
        this.updateComponents();
    }

    public void updateComponents() {
        this.label.setText(this.node.getName());
        this.field.setText(this.node.getName());
        this.box.setSelectedItem(this.node.getStatus());
        Color fill = this.node.getStatus().getBackgroundColor();
        Color borderColor = this.node.getStatus().getBorderColor();
        this.cellContent.setBorderColor(borderColor);
        this.cellContent.setBackground(fill);
        this.label.setBackground(fill);
        this.field.setBackground(fill);
        this.box.setBackground(fill);
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
        this.node.setName(this.field.getText());
    }

    public void selectNode() {
        this.node.select();
    }

    public void clickButton() {
        Treepeater.api.logging().logToOutput("Open combobox");
        this.box.requestFocusInWindow();
        this.box.showPopup();
    }
}

