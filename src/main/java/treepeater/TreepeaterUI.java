package treepeater;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;

import treepeater.tree.RequestTreeNode;
import treepeater.draggable.RequestTreeNodeSimple;
import treepeater.requestResponse.RequestResponsePanel;
import treepeater.requestResponse.RequestResponseTab;

public class TreepeaterUI extends JSplitPane {
    private static final Dimension MIN_LEFT_PANEL_SIZE = new Dimension(240, 0);

    JTabbedPane requestResponseTabbedPane;
    TreepeaterModel model;
    HashMap<RequestTreeNode, RequestResponsePanel> tabMap;

    public TreepeaterUI(TreepeaterModel model) {
        super(JSplitPane.HORIZONTAL_SPLIT);

        this.model = model;
        this.requestResponseTabbedPane = new JTabbedPane();
        this.requestResponseTabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        this.setRightComponent(this.requestResponseTabbedPane);
        this.tabMap = new HashMap<>();

        this.setDividerLocation(0.3);
        this.resetToPreferredSizes();

        if (model.getRequestCount() > 0) {
            this.setLeftComponent(this.buildTreePanel());
        } else {
            this.setLeftComponent(this.buildDefaultLeftPanel());
        }

        for (RequestTreeNode node : model.getTabs()) {
            this.addTab(node);
        }

        if (model.getActiveNode() != null) {
            this.openTab(model.getActiveNode());
        }

        model.getTree().getTreeModel().addTreeModelListener(new TreeModelListener() {

            @Override
            public void treeNodesChanged(TreeModelEvent e) {
            }

            @Override
            public void treeNodesInserted(TreeModelEvent e) {
                if (model.getRequestCount() > 0) {
                    JComponent leftPanel = TreepeaterUI.this.buildTreePanel();
                    TreepeaterUI.this.setLeftComponent(leftPanel);
                }
            }

            @Override
            public void treeNodesRemoved(TreeModelEvent e) {
                RequestTreeNode root = (RequestTreeNode) model.getTree().getTreeModel().getRoot();
                if (root.getChildCount() == 0) {
                    TreepeaterUI.this.setLeftComponent(TreepeaterUI.this.buildDefaultLeftPanel());
                }
            }

            @Override
            public void treeStructureChanged(TreeModelEvent e) {
            }
            
        });


        model.addListener(new TreepeaterModelListener() {

            @Override
            public void onOpenTab(RequestTreeNode node) {
                TreepeaterUI.this.openTab(node);
            }

            @Override
            public void onCloseTab(RequestTreeNode node) {
                TreepeaterUI.this.removeTab(node);
            }

            @Override
            public void onNewTab(RequestTreeNode node) {
                TreepeaterUI.this.addTab(node);
            }
        });
    }

    private void openTab(RequestTreeNode node) {
        RequestResponsePanel panel = this.tabMap.get(node);
        if (panel == null) {
            Treepeater.api.logging().logToError("No tab found for node " + node.getId());
            return;
        }

        this.requestResponseTabbedPane.setSelectedComponent(panel);
    }

    private void addTab(RequestTreeNode node) {
        RequestResponsePanel panel = new RequestResponsePanel(
                this.model,
                node,
                this::selectPreviousRequestResponseTab,
                this::selectNextRequestResponseTab);
        int index = this.requestResponseTabbedPane.getTabCount();
        this.requestResponseTabbedPane.add(node.getName(), panel);

        RequestResponseTab tab = new RequestResponseTab(node);
        tab.addActionListener(e -> this.model.removeTab(index));
        this.requestResponseTabbedPane.setTabComponentAt(index, tab);
        this.requestResponseTabbedPane.setSelectedIndex(index);
        tabMap.put(node, panel);
    }

    private void removeTab(RequestTreeNode node) {
        RequestResponsePanel requestResponsePanel = this.tabMap.get(node);
        if (requestResponsePanel == null) {
            Treepeater.api.logging().logToError("No tab found for node " + node.getId());
            return;
        }
        this.requestResponseTabbedPane.remove(requestResponsePanel);;
        this.tabMap.remove(node);
    }

    /**
     * Activates the previous tab in {@link #requestResponseTabbedPane} (wraps). Uses
     * {@link RequestTreeNode#select()} so the model active node and tree stay in sync.
     */
    private void selectPreviousRequestResponseTab() {
        int n = this.requestResponseTabbedPane.getTabCount();
        if (n <= 1) {
            return;
        }
        int i = this.requestResponseTabbedPane.getSelectedIndex();
        if (i < 0) {
            i = 0;
        }
        int prev = (i - 1 + n) % n;
        this.model.getTabs().get(prev).select();
    }

    /**
     * Activates the next tab in {@link #requestResponseTabbedPane} (wraps).
     */
    private void selectNextRequestResponseTab() {
        int n = this.requestResponseTabbedPane.getTabCount();
        if (n <= 1) {
            return;
        }
        int i = this.requestResponseTabbedPane.getSelectedIndex();
        if (i < 0) {
            i = 0;
        }
        int next = (i + 1) % n;
        this.model.getTabs().get(next).select();
    }

    private JComponent buildDefaultLeftPanel() {
        JPanel leftPanel = new JPanel();
        leftPanel.add(new JLabel("Send a request to Treepeater"));
        return leftPanel;
    }

    private JComponent buildTreePanel() {
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BorderLayout());
        leftPanel.setMinimumSize(MIN_LEFT_PANEL_SIZE);

        JScrollPane scrollPane = new JScrollPane(this.model.getTree());
        this.model.getTree().setViewportContext(scrollPane.getViewport());
        scrollPane.setMinimumSize(MIN_LEFT_PANEL_SIZE);

        leftPanel.add(scrollPane, BorderLayout.CENTER);

        JButton syncButton = new JButton("Sync");
        syncButton.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                List<RequestTreeNodeSimple> allRequests = TreepeaterUI.this.model.getTree().toSimpleRepeaterList();

                for (int idx = 0; idx < allRequests.size(); idx++) {
                    RequestTreeNodeSimple node = allRequests.get(idx);
                    Treepeater.api.repeater().sendToRepeater(node.request, node.name);
                }
            }
            
        });

        leftPanel.add(syncButton, BorderLayout.PAGE_END);
        
        return leftPanel;
    }
}
