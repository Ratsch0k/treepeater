package treepeater;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.OptionalInt;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

import burp.api.montoya.http.message.requests.HttpRequest;

import treepeater.ai.AgentToolContext;
import treepeater.ai.HttpTargetTools;
import treepeater.ai.RepeaterTabAgentBridge;
import treepeater.ai.RepeaterTabQueryMatcher;
import treepeater.ai.SearchTabRow;
import treepeater.icons.DoubleArrowLeftIcon;
import treepeater.icons.DoubleArrowRightIcon;
import treepeater.requestResponse.RequestResponsePanel;
import treepeater.requestResponse.RequestResponseTab;
import treepeater.requestResponse.toolbar.RequestResponseToolbar;
import treepeater.requestResponse.toolbar.RequestResponseToolbarListener;
import treepeater.tree.RequestTreeNode;
import treepeater.draggable.RequestTreeNodeSimple;

public class TreepeaterUI extends JSplitPane implements RequestResponseToolbarListener, RepeaterTabAgentBridge {
    private static final Dimension MIN_LEFT_PANEL_SIZE = new Dimension(240, 0);

    private static final int EXPAND_PANEL_MIN_OPEN_WIDTH = 120;

    private static int expandSplitDividerSizeWhenOpen() {
        int s = UIManager.getInt("SplitPane.dividerSize");
        return s > 0 ? s : 8;
    }

    JTabbedPane requestResponseTabbedPane;
    TreepeaterModel model;
    HashMap<RequestTreeNode, RequestResponsePanel> tabMap;
    private boolean treePanelActive;

    private final RequestResponseToolbar sideToolbar;
    private final JSplitPane expandSplitPane;
    private final JPanel expandPanel;
    private final JPanel mainContent;
    private boolean expandPanelOpen;
    private double expandSplitEditorWidthFraction = 0.78;

    private RequestResponsePanel panelBoundForInfo;

    public TreepeaterUI(TreepeaterModel model) {
        super(JSplitPane.HORIZONTAL_SPLIT);

        this.model = model;
        this.requestResponseTabbedPane = new JTabbedPane();
        this.requestResponseTabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        this.tabMap = new HashMap<>();

        this.sideToolbar = new RequestResponseToolbar(this.model, this);
        this.sideToolbar.addToolbarListener(this);
        this.expandPanel = this.sideToolbar.getToolbarPanel();

        this.expandSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, this.requestResponseTabbedPane, this.expandPanel);
        this.expandSplitPane.setResizeWeight(1.0);
        this.expandSplitPane.setOneTouchExpandable(false);
        this.expandSplitPane.setContinuousLayout(true);
        this.syncExpandSplitInteraction();

        this.mainContent = new JPanel(new BorderLayout());
        this.mainContent.add(this.expandSplitPane, BorderLayout.CENTER);
        this.mainContent.add(this.sideToolbar, BorderLayout.LINE_END);

        this.installExpandSplitInitiallyCollapsed();

        JPanel rightWrap = new JPanel(new BorderLayout());
        rightWrap.add(this.mainContent, BorderLayout.CENTER);

        this.setRightComponent(rightWrap);

        this.setDividerLocation(0.3);
        this.resetToPreferredSizes();

        if (model.getRequestCount() > 0) {
            this.treePanelActive = true;
            this.setLeftComponent(this.buildTreePanel());
        } else {
            this.treePanelActive = false;
            this.setLeftComponent(this.buildDefaultLeftPanel());
        }

        for (RequestTreeNode node : model.getTabs()) {
            this.addTab(node);
        }

        if (model.getActiveNode() != null) {
            this.openTab(model.getActiveNode());
        }

