package treepeater;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreePath;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import treepeater.ai.AgentToolContext;
import treepeater.api.tools.http.support.TabListingFormatter;
import treepeater.ai.TreepeaterTabAgentBridge;
import treepeater.ai.TreepeaterTabQueryMatcher;
import treepeater.ai.SearchTabRow;
import treepeater.components.EmptyStateHint;
import treepeater.icons.CreateNewFolderIcon;
import treepeater.icons.DoubleArrowLeftIcon;
import treepeater.icons.DoubleArrowRightIcon;
import treepeater.icons.FileExportIcon;
import treepeater.TreepeaterModel.SiblingCopyPlacement;
import treepeater.tree.CustomTreeCellEditor.ProgrammaticEdit;
import treepeater.tree.FolderTreeNode;
import treepeater.tree.RequestTreeNode;
import treepeater.tree.TreepeaterNode;
import treepeater.draggable.RequestTreeNodeSimple;
import treepeater.requestResponse.RequestResponsePanel;
import treepeater.requestResponse.toolbar.RequestResponseToolbar;
import treepeater.requestResponse.toolbar.RequestResponseToolbarListener;
import treepeater.requestResponse.toolbar.ToolbarIconButton;
import treepeater.workspace.EditorWorkspacePanel;
import treepeater.workspace.TabGroupNode;

public class TreepeaterUI extends JSplitPane implements RequestResponseToolbarListener, TreepeaterTabAgentBridge {
    private static final Dimension MIN_LEFT_PANEL_SIZE = new Dimension(240, 0);

    private static final int TREE_PANEL_TOOLBAR_ICON_SIZE = 20;

    private static final int EXPAND_PANEL_MIN_OPEN_WIDTH = 120;

    private static final String TREE_CARD_TREE = "tree";
    private static final String TREE_CARD_EMPTY = "empty";

    private static int expandSplitDividerSizeWhenOpen() {
        int s = UIManager.getInt("SplitPane.dividerSize");
        return s > 0 ? s : 8;
    }

    EditorWorkspacePanel editorWorkspacePanel;
    TreepeaterModel model;

    private final RequestResponseToolbar sideToolbar;
    private final JSplitPane expandSplitPane;
    private final JPanel expandPanel;
    private final JPanel mainContent;
    private boolean expandPanelOpen;
    private double expandSplitEditorWidthFraction = 0.78;

    private RequestResponsePanel panelBoundForInfo;

    private ToolbarIconButton treePanelNewFolderButton;
    private ToolbarIconButton treePanelSyncButton;
    private CardLayout treeContentCardLayout;
    private JPanel treeContentCardPanel;

    public TreepeaterUI(TreepeaterModel model) {
        super(JSplitPane.HORIZONTAL_SPLIT);

        this.model = model;
        this.editorWorkspacePanel = new EditorWorkspacePanel(model);
        this.editorWorkspacePanel.setOnSelectionChanged(this::onRequestResponseTabSelectionChanged);

        this.sideToolbar = new RequestResponseToolbar(this.model, this);
        this.sideToolbar.addToolbarListener(this);
        this.expandPanel = this.sideToolbar.getToolbarPanel();

        this.expandSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, this.editorWorkspacePanel, this.expandPanel);
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

        model.getTree().setCreateFolderHandler(model::createFolder);
        this.activateTreePanel();

        this.editorWorkspacePanel.restoreInitialTabs();

        if (model.getActiveNode() != null) {
            TabGroupNode activeGroup = model.getWorkspace().findGroupContaining(model.getActiveNode());
            this.editorWorkspacePanel.handleOpenTab(
                    model.getActiveNode(),
                    activeGroup != null ? activeGroup.id() : model.getWorkspace().focusedTabGroupId());
        }

        this.onRequestResponseTabSelectionChanged();

