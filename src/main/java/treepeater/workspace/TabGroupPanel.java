package treepeater.workspace;

import java.awt.Dimension;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import treepeater.TreepeaterModel;
import treepeater.Utilities;
import treepeater.requestResponse.RequestResponsePanel;
import treepeater.requestResponse.RequestResponsePanelUi;
import treepeater.requestResponse.RequestResponseTab;
import treepeater.tree.RequestTreeNode;

public class TabGroupPanel extends JTabbedPane {
    private static final Dimension MIN_GROUP_SIZE = new Dimension(120, 80);

    private final String groupId;
    private final TreepeaterModel model;
    private final EditorWorkspaceInterface workspaceInterface;
    private JPopupMenu splitContextMenu;
    private JMenuItem splitRightItem;
    private JMenuItem splitDownItem;

    public TabGroupPanel(String groupId, TreepeaterModel model, EditorWorkspaceInterface workspaceInterface) {
        super(TOP, SCROLL_TAB_LAYOUT);
        this.groupId = groupId;
        this.model = model;
        this.workspaceInterface = workspaceInterface;
        this.setMinimumSize(MIN_GROUP_SIZE);
        RequestResponsePanelUi.applyCompactTabPane(this);
        this.installListeners();
        this.installSplitContextMenu();
        TabTransferHandler.installDropTarget(this, this);
    }

    public String groupId() {
        return this.groupId;
    }

    public TreepeaterModel model() {
        return this.model;
    }

    public EditorWorkspaceInterface workspaceInterface() {
        return this.workspaceInterface;
    }

    public void syncFromModel(TabGroupNode groupNode) {
        if (groupNode == null || !this.groupId.equals(groupNode.id())) {
            return;
        }
        Set<Integer> modelIds = new HashSet<>();
        for (RequestTreeNode node : groupNode.tabs()) {
            modelIds.add(node.getId());
        }
        for (int i = this.getTabCount() - 1; i >= 0; i--) {
            if (this.getComponentAt(i) instanceof RequestResponsePanel p) {
                if (!modelIds.contains(p.getRequestNodeId())) {
                    this.remove(i);
                }
            }
        }
        for (RequestTreeNode node : groupNode.tabs()) {
            if (this.tabIndexForNode(node) < 0) {
                this.addTabUi(node, false);
            }
        }
        int sel = groupNode.selectedIndex();
        if (sel >= 0 && sel < this.getTabCount()) {
            this.setSelectedIndex(sel);
        }
    }

    public void addTabUi(RequestTreeNode node) {
        addTabUi(node, true);
    }

    public void addTabUi(RequestTreeNode node, boolean select) {
        RequestResponsePanel panel = this.workspaceInterface.getOrCreatePanel(node);
        int index = this.getTabCount();
        // FlatLaf "more tabs" popup uses getTitleAt(); the custom tab component paints the strip.
        this.add(Utilities.slashPathForNode(node), panel);

        RequestResponseTab tab = new RequestResponseTab(node);
        tab.addActionListener(e -> this.model.removeTab(node));
        tab.enableDrag(
                new TabTransferHandler(this, node),
                () -> {
                    int tabIndex = this.tabIndexForNode(node);
                    if (tabIndex >= 0) {
                        this.setSelectedIndex(tabIndex);
                    }
                    this.workspaceInterface.onGroupFocused(this);
                });
        tab.installPopupMenu(
                this.splitContextMenu(),
                () -> {
                    this.workspaceInterface.onGroupFocused(this);
                    this.updateSplitMenuState();
                });
        this.setTabComponentAt(index, tab);

        if (select) {
            this.setSelectedIndex(index);
        }
    }

    public void openTabUi(RequestTreeNode node) {
        for (int i = 0; i < this.getTabCount(); i++) {
            if (this.getComponentAt(i) instanceof RequestResponsePanel p && p.getRequestNodeId() == node.getId()) {
                this.setSelectedIndex(i);
                return;
            }
        }
    }

    public void removeTabUi(RequestTreeNode node) {
        removeTabUi(node, true);
    }

    public void removeTabUi(RequestTreeNode node, boolean disposePanel) {
        for (int i = 0; i < this.getTabCount(); i++) {
            if (this.getComponentAt(i) instanceof RequestResponsePanel p && p.getRequestNodeId() == node.getId()) {
                RequestResponsePanel panel = (RequestResponsePanel) this.getComponentAt(i);
                this.remove(i);
                if (disposePanel) {
                    this.workspaceInterface.onTabClosed(node, panel);
                }
                return;
            }
        }
    }

    public RequestResponsePanel selectedPanel() {
        if (this.getSelectedComponent() instanceof RequestResponsePanel p) {
            return p;
        }
        return null;
    }

