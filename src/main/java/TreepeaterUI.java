import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.ScrollPaneConstants;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;

import burp.api.montoya.ui.editor.EditorOptions;
import burp.api.montoya.ui.editor.HttpRequestEditor;

public class TreepeaterUI extends JSplitPane {
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

                    JScrollPane scrollPane = new JScrollPane(model.getTree());
                    scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                    
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

        HashMap<RequestTreeNode, Integer> tabMap = new HashMap<>();

        model.addListener(new TreepeaterModelListener() {

            @Override
            public void onOpen(RequestTreeNode node) {
                Treepeater.api.logging().logToOutput("Opened request " + System.identityHashCode(node));

                if (tabMap.containsKey(node)) {
                    Treepeater.api.logging().logToOutput("Changing active tab");
                    TreepeaterUI.this.requestResponseTabbedPane.setSelectedIndex(tabMap.get(node));
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
                        tabMap.remove(node);
                    }
                    
                });

                TreepeaterUI.this.requestResponseTabbedPane.setTabComponentAt(index, tab);
                tabMap.put(node, index);
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
