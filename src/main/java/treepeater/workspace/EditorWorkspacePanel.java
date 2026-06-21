package treepeater.workspace;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JPanel;
import javax.swing.JSplitPane;

import treepeater.Treepeater;
import treepeater.TreepeaterModel;
import treepeater.requestResponse.RequestResponsePanel;
import treepeater.tree.RequestTreeNode;

public class EditorWorkspacePanel extends JPanel implements EditorWorkspaceInterface {
    private final TreepeaterModel model;
    private final Map<String, TabGroupPanel> groupPanels = new HashMap<>();
    private final Map<RequestTreeNode, RequestResponsePanel> tabMap = new HashMap<>();

    /** Guards model updates while programmatically restoring divider proportions after a rebuild. */
    private boolean applyingDividerLocation;

    /** Non-null when rebuild ran before this panel had a size; applied on first resize. */
    private List<SplitBinding> pendingSplitProportions;

    private TabGroupPanel focusedGroup;
    private Runnable onSelectionChanged;
    private Runnable onWorkspaceLayoutChanged;

    public EditorWorkspacePanel(TreepeaterModel model) {
        super(new BorderLayout());
        this.model = model;
        this.addComponentListener(
                new ComponentAdapter() {
                    @Override
                    public void componentResized(ComponentEvent e) {
                        EditorWorkspacePanel.this.applyPendingSplitProportions();
                    }
                });
        this.rebuildStructure();
    }

    public void setOnSelectionChanged(Runnable onSelectionChanged) {
        this.onSelectionChanged = onSelectionChanged;
    }

    public void setOnWorkspaceLayoutChanged(Runnable onWorkspaceLayoutChanged) {
        this.onWorkspaceLayoutChanged = onWorkspaceLayoutChanged;
    }

    public Collection<RequestResponsePanel> allPanels() {
        return Collections.unmodifiableCollection(this.tabMap.values());
    }

