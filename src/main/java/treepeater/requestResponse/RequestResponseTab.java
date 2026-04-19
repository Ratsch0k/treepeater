package treepeater.requestResponse;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

import treepeater.icons.CloseIcon;
import treepeater.tree.RequestTreeNode;
import treepeater.tree.TreepeaterNode;
import treepeater.tree.TreepeaterNodeListener;

import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;

public class RequestResponseTab extends JPanel {
    JLabel label;
    JButton closeButton;

    public HashSet<ActionListener> listeners;

    private RequestTreeNode nameSourceNode;
    private final TreepeaterNodeListener nameChangeListener = new TreepeaterNodeListener() {

        @Override
        public void onSelect(TreepeaterNode node) {
        }

        @Override
        public void onNameChange(String newName) {
            RequestResponseTab.this.label.setText(newName);
            RequestResponseTab.this.label.repaint();
        }

        @Override
        public void onDelete(TreepeaterNode node) {
        }
    };
    
    public RequestResponseTab(RequestTreeNode node) {
        this.setLayout(new FlowLayout());

        this.label = new JLabel();
        this.add(this.label);
        this.listeners = new HashSet<>();

        closeButton = new JButton();
        closeButton.setIcon(new CloseIcon().withColor(UIManager.getColor("Label.foreground")));
        closeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                RequestResponseTab.this.listeners.forEach(l -> l.actionPerformed(e));
            }
            
        });
        closeButton.setBorderPainted(false);
        closeButton.setContentAreaFilled(false);
        closeButton.setFocusPainted(false);
        closeButton.setOpaque(false);
        closeButton.setMargin(new Insets(0, 4, 0, 0));
        closeButton.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        this.add(closeButton);

        bindNameSource(node);
    }

    private void bindNameSource(RequestTreeNode node) {
        if (this.nameSourceNode != null) {
            this.nameSourceNode.removeListener(this.nameChangeListener);
        }
        this.nameSourceNode = node;
        this.label.setText(node.getName());
        node.addListener(this.nameChangeListener);
    }

    public void retargetToNode(RequestTreeNode node) {
        bindNameSource(node);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        this.applyThemeLocalStyles();
    }

    private void applyThemeLocalStyles() {
        if (closeButton != null) {
            closeButton.setIcon(new CloseIcon().withColor(UIManager.getColor("Label.foreground")));
        }
    }

    public void addActionListener(ActionListener listener) {
        this.listeners.add(listener);
    }
}