    public int tabIndexForNode(RequestTreeNode node) {
        for (int i = 0; i < this.getTabCount(); i++) {
            if (this.getComponentAt(i) instanceof RequestResponsePanel p && p.getRequestNodeId() == node.getId()) {
                return i;
            }
        }
        return -1;
    }

    public RequestTreeNode nodeAtTabIndex(int index) {
        if (index < 0 || index >= this.getTabCount()) {
            return null;
        }
        if (this.getComponentAt(index) instanceof RequestResponsePanel p) {
            return this.model.resolveRequestNode(p.getRequestNodeId(), null);
        }
        return null;
    }

    private void installListeners() {
        this.addChangeListener(
                new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e) {
                        TabGroupPanel.this.onSelectionChanged();
                    }
                });

        this.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        TabGroupPanel.this.workspaceInterface.onGroupFocused(TabGroupPanel.this);
                    }
                });

        this.addFocusListener(
                new FocusAdapter() {
                    @Override
                    public void focusGained(FocusEvent e) {
                        TabGroupPanel.this.workspaceInterface.onGroupFocused(TabGroupPanel.this);
                    }
                });
    }

    private void onSelectionChanged() {
        RequestResponsePanel p = this.selectedPanel();
        if (p == null) {
            return;
        }
        RequestTreeNode node = this.model.resolveRequestNode(p.getRequestNodeId(), null);
        if (node != null) {
            this.model.setActiveNode(node);
            this.workspaceInterface.onTabSelected(this, node);
        }
    }

    private JPopupMenu splitContextMenu() {
        if (this.splitContextMenu == null) {
            this.splitContextMenu = new JPopupMenu();
            this.splitRightItem =
                    this.splitContextMenu.add("Split Right");
            this.splitRightItem.addActionListener(
                    e -> this.workspaceInterface.onSplitRequested(this, SplitOrientation.HORIZONTAL));
            this.splitDownItem =
                    this.splitContextMenu.add("Split Down");
            this.splitDownItem.addActionListener(
                    e -> this.workspaceInterface.onSplitRequested(this, SplitOrientation.VERTICAL));
        }
        return this.splitContextMenu;
    }

    private void updateSplitMenuState() {
        this.splitContextMenu();
        boolean canSplit = this.getTabCount() > 1;
        this.splitRightItem.setEnabled(canSplit);
        this.splitDownItem.setEnabled(canSplit);
    }

    private void installSplitContextMenu() {
        this.installPopupTrigger(this);
    }

    private void installPopupTrigger(java.awt.Component component) {
        this.splitContextMenu();
        component.addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (e.isPopupTrigger()) {
                            showMenu(e);
                        }
                    }

                    @Override
                    public void mouseReleased(MouseEvent e) {
                        if (e.isPopupTrigger()) {
                            showMenu(e);
                        }
                    }

                    private void showMenu(MouseEvent e) {
                        TabGroupPanel.this.workspaceInterface.onGroupFocused(TabGroupPanel.this);
                        TabGroupPanel.this.updateSplitMenuState();
                        TabGroupPanel.this.splitContextMenu.show(component, e.getX(), e.getY());
                    }
                });
    }

    void selectPreviousTab() {
        int n = this.getTabCount();
        if (n <= 1) {
            return;
        }
        int i = this.getSelectedIndex();
        if (i < 0) {
            i = 0;
        }
        int prev = (i - 1 + n) % n;
        RequestTreeNode node = this.nodeAtTabIndex(prev);
        if (node != null) {
            node.select();
        }
    }

    void selectNextTab() {
        int n = this.getTabCount();
        if (n <= 1) {
            return;
        }
        int i = this.getSelectedIndex();
        if (i < 0) {
            i = 0;
        }
        int next = (i + 1) % n;
        RequestTreeNode node = this.nodeAtTabIndex(next);
        if (node != null) {
            node.select();
        }
    }

    void refreshTabTitles() {
        for (int i = 0; i < this.getTabCount(); i++) {
            RequestTreeNode node = this.nodeAtTabIndex(i);
            if (node != null) {
                this.syncTabPaneTitle(i, node);
            }
            if (this.getTabComponentAt(i) instanceof RequestResponseTab tab) {
                tab.refreshFromNode();
            }
        }
        revalidate();
        repaint();
    }

    /** Title for FlatLaf overflow popup and accessibility; not painted when a custom tab component is set. */
    private void syncTabPaneTitle(int tabIndex, RequestTreeNode node) {
        String title = Utilities.slashPathForNode(node);
        this.setTitleAt(tabIndex, title);
        this.setToolTipTextAt(tabIndex, title);
    }
}