        model.getTree().getTreeModel().addTreeModelListener(new TreeModelListener() {

            @Override
            public void treeNodesChanged(TreeModelEvent e) {
            }

            @Override
            public void treeNodesInserted(TreeModelEvent e) {
                TreepeaterUI.this.updateTreeEmptyState();
            }

            @Override
            public void treeNodesRemoved(TreeModelEvent e) {
                TreepeaterUI.this.updateTreeEmptyState();
            }

            @Override
            public void treeStructureChanged(TreeModelEvent e) {
                TreepeaterUI.this.updateTreeEmptyState();
            }

        });


        model.addListener(new TreepeaterModelListener() {

            @Override
            public void onOpenTab(RequestTreeNode node, String tabGroupId) {
                TreepeaterUI.this.editorWorkspacePanel.handleOpenTab(node, tabGroupId);
            }

            @Override
            public void onCloseTab(RequestTreeNode node) {
                TreepeaterUI.this.editorWorkspacePanel.handleCloseTab(node);
            }

            @Override
            public void onNewTab(RequestTreeNode node, String tabGroupId) {
                TreepeaterUI.this.editorWorkspacePanel.handleNewTab(node, tabGroupId);
            }

            @Override
            public void onTabMoved(RequestTreeNode node, String fromGroupId, String toGroupId, int dropIndex) {
                TreepeaterUI.this.editorWorkspacePanel.handleTabMoved(node, fromGroupId, toGroupId, dropIndex);
            }

            @Override
            public void onWorkspaceLayoutChanged() {
                TreepeaterUI.this.editorWorkspacePanel.handleWorkspaceLayoutChanged();
            }

            @Override
            public void onFocusedGroupChanged(String tabGroupId) {
                TreepeaterUI.this.editorWorkspacePanel.handleFocusedGroupChanged(tabGroupId);
            }

            @Override
            public void onTreeChanged() {
                TreepeaterUI.this.editorWorkspacePanel.refreshTabTitles();
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
        for (RequestTreeNode n : this.model.getAllOpenTabs()) {
            if (n.getId() == id) {
                RequestResponsePanel p = this.editorWorkspacePanel.findPanelForNode(n);
                if (p != null) {
                    return p.buildAgentToolContextForAi();
                }
            }
        }
        for (RequestResponsePanel p : this.editorWorkspacePanel.allPanels()) {
            if (p != null && p.getRequestNodeId() == id) {
                return p.buildAgentToolContextForAi();
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
            return TabListingFormatter.formatSearchTabsResponse(0, Math.max(0, offset), pageSize, false, List.of());
        } catch (InvocationTargetException e) {
            return "{\"error\":\"search_tabs failed\"}";
        }
        return holder[0] != null ? holder[0] : "{}";
    }

    @Override
    public String copyTreepeaterNode(int sourceRequestNodeId, String name, SiblingCopyPlacement placement) {
        final String[] holder = new String[1];
        Runnable r = () -> holder[0] = this.copyTreepeaterNodeOnEdt(sourceRequestNodeId, name, placement);
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                r.run();
            } else {
                SwingUtilities.invokeAndWait(r);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "{\"error\":\"copy_treepeater_node failed\"}";
        } catch (InvocationTargetException e) {
            return "{\"error\":\"copy_treepeater_node failed\"}";
        }
        return holder[0] != null ? holder[0] : "{\"error\":\"copy_treepeater_node failed\"}";
    }

    private String copyTreepeaterNodeOnEdt(int sourceRequestNodeId, String name, SiblingCopyPlacement placement) {
        if (name == null || name.trim().isEmpty()) {
            return "{\"error\":\"name required\"}";
        }
        String trimmedName = name.trim();
        RequestTreeNode source = this.model.findRequestNodeInTreeById(sourceRequestNodeId);
        if (source == null) {
            return "{\"error\":\"request node not found\"}";
        }
        RequestResponsePanel panel = this.findPanelForNode(source);
        HttpRequest request;
        HttpResponse response;
        if (panel != null) {
            request = panel.getLiveRequestFromEditor();
            response = panel.getLiveResponseFromEditor();
        } else {
            request = source.getRequest();
            response = source.getResponse();
        }
        if (request == null) {
            return "{\"error\":\"source has no request\"}";
        }
        RequestTreeNode copy =
                this.model.copyAsSiblingUnderSameParent(source, request, response, trimmedName, placement);
        if (copy == null) {
            return "{\"error\":\"cannot copy node without parent\"}";
        }
        TreePath path = new TreePath(copy.getPath());
        this.model.getTree().setSelectionPath(path);
        this.model.getTree().scrollPathToVisible(path);
        copy.select();
        return TabListingFormatter.formatCopyTreepeaterNodeResponse(copy.getId(), trimmedName);
    }

    private RequestResponsePanel findPanelForNode(RequestTreeNode node) {
        return this.editorWorkspacePanel.findPanelForNode(node);
    }

    private String searchTabsOnEdt(int offset, int pageSize, String queryOrNull) {
        String qRaw = queryOrNull != null ? queryOrNull.trim() : "";
        boolean filter = !qRaw.isEmpty();
        List<RequestTreeNode> tabs = this.model.getAllOpenTabs();
        List<SearchTabRow> matched = new ArrayList<>();
        RequestResponsePanel selected = this.getSelectedRequestResponsePanel();

        for (RequestTreeNode node : tabs) {
            RequestResponsePanel p = this.editorWorkspacePanel.findPanelForNode(node);
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
            if (!filter || TreepeaterTabQueryMatcher.matches(qRaw, method, url, title)) {
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
        return TabListingFormatter.formatSearchTabsResponse(total, off, pageSize, hasMore, page);
    }

    private static SearchTabRow buildSearchTabRow(int id, String title, boolean selected, String method, String url) {
        int max = TabListingFormatter.MAX_TAB_LIST_URL_CHARS;
        String urlSafe = url != null ? url : "";
        boolean trunc = urlSafe.length() > max;
        String u = trunc ? urlSafe.substring(0, max) : urlSafe;
        return new SearchTabRow(id, title, selected, method != null ? method : "", u, trunc);
    }

    private RequestResponsePanel getSelectedRequestResponsePanel() {
        return this.editorWorkspacePanel.getSelectedPanel();
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
            this.sideToolbar.getInspectorToolbarTab().setActivePanel(p);
        } else {
            this.sideToolbar.getInfoToolbarTab().clearDisplay();
            this.sideToolbar.getInspectorToolbarTab().setActivePanel(null);
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
        if (this.treePanelNewFolderButton != null) {
            this.treePanelNewFolderButton.applyLocalTheme(TREE_PANEL_TOOLBAR_ICON_SIZE);
        }
        if (this.treePanelSyncButton != null) {
            this.treePanelSyncButton.applyLocalTheme(TREE_PANEL_TOOLBAR_ICON_SIZE);
        }
        SwingUtilities.invokeLater(this::applyExpandDividerInteractionState);
    }

    private void activateTreePanel() {
        JComponent treePanel = this.buildTreePanel();
        this.setLeftComponent(treePanel);
        this.model.getTree().syncUiAfterBulkLoad();
        this.model.getTree().invalidateLayoutCache();
        this.updateTreeEmptyState();
        treePanel.revalidate();
        treePanel.repaint();
    }

    /**
     * Toolbar entry point for folder creation: targets the selected tree node (or the root) and
     * starts the rename after the tree card becomes visible, so editing starts on a laid-out tree.
     */
    private void createFolderFromToolbar(TreepeaterNode selectedOverride) {
        TreepeaterNode selected = selectedOverride;
        if (selected == null && this.model.getTree().getSelectionPath() != null) {
            Object comp = this.model.getTree().getSelectionPath().getLastPathComponent();
            if (comp instanceof TreepeaterNode) {
                selected = (TreepeaterNode) comp;
            }
        }
        FolderTreeNode folder = this.model.createFolder(selected);
        if (folder != null) {
            SwingUtilities.invokeLater(() -> {
                // The tree card may have just replaced the empty-state hint; force layout so row
                // bounds exist before editing starts.
                if (this.treeContentCardPanel != null) {
                    this.treeContentCardPanel.validate();
                }
                this.model.getTree().startProgrammaticEditForNode(folder, ProgrammaticEdit.RENAME);
            });
        }
    }

    private void updateTreeEmptyState() {
        if (this.treeContentCardLayout == null || this.treeContentCardPanel == null) {
            return;
        }
        TreepeaterNode root = (TreepeaterNode) this.model.getTree().getTreeModel().getRoot();
        boolean empty = root == null || root.getChildCount() == 0;
        this.treeContentCardLayout.show(this.treeContentCardPanel, empty ? TREE_CARD_EMPTY : TREE_CARD_TREE);
        if (this.treePanelSyncButton != null) {
            this.treePanelSyncButton.setEnabled(this.model.getTree().hasRequestNodes());
        }
    }

    private JComponent buildTreePanel() {
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BorderLayout());
        leftPanel.setMinimumSize(MIN_LEFT_PANEL_SIZE);

        JScrollPane scrollPane = new JScrollPane(this.model.getTree());
        this.model.getTree().setViewportContext(scrollPane.getViewport());
        this.model.getTree().setActivateRequestFromKeyboardFollowUp(node -> {
            RequestResponsePanel panel = TreepeaterUI.this.editorWorkspacePanel.findPanelForNode(node);
            if (panel != null) {
                panel.focusRequestEditor();
            }
        });
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setMinimumSize(MIN_LEFT_PANEL_SIZE);

        this.treeContentCardLayout = new CardLayout();
        this.treeContentCardPanel = new JPanel(this.treeContentCardLayout);
        EmptyStateHint emptyHint = new EmptyStateHint(
                "No requests yet. Send a request to Treepeater from anywhere in Burp, or create a folder with the button above.");
        JPopupMenu emptyHintMenu = new JPopupMenu();
        JMenuItem newFolderItem = new JMenuItem("New Folder");
        newFolderItem.addActionListener(ev -> this.createFolderFromToolbar(null));
        emptyHintMenu.add(newFolderItem);
        emptyHint.setComponentPopupMenu(emptyHintMenu);
        this.treeContentCardPanel.add(scrollPane, TREE_CARD_TREE);
        this.treeContentCardPanel.add(emptyHint, TREE_CARD_EMPTY);

        leftPanel.add(this.treeContentCardPanel, BorderLayout.CENTER);

        JPanel topBar = new JPanel(new BorderLayout());
        JLabel treeTitle = new JLabel("Treepeater");
        treeTitle.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        topBar.add(treeTitle, BorderLayout.LINE_START);

        JPanel buttonStrip = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));

        ToolbarIconButton newFolderButton = new ToolbarIconButton(new CreateNewFolderIcon());
        newFolderButton.applyLocalTheme(TREE_PANEL_TOOLBAR_ICON_SIZE);
        newFolderButton.setToolTipText("Create a new folder under the selected item (or at the root)");
        newFolderButton.addActionListener(ev -> this.createFolderFromToolbar(null));
        buttonStrip.add(newFolderButton);

        ToolbarIconButton syncButton = new ToolbarIconButton(new FileExportIcon());
        syncButton.addPropertyChangeListener("enabled",
                evt -> syncButton.applyLocalTheme(TREE_PANEL_TOOLBAR_ICON_SIZE));
        syncButton.applyLocalTheme(TREE_PANEL_TOOLBAR_ICON_SIZE);
        syncButton.setToolTipText("Send all requests in the tree to Repeater");
        syncButton.addActionListener(e -> {
            List<RequestTreeNodeSimple> allRequests = TreepeaterUI.this.model.getTree().toSimpleRepeaterList();

            for (int idx = 0; idx < allRequests.size(); idx++) {
                RequestTreeNodeSimple node = allRequests.get(idx);
                Treepeater.api.repeater().sendToRepeater(node.request, node.name);
            }
        });
        buttonStrip.add(syncButton);

        topBar.add(buttonStrip, BorderLayout.LINE_END);
        leftPanel.add(topBar, BorderLayout.PAGE_START);

        this.treePanelNewFolderButton = newFolderButton;
        this.treePanelSyncButton = syncButton;
        
        return leftPanel;
    }
}
