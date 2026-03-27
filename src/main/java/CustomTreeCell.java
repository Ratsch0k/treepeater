import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.time.Instant;
import java.time.LocalTime;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;


public class CustomTreeCell extends JPanel implements DocumentListener {
    private JComboBox box;
    private JLabel label;
    private JTextField field;
    private CardLayout card;
    private JPanel textPanel;
    private RequestTreeNode node;
    private JPanel cellContent;

    public CustomTreeCell() {
        super();

        JButton button = new JButton("Click");
        Status[] statuStrings = {Status.TODO, Status.WORKING_ON_IT, Status.DONE, Status.COLLECTION};
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
                    this.node.setStatus(Status.WORKING_ON_IT);
                    break;
                }
                case 2: {
                    this.node.setStatus(Status.DONE);
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

        this.cellContent = new JPanel(new GridBagLayout());

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridy = 0;
        gc.insets = new Insets(2, 2, 2, 2);

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
        this.textPanel.setPreferredSize(new Dimension(500, 20));

        gc.gridx = 2;
        gc.weightx = 1;
        gc.fill =  GridBagConstraints.HORIZONTAL;
        this.cellContent.add(this.textPanel, gc);

        this.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
        this.cellContent.setOpaque(true);
        this.add(this.cellContent);
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
        this.cellContent.setBackground(this.node.getStatus().getColor());
        this.label.setBackground(this.node.getStatus().getColor());
        this.field.setBackground(this.node.getStatus().getColor());
        this.box.setBackground(this.node.getStatus().getColor());
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