        this.requestResponseTabbedPane.addChangeListener(
                new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e) {
                        TreepeaterUI.this.onRequestResponseTabSelectionChanged();
                    }
                });
        this.onRequestResponseTabSelectionChanged();

        model.getTree().getTreeModel().addTreeModelListener(new TreeModelListener() {

            @Override
            public void treeNodesChanged(TreeModelEvent e) {
            }

            @Override
            public void treeNodesInserted(TreeModelEvent e) {
                if (!treePanelActive && model.getRequestCount() > 0) {
                    treePanelActive = true;
                    TreepeaterUI.this.setLeftComponent(TreepeaterUI.this.buildTreePanel());
                }
            }

            @Override
            public void treeNodesRemoved(TreeModelEvent e) {
                RequestTreeNode root = (RequestTreeNode) model.getTree().getTreeModel().getRoot();
                if (root.getChildCount() == 0) {
                    treePanelActive = false;
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

        this.sideToolbar.applyLocalTheme();
    }

    @Override
    public AgentToolContext contextForAgent(OptionalInt requestNodeId) {
        final AgentToolContext[] holder = new AgentToolContext[1];
        Runnable r = () -> holder[0] = this.contextForAgentOnEdt(requestNodeId);
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                r.run();
            } else {
                SwingUtilities.invokeAndWait(r);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) {
                throw re;
            }
            if (c instanceof Error err) {
                throw err;
            }
            return null;
        }
        return holder[0];
    }

    @Override
    public int uiSelectedRequestNodeIdForToolCard() {
        RequestResponsePanel p = this.getSelectedRequestResponsePanel();
        return p != null ? p.getRequestNodeId() : Integer.MIN_VALUE;
    }

    private AgentToolContext contextForAgentOnEdt(OptionalInt requestNodeId) {
        if (requestNodeId.isEmpty()) {
            RequestResponsePanel p = this.getSelectedRequestResponsePanel();
            return p != null ? p.buildAgentToolContextForAi() : null;
        }
        int id = requestNodeId.getAsInt();
        for (RequestTreeNode n : this.model.getTabs()) {
            if (n.getId() == id) {
                RequestResponsePanel p = this.tabMap.get(n);
                return p != null ? p.buildAgentToolContextForAi() : null;
            }
        }
        return null;
    }

    @Override
    public String searchTabs(int offset, int pageSize, String queryOrNull) {
        final String[] holder = new String[1];
        Runnable r = () -> holder[0] = this.searchTabsOnEdt(offset, pageSize, queryOrNull);
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                r.run();
            } else {
                SwingUtilities.invokeAndWait(r);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return HttpTargetTools.formatSearchTabsResponse(0, Math.max(0, offset), pageSize, false, List.of());
        } catch (InvocationTargetException e) {
            return "{\"error\":\"search_tabs failed\"}";
        }
        return holder[0] != null ? holder[0] : "{}";
    }

    private String searchTabsOnEdt(int offset, int pageSize, String queryOrNull) {
        String qRaw = queryOrNull != null ? queryOrNull.trim() : "";
        boolean filter = !qRaw.isEmpty();
        List<RequestTreeNode> tabs = this.model.getTabs();
        List<SearchTabRow> matched = new ArrayList<>();
        Component selected = this.requestResponseTabbedPane.getSelectedComponent();

        for (RequestTreeNode node : tabs) {
            RequestResponsePanel p = this.tabMap.get(node);
            String method = "";
            String url = "";
            if (p != null) {
                HttpRequest req = p.getLiveRequestFromEditor();
                if (req != null) {
                    try {
                        String m = req.method();
                        method = m != null ? m : "";
                    } catch (Exception ignored) {
                    }
                    try {
                        String u = req.url();
                        url = u != null ? u : "";
                    } catch (Exception ignored) {
                    }
                }
            }
            String title = node.getName() != null ? node.getName() : "";
            if (!filter || RepeaterTabQueryMatcher.matches(qRaw, method, url, title)) {
                boolean isSel = p != null && p == selected;
                matched.add(buildSearchTabRow(node.getId(), title, isSel, method, url));
            }
        }
        int total = matched.size();
        int off = offset;
        if (off < 0) {
            off = 0;
        }
        if (off > total) {
            off = total;
        }
        int end = Math.min(off + pageSize, total);
        List<SearchTabRow> page = matched.subList(off, end);
        boolean hasMore = end < total;
        return HttpTargetTools.formatSearchTabsResponse(total, off, pageSize, hasMore, page);
    }

    private static SearchTabRow buildSearchTabRow(int id, String title, boolean selected, String method, String url) {
        int max = HttpTargetTools.MAX_TAB_LIST_URL_CHARS;
        String urlSafe = url != null ? url : "";
        boolean trunc = urlSafe.length() > max;
        String u = trunc ? urlSafe.substring(0, max) : urlSafe;
        return new SearchTabRow(id, title, selected, method != null ? method : "", u, trunc);
    }

    private RequestResponsePanel getSelectedRequestResponsePanel() {
        Component c = this.requestResponseTabbedPane.getSelectedComponent();
        return c instanceof RequestResponsePanel p ? p : null;
    }

    private void onRequestResponseTabSelectionChanged() {
        if (this.panelBoundForInfo != null) {
            this.panelBoundForInfo.removeRequestResponseChangeListener(this.sideToolbar.getInfoToolbarTab());
            this.panelBoundForInfo = null;
        }
        RequestResponsePanel p = this.getSelectedRequestResponsePanel();
        if (p != null) {
            p.addRequestResponseChangeListener(this.sideToolbar.getInfoToolbarTab());
            p.refreshToolbarLinkedInfo();
            this.panelBoundForInfo = p;
        } else {
            this.sideToolbar.getInfoToolbarTab().clearDisplay();
        }
    }

    @Override
    public void onToolbarOpen() {
        this.toggleExpandPanel();
    }

    @Override
    public void onToolbarClose() {
        this.toggleExpandPanel();
    }

    private void installExpandSplitInitiallyCollapsed() {
        this.expandSplitPane.addComponentListener(
                new ComponentAdapter() {
                    private boolean laidOut;

                    @Override
                    public void componentResized(ComponentEvent e) {
                        if (this.laidOut || TreepeaterUI.this.expandSplitPane.getWidth() < 32) {
                            return;
                        }
                        this.laidOut = true;
                        TreepeaterUI.this.expandSplitPane.setDividerLocation(1.0d);
                        TreepeaterUI.this.syncExpandSplitInteraction();
                        TreepeaterUI.this.expandSplitPane.removeComponentListener(this);
                    }
                });
    }

    private void syncExpandSplitInteraction() {
        this.applyExpandPanelMinSizeForState();
        this.applyExpandDividerInteractionState();
    }

    private void applyExpandPanelMinSizeForState() {
        int minW = this.expandPanelOpen ? EXPAND_PANEL_MIN_OPEN_WIDTH : 0;
        this.expandPanel.setMinimumSize(new Dimension(minW, 0));
        this.expandSplitPane.revalidate();
    }

    private void applyExpandDividerInteractionState() {
        if (this.expandPanelOpen) {
            this.expandSplitPane.setDividerSize(expandSplitDividerSizeWhenOpen());
        } else {
            this.expandSplitPane.setDividerSize(0);
        }
        if (!(this.expandSplitPane.getUI() instanceof BasicSplitPaneUI)) {
            return;
        }
        BasicSplitPaneDivider divider = ((BasicSplitPaneUI) this.expandSplitPane.getUI()).getDivider();
        if (divider == null) {
            return;
        }
        divider.setEnabled(this.expandPanelOpen);
    }

    private void toggleExpandPanel() {
        if (this.expandPanelOpen) {
            int w = this.expandSplitPane.getWidth();
            if (w > 0) {
                double editorFrac = this.expandSplitPane.getDividerLocation() / (double) w;
                this.expandSplitEditorWidthFraction = Math.max(0.35d, Math.min(0.96d, editorFrac));
            }
            this.sideToolbar.getExpandButton().setIcon(new DoubleArrowLeftIcon().withSize(24, 24).withColor(UIManager.getColor("Label.foreground")));
            this.expandPanelOpen = false;
            this.expandSplitPane.setResizeWeight(1.0);
            this.syncExpandSplitInteraction();
            SwingUtilities.invokeLater(() -> this.expandSplitPane.setDividerLocation(1.0d));
        } else {
            this.sideToolbar.getExpandButton().setIcon(new DoubleArrowRightIcon().withSize(24, 24).withColor(UIManager.getColor("Label.foreground")));
            this.expandPanelOpen = true;
            this.expandSplitPane.setResizeWeight(0.78);
            this.syncExpandSplitInteraction();
            SwingUtilities.invokeLater(() -> this.expandSplitPane.setDividerLocation(this.expandSplitEditorWidthFraction));
        }
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (this.sideToolbar != null) {
            this.sideToolbar.applyLocalTheme();
        }
        SwingUtilities.invokeLater(this::applyExpandDividerInteractionState);
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
        this.requestResponseTabbedPane.remove(requestResponsePanel);
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