    public RequestResponsePanel getSelectedPanel() {
        if (this.focusedGroup != null) {
            RequestResponsePanel p = this.focusedGroup.selectedPanel();
            if (p != null) {
                return p;
            }
        }
        for (TabGroupPanel group : this.groupPanels.values()) {
            RequestResponsePanel p = group.selectedPanel();
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    public RequestResponsePanel findPanelForNode(RequestTreeNode node) {
        RequestResponsePanel p = this.tabMap.get(node);
        if (p != null) {
            return p;
        }
        if (node == null) {
            return null;
        }
        for (RequestResponsePanel cand : this.tabMap.values()) {
            if (cand != null && cand.getRequestNodeId() == node.getId()) {
                return cand;
            }
        }
        return null;
    }

    public void handleNewTab(RequestTreeNode node, String tabGroupId) {
        TabGroupPanel group = this.groupPanels.get(tabGroupId);
        if (group == null) {
            this.rebuildStructure();
            group = this.groupPanels.get(tabGroupId);
        }
        if (group != null) {
            group.addTabUi(node);
            this.focusedGroup = group;
        }
        this.notifySelectionChanged();
    }

    public void handleOpenTab(RequestTreeNode node, String tabGroupId) {
        TabGroupPanel group = this.groupPanels.get(tabGroupId);
        if (group == null) {
            this.rebuildStructure();
            group = this.groupPanels.get(tabGroupId);
        }
        if (group != null) {
            group.openTabUi(node);
            this.focusedGroup = group;
        }
        this.notifySelectionChanged();
    }

    public void handleCloseTab(RequestTreeNode node) {
        for (TabGroupPanel group : this.groupPanels.values()) {
            if (group.tabIndexForNode(node) >= 0) {
                group.removeTabUi(node);
                break;
            }
        }
        this.notifySelectionChanged();
    }

    public void handleWorkspaceLayoutChanged() {
        this.rebuildStructure();
        for (TabGroupNode groupNode : this.model.getWorkspace().allTabGroups()) {
            TabGroupPanel panel = this.groupPanels.get(groupNode.id());
            if (panel != null) {
                panel.syncFromModel(groupNode);
            }
        }
        TabGroupPanel focused = this.groupPanels.get(this.model.getWorkspace().focusedTabGroupId());
        if (focused != null) {
            this.focusedGroup = focused;
        }
        if (this.onWorkspaceLayoutChanged != null) {
            this.onWorkspaceLayoutChanged.run();
        }
        this.notifySelectionChanged();
    }

    public void handleFocusedGroupChanged(String tabGroupId) {
        TabGroupPanel group = this.groupPanels.get(tabGroupId);
        if (group != null) {
            this.focusedGroup = group;
        }
    }

    public void restoreInitialTabs() {
        this.rebuildStructure();
        EditorWorkspace workspace = this.model.getWorkspace();
        for (TabGroupNode groupNode : workspace.allTabGroups()) {
            TabGroupPanel panel = this.groupPanels.get(groupNode.id());
            if (panel == null) {
                continue;
            }
            for (RequestTreeNode node : groupNode.tabs()) {
                panel.addTabUi(node, false);
            }
            int sel = groupNode.selectedIndex();
            if (sel >= 0 && sel < panel.getTabCount()) {
                panel.setSelectedIndex(sel);
            }
        }
        TabGroupPanel focused = this.groupPanels.get(workspace.focusedTabGroupId());
        if (focused != null) {
            this.focusedGroup = focused;
        }
        if (this.model.getActiveNode() != null) {
            TabGroupNode g = workspace.findGroupContaining(this.model.getActiveNode());
            if (g != null) {
                TabGroupPanel p = this.groupPanels.get(g.id());
                if (p != null) {
                    p.openTabUi(this.model.getActiveNode());
                    this.focusedGroup = p;
                }
            }
        }
        this.notifySelectionChanged();
    }

    public void rebuildStructure() {
        EditorWorkspace workspace = this.model.getWorkspace();
        Set<String> neededIds = new HashSet<>();
        WorkspaceWalk.walk(
                workspace.root(),
                new WorkspaceVisitor<Void>() {
                    @Override
                    public Void visitGroup(TabGroupNode g) {
                        neededIds.add(g.id());
                        return null;
                    }

                    @Override
                    public Void visitSplit(SplitNode s) {
                        collectGroupIds(s.first(), neededIds);
                        collectGroupIds(s.second(), neededIds);
                        return null;
                    }
                });

        for (String id : new HashSet<>(this.groupPanels.keySet())) {
            if (!neededIds.contains(id)) {
                this.groupPanels.remove(id);
            }
        }

        for (String id : neededIds) {
            this.groupPanels.computeIfAbsent(id, gid -> new TabGroupPanel(gid, this.model, this));
        }

        List<SplitBinding> splits = new ArrayList<>();
        Component built = buildComponent(workspace.root(), splits);
        this.removeAll();
        this.add(built, BorderLayout.CENTER);
        this.revalidate();
        this.validate();
        if (!applySplitProportions(splits)) {
            this.pendingSplitProportions = splits;
        } else {
            this.pendingSplitProportions = null;
        }
        this.repaint();

        TabGroupPanel focused = this.groupPanels.get(workspace.focusedTabGroupId());
        if (focused != null) {
            this.focusedGroup = focused;
        }
    }

    private static void collectGroupIds(WorkspaceNode node, Set<String> ids) {
        WorkspaceWalk.walk(
                node,
                new WorkspaceVisitor<Void>() {
                    @Override
                    public Void visitGroup(TabGroupNode g) {
                        ids.add(g.id());
                        return null;
                    }

                    @Override
                    public Void visitSplit(SplitNode s) {
                        collectGroupIds(s.first(), ids);
                        collectGroupIds(s.second(), ids);
                        return null;
                    }
                });
    }

    private Component buildComponent(WorkspaceNode node, List<SplitBinding> splits) {
        if (node instanceof TabGroupNode g) {
            return this.groupPanels.get(g.id());
        }
        if (node instanceof SplitNode s) {
            JSplitPane split = new JSplitPane(s.orientation().swingOrientation());
            split.setContinuousLayout(true);
            if (Treepeater.api != null) {
                Treepeater.api.userInterface().applyThemeToComponent(split);
            }
            split.setLeftComponent(wrapForSplit(buildComponent(s.first(), splits)));
            split.setRightComponent(wrapForSplit(buildComponent(s.second(), splits)));
            split.setResizeWeight(s.dividerProportion());
            attachDividerListener(split, s);
            splits.add(new SplitBinding(split, s));
            return split;
        }
        TabGroupPanel fallback = this.groupPanels.values().iterator().next();
        return fallback;
    }

    private static JPanel wrapForSplit(Component child) {
        if (child.getParent() != null) {
            child.getParent().remove(child);
        }
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(child, BorderLayout.CENTER);
        return wrap;
    }

    private void attachDividerListener(JSplitPane split, SplitNode splitNode) {
        split.addPropertyChangeListener(
                JSplitPane.DIVIDER_LOCATION_PROPERTY,
                evt -> {
                    if (this.applyingDividerLocation) {
                        return;
                    }
                    persistDivider(split, splitNode);
                });
    }

    private void persistDivider(JSplitPane split, SplitNode splitNode) {
        double proportion = readDividerProportion(split);
        if (Double.isNaN(proportion)) {
            return;
        }
        if (Math.abs(splitNode.dividerProportion() - proportion) > 0.01) {
            splitNode.setDividerProportion(proportion);
            Treepeater.saveState();
        }
    }

    private static double readDividerProportion(JSplitPane split) {
        int size = split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT ? split.getWidth() : split.getHeight();
        if (size <= 0) {
            return Double.NaN;
        }
        return split.getDividerLocation() / (double) size;
    }

    private void applyPendingSplitProportions() {
        List<SplitBinding> pending = this.pendingSplitProportions;
        if (pending == null || pending.isEmpty()) {
            return;
        }
        if (applySplitProportions(pending)) {
            this.pendingSplitProportions = null;
        }
    }

    /** @return {@code true} when every split in {@code splits} had a non-zero size and was positioned */
    private boolean applySplitProportions(List<SplitBinding> splits) {
        if (splits.isEmpty()) {
            return true;
        }
        boolean allApplied = true;
        this.applyingDividerLocation = true;
        try {
            for (SplitBinding binding : splits) {
                JSplitPane split = binding.split();
                int size =
                        split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT ? split.getWidth() : split.getHeight();
                if (size <= 0) {
                    allApplied = false;
                    continue;
                }
                split.setDividerLocation(binding.model().dividerProportion());
            }
        } finally {
            this.applyingDividerLocation = false;
        }
        return allApplied;
    }

    private record SplitBinding(JSplitPane split, SplitNode model) {}

    @Override
    public RequestResponsePanel getOrCreatePanel(RequestTreeNode node) {
        return this.tabMap.computeIfAbsent(
                node,
                n -> {
                    RequestResponsePanel panel =
                            new RequestResponsePanel(
                                    this.model,
                                    n,
                                    () -> this.selectPreviousInGroup(n),
                                    () -> this.selectNextInGroup(n));
                    WorkspaceFocus.install(panel, () -> this.onPanelFocused(panel));
                    return panel;
                });
    }

    private void onPanelFocused(RequestResponsePanel panel) {
        for (TabGroupPanel group : this.groupPanels.values()) {
            if (group.indexOfComponent(panel) >= 0) {
                this.onGroupFocused(group);
                return;
            }
        }
    }

    private void selectPreviousInGroup(RequestTreeNode node) {
        TabGroupNode group = this.model.getWorkspace().findGroupContaining(node);
        if (group == null) {
            return;
        }
        TabGroupPanel panel = this.groupPanels.get(group.id());
        if (panel != null) {
            panel.selectPreviousTab();
        }
    }

    private void selectNextInGroup(RequestTreeNode node) {
        TabGroupNode group = this.model.getWorkspace().findGroupContaining(node);
        if (group == null) {
            return;
        }
        TabGroupPanel panel = this.groupPanels.get(group.id());
        if (panel != null) {
            panel.selectNextTab();
        }
    }

    @Override
    public void onTabClosed(RequestTreeNode node, RequestResponsePanel panel) {
        this.tabMap.remove(node);
    }

    @Override
    public void onTabSelected(TabGroupPanel group, RequestTreeNode node) {
        this.focusedGroup = group;
        this.model.setFocusedTabGroupId(group.groupId());
        this.notifySelectionChanged();
    }

    @Override
    public void onGroupFocused(TabGroupPanel group) {
        this.focusedGroup = group;
        this.model.setFocusedTabGroupId(group.groupId());
        this.notifySelectionChanged();
    }

    @Override
    public void onSplitRequested(TabGroupPanel group, SplitOrientation orientation) {
        this.model.splitTabGroup(group.groupId(), orientation);
    }

    @Override
    public void onTabMoved(RequestTreeNode node, String fromGroupId, String toGroupId, int dropIndex) {
        this.model.moveTab(node, fromGroupId, toGroupId, dropIndex);
    }

    public void handleTabMoved(RequestTreeNode node, String fromGroupId, String toGroupId, int dropIndex) {
        if (!java.util.Objects.equals(fromGroupId, toGroupId)
                && this.model.getWorkspace().findGroupById(fromGroupId) == null) {
            this.handleWorkspaceLayoutChanged();
            return;
        }

        if (java.util.Objects.equals(fromGroupId, toGroupId)) {
            TabGroupPanel panel = this.groupPanels.get(toGroupId);
            TabGroupNode groupNode = this.model.getWorkspace().findGroupById(toGroupId);
            if (panel != null && groupNode != null) {
                panel.syncFromModel(groupNode);
            }
        } else {
            TabGroupPanel fromPanel = this.groupPanels.get(fromGroupId);
            TabGroupNode fromNode = this.model.getWorkspace().findGroupById(fromGroupId);
            if (fromPanel != null && fromNode != null) {
                fromPanel.syncFromModel(fromNode);
            }
            TabGroupPanel toPanel = this.groupPanels.get(toGroupId);
            TabGroupNode toNode = this.model.getWorkspace().findGroupById(toGroupId);
            if (toPanel != null && toNode != null) {
                toPanel.syncFromModel(toNode);
                this.focusedGroup = toPanel;
            }
        }
        this.notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        if (this.onSelectionChanged != null) {
            this.onSelectionChanged.run();
        }
    }

    public void refreshTabTitles() {
        for (TabGroupPanel group : this.groupPanels.values()) {
            group.refreshTabTitles();
        }
    }
}
