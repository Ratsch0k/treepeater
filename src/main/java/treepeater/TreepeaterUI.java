package treepeater;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.tree.TreePath;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import treepeater.ai.AgentToolContext;
import treepeater.api.tools.http.support.TabListingFormatter;
import treepeater.ai.TreepeaterTabAgentBridge;
import treepeater.ai.TreepeaterTabQueryMatcher;
import treepeater.ai.SearchTabRow;
import treepeater.components.EmptyStateHint;
import treepeater.hotkeys.HotkeyHandler;
import treepeater.hotkeys.HotkeyInstaller;
import treepeater.icons.CreateNewFolderIcon;
import treepeater.icons.DoubleArrowLeftIcon;
import treepeater.icons.DoubleArrowRightIcon;
import treepeater.icons.FileExportIcon;
import treepeater.TreepeaterModel.SiblingCopyPlacement;
import treepeater.tree.CustomTreeCellEditor.ProgrammaticEdit;
import treepeater.requestResponse.Status;
import treepeater.settings.StatusRegistry;
import treepeater.settings.TreepeaterSettings;
import treepeater.tree.FolderTreeNode;
import treepeater.tree.RequestTree;
import treepeater.tree.TreeFilterButton;
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
    private static final String TREE_CARD_NO_MATCHES = "noMatches";

    private static final String MAP_KEY_SELECT_FIRST_MATCH = "treepeater.selectFirstTreeMatch";

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
    private JTextField treeSearchField;
    private TreeFilterButton treeFilterButton;
    private EmptyStateHint treeNoMatchesHint;
    /** Guard so reflecting tree-side filter clears into the field does not re-apply the filter. */
    private boolean updatingTreeSearchFieldFromTree;

    private final HotkeyHandler hotkeyHandler = new HotkeyHandler(this);
    private final AtomicBoolean hotkeyHandlerRegistered = new AtomicBoolean(false);
    private final Map<String, Runnable> hotkeyActions = new HashMap<>();

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

        this.hotkeyActions.put(TreepeaterSettings.FOCUS_TREE_HOTKEY_SETTING, this::focusRequestTree);
        this.hotkeyActions.put(TreepeaterSettings.FOCUS_TREE_SEARCH_HOTKEY_SETTING, this::focusTreeSearch);
        this.hotkeyActions.put(TreepeaterSettings.OPEN_TREE_FILTER_HOTKEY_SETTING, this::openTreeFilterMenu);
        HotkeyInstaller.install(this, this.hotkeyHandler, this.hotkeyActions, this.hotkeyHandlerRegistered);
    }

    private void focusTreeSearch() {
        if (this.treeSearchField == null || !this.treeSearchField.isEnabled()) {
            return;
        }
        this.treeSearchField.requestFocusInWindow();
        this.treeSearchField.selectAll();
    }

    private void openTreeFilterMenu() {
        if (this.treeFilterButton == null || !this.treeFilterButton.isEnabled()) {
            return;
        }
        this.treeFilterButton.showMenu();
    }

    private void focusRequestTree() {
        SwingUtilities.invokeLater(() -> {
            RequestTree tree = this.model.getTree();
            RequestTreeNode active = this.model.getActiveNode();
            if (active != null) {
                TreePath path = new TreePath(active.getPath());
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
            }
            tree.requestFocusInWindow();
        });
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
        this.model.getTree().revealNode(copy);
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
        if (this.treeSearchField != null) {
            this.applyTreeSearchFieldPadding(this.treeSearchField);
        }
        if (this.treeFilterButton != null) {
            this.treeFilterButton.applyLocalTheme(TREE_PANEL_TOOLBAR_ICON_SIZE);
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
     * Clears an active search filter first so the new folder is visible.
     */
    private void createFolderFromToolbar(TreepeaterNode selectedOverride) {
        TreepeaterNode selected = selectedOverride;
        if (selected == null && this.model.getTree().getSelectionPath() != null) {
            Object comp = this.model.getTree().getSelectionPath().getLastPathComponent();
            if (comp instanceof TreepeaterNode) {
                selected = (TreepeaterNode) comp;
            }
        }
        this.model.getTree().clearFilters();
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
        RequestTree tree = this.model.getTree();
        TreepeaterNode root = (TreepeaterNode) tree.getTreeModel().getRoot();
        boolean empty = root == null || root.getChildCount() == 0;
        boolean noMatches = !empty && tree.isFiltering() && !tree.hasVisibleNodes();
        String card = empty
                ? TREE_CARD_EMPTY
                : (noMatches ? TREE_CARD_NO_MATCHES : TREE_CARD_TREE);
        if (noMatches && this.treeNoMatchesHint != null) {
            this.treeNoMatchesHint.setMessage("No nodes match the search or filters");
        }
        this.treeContentCardLayout.show(this.treeContentCardPanel, card);
        if (this.treePanelSyncButton != null) {
            this.treePanelSyncButton.setEnabled(tree.hasRequestNodes());
        }
        if (this.treeSearchField != null) {
            this.treeSearchField.setEnabled(!empty);
        }
        if (this.treeFilterButton != null) {
            this.treeFilterButton.setEnabled(!empty);
        }
    }

    private JComponent buildTreePanel() {
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BorderLayout());
        leftPanel.setMinimumSize(MIN_LEFT_PANEL_SIZE);

        RequestTree tree = this.model.getTree();
        JScrollPane scrollPane = new JScrollPane(tree);
        tree.setViewportContext(scrollPane.getViewport());
        tree.setActivateRequestFromKeyboardFollowUp(node -> {
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
        this.treeNoMatchesHint = new EmptyStateHint("");
        this.treeNoMatchesHint.setComponentPopupMenu(emptyHintMenu);
        this.treeContentCardPanel.add(scrollPane, TREE_CARD_TREE);
        this.treeContentCardPanel.add(emptyHint, TREE_CARD_EMPTY);
        this.treeContentCardPanel.add(this.treeNoMatchesHint, TREE_CARD_NO_MATCHES);

        leftPanel.add(this.treeContentCardPanel, BorderLayout.CENTER);

        // Vertical stack below the split: the existing header row plus a full-width search row.
        JPanel topStack = new JPanel();
        topStack.setLayout(new BoxLayout(topStack, BoxLayout.PAGE_AXIS));

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
            List<RequestTreeNodeSimple> allRequests = tree.toSimpleRepeaterList();

            for (int idx = 0; idx < allRequests.size(); idx++) {
                RequestTreeNodeSimple node = allRequests.get(idx);
                Treepeater.api.repeater().sendToRepeater(node.request, node.name);
            }
        });
        buttonStrip.add(syncButton);

        topBar.add(buttonStrip, BorderLayout.LINE_END);
        topStack.add(topBar);
        topStack.add(this.buildTreeSearchRow(tree));
        leftPanel.add(topStack, BorderLayout.PAGE_START);

        this.treePanelNewFolderButton = newFolderButton;
        this.treePanelSyncButton = syncButton;

        return leftPanel;
    }

    /**
     * The tree search field: every keystroke applies the filter immediately; Down/Enter select
     * the first visible request row and focus the tree. Stays in sync when the tree clears the
     * filter itself (e.g. "New Folder").
     */
    private JComponent buildTreeSearchRow(RequestTree tree) {
        JTextField searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Search by title\u2026");
        searchField.putClientProperty("JTextField.showClearButton", true);
        searchField.setToolTipText("Filter the tree by node title. Press Enter/Down to navigate to the first result");
        this.applyTreeSearchFieldPadding(searchField);

        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                TreepeaterUI.this.applyTreeSearchText();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                TreepeaterUI.this.applyTreeSearchText();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                TreepeaterUI.this.applyTreeSearchText();
            }
        });

        InputMap inputMap = searchField.getInputMap(JComponent.WHEN_FOCUSED);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), MAP_KEY_SELECT_FIRST_MATCH);
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), MAP_KEY_SELECT_FIRST_MATCH);
        ActionMap actionMap = searchField.getActionMap();
        actionMap.put(MAP_KEY_SELECT_FIRST_MATCH, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (tree.selectFirstVisibleRequestRow()) {
                    tree.requestFocusInWindow();
                }
            }
        });

        tree.addPropertyChangeListener(RequestTree.PROPERTY_FILTER_QUERY, ev -> {
            String query = (String) ev.getNewValue();
            if (Objects.equals(query, searchField.getText())) {
                TreepeaterUI.this.updateTreeEmptyState();
                return;
            }
            TreepeaterUI.this.updatingTreeSearchFieldFromTree = true;
            try {
                searchField.setText(query != null ? query : "");
            } finally {
                TreepeaterUI.this.updatingTreeSearchFieldFromTree = false;
            }
            TreepeaterUI.this.updateTreeEmptyState();
        });

        tree.addPropertyChangeListener(RequestTree.PROPERTY_TREE_FILTER,
                ev -> TreepeaterUI.this.updateTreeEmptyState());

        StatusRegistry statusRegistry = Treepeater.getStatusRegistry();
        if (statusRegistry != null) {
            statusRegistry.addChangeListener(() -> tree.retainStatusFilter(
                    statusRegistry.getAll().stream().map(Status::getId).toList()));
        }

        TreeFilterButton filterButton = new TreeFilterButton(tree);
        filterButton.applyLocalTheme(TREE_PANEL_TOOLBAR_ICON_SIZE);
        this.treeFilterButton = filterButton;

        this.treeSearchField = searchField;
        JPanel searchRow = new JPanel(new BorderLayout(4, 0));
        searchRow.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        searchRow.add(searchField, BorderLayout.CENTER);
        searchRow.add(filterButton, BorderLayout.LINE_END);
        return searchRow;
    }

    private void applyTreeSearchText() {
        if (this.updatingTreeSearchFieldFromTree || this.treeSearchField == null) {
            return;
        }
        this.model.getTree().setFilterQuery(this.treeSearchField.getText());
    }

    /** updateUI resets borders to the L&F default, so re-apply the search field's padding. */
    private void applyTreeSearchFieldPadding(JTextField field) {
        field.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
    }
}
