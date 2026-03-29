import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
import javax.swing.ScrollPaneConstants;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;

public class TreepeaterUI extends JSplitPane {
    private static final Dimension MIN_LEFT_PANEL_SIZE = new Dimension(240, 0);

    JTabbedPane requestResponseTabbedPane;

    public TreepeaterUI(TreepeaterModel model) {
        super(JSplitPane.HORIZONTAL_SPLIT);

        this.requestResponseTabbedPane = new JTabbedPane();
        this.requestResponseTabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        this.setRightComponent(this.requestResponseTabbedPane);

        this.setDividerLocation(0.3);
        this.resetToPreferredSizes();

        model.getTree().getTreeModel().addTreeModelListener(new TreeModelListener() {

            @Override
            public void treeNodesChanged(TreeModelEvent e) {
            }

            @Override
            public void treeNodesInserted(TreeModelEvent e) {
                if (model.getRequestCount() > 0) {
                    JPanel leftPanel = new JPanel();
                    leftPanel.setLayout(new BorderLayout());
                    leftPanel.setMinimumSize(MIN_LEFT_PANEL_SIZE);

                    JScrollPane scrollPane = new JScrollPane(model.getTree());
                    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                    scrollPane.setMinimumSize(MIN_LEFT_PANEL_SIZE);

                    leftPanel.add(scrollPane, BorderLayout.CENTER);

                    JButton syncButton = new JButton("Sync");
                    syncButton.addActionListener(new AbstractAction() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            List<RequestTreeNodeSimple> allRequests = model.getTree().toSimpleRepeaterList();

                            for (int idx = 0; idx < allRequests.size(); idx++) {
                                RequestTreeNodeSimple node = allRequests.get(idx);
                                Treepeater.api.repeater().sendToRepeater(node.request, node.name);
                            }
                        }
                        
                    });

                    leftPanel.add(syncButton, BorderLayout.PAGE_END);
                    
                    TreepeaterUI.this.setLeftComponent(leftPanel);
                }
            }

            @Override
            public void treeNodesRemoved(TreeModelEvent e) {
                if (model.getRequestCount() <= 0) {
                    TreepeaterUI.this.setLeftComponent(TreepeaterUI.this.buildDefaultLeftPanel());
                }
            }

            @Override
            public void treeStructureChanged(TreeModelEvent e) {
            }
            
        });

        HashMap<Integer, Integer> tabMap = new HashMap<>();

        model.addListener(new TreepeaterModelListener() {

            @Override
            public void onOpen(RequestTreeNode node) {
                Treepeater.api.logging().logToOutput("Opened request " + System.identityHashCode(node));

                if (tabMap.containsKey(node.getId())) {
                    Treepeater.api.logging().logToOutput("Changing active tab");
                    TreepeaterUI.this.requestResponseTabbedPane.setSelectedIndex(tabMap.get(node.getId()));
                    return;
                }

                Treepeater.api.logging().logToOutput("Create a new tab");

                RequestResponsePanel panel = new RequestResponsePanel(node);
                int index = TreepeaterUI.this.requestResponseTabbedPane.getTabCount();
                TreepeaterUI.this.requestResponseTabbedPane.add(node.getName(), panel);

                RequestResponseTab tab = new RequestResponseTab(node);
                tab.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        TreepeaterUI.this.requestResponseTabbedPane.remove(index);
                        tabMap.remove(node.getId());
                    }
                    
                });

                TreepeaterUI.this.requestResponseTabbedPane.setTabComponentAt(index, tab);
                tabMap.put(node.getId(), index);
            }
            
        });
        
        this.setLeftComponent(this.buildDefaultLeftPanel());
    }

    private JComponent buildDefaultLeftPanel() {
        JPanel leftPanel = new JPanel();
        leftPanel.add(new JLabel("Send a request to Treepeater"));
        return leftPanel;
    }
}
