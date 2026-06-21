package treepeater.workspace;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import treepeater.tree.RequestTreeNode;

public final class EditorWorkspace {
    private WorkspaceNode root;
    private String focusedTabGroupId;

    public EditorWorkspace() {
        TabGroupNode initial = new TabGroupNode(TabGroupNode.newGroupId());
        this.root = initial;
        this.focusedTabGroupId = initial.id();
    }

    public EditorWorkspace(WorkspaceNode root, String focusedTabGroupId) {
        this.root = root != null ? root : new TabGroupNode(TabGroupNode.newGroupId());
        this.focusedTabGroupId = focusedTabGroupId;
        if (this.focusedTabGroupId == null || findGroupById(this.focusedTabGroupId) == null) {
            TabGroupNode first = firstTabGroup(this.root);
            this.focusedTabGroupId = first != null ? first.id() : new TabGroupNode(TabGroupNode.newGroupId()).id();
            if (first == null) {
                this.root = new TabGroupNode(this.focusedTabGroupId);
            }
        }
    }

    public WorkspaceNode root() {
        return this.root;
    }

    public void setRoot(WorkspaceNode root) {
        this.root = root != null ? root : new TabGroupNode(TabGroupNode.newGroupId());
    }

    public String focusedTabGroupId() {
        return this.focusedTabGroupId;
    }

    public void setFocusedTabGroupId(String focusedTabGroupId) {
        if (focusedTabGroupId != null && findGroupById(focusedTabGroupId) != null) {
            this.focusedTabGroupId = focusedTabGroupId;
        }
    }

    public TabGroupNode focusedGroup() {
        TabGroupNode g = findGroupById(this.focusedTabGroupId);
        if (g != null) {
            return g;
        }
        TabGroupNode first = firstTabGroup(this.root);
        if (first != null) {
            this.focusedTabGroupId = first.id();
            return first;
        }
        TabGroupNode created = new TabGroupNode(TabGroupNode.newGroupId());
        this.root = created;
        this.focusedTabGroupId = created.id();
        return created;
    }

    public TabGroupNode findGroupById(String id) {
        if (id == null) {
            return null;
        }
        return findGroupById(this.root, id);
    }

    private static TabGroupNode findGroupById(WorkspaceNode node, String id) {
        return WorkspaceWalk.walk(
                node,
                new WorkspaceVisitor<>() {
                    @Override
                    public TabGroupNode visitGroup(TabGroupNode g) {
                        return g.id().equals(id) ? g : null;
                    }

                    @Override
                    public TabGroupNode visitSplit(SplitNode s) {
                        TabGroupNode found = findGroupById(s.first(), id);
                        if (found != null) {
                            return found;
                        }
                        return findGroupById(s.second(), id);
                    }
                });
    }

    public TabGroupNode findGroupContaining(RequestTreeNode node) {
        if (node == null) {
            return null;
        }
        return findGroupContaining(this.root, node);
    }

    private static TabGroupNode findGroupContaining(WorkspaceNode workspaceNode, RequestTreeNode node) {
        return WorkspaceWalk.walk(
                workspaceNode,
                new WorkspaceVisitor<>() {
                    @Override
                    public TabGroupNode visitGroup(TabGroupNode g) {
                        return g.contains(node) ? g : null;
                    }

                    @Override
                    public TabGroupNode visitSplit(SplitNode s) {
                        TabGroupNode found = findGroupContaining(s.first(), node);
                        if (found != null) {
                            return found;
                        }
                        return findGroupContaining(s.second(), node);
                    }
                });
    }

    public boolean isOpen(RequestTreeNode node) {
        return findGroupContaining(node) != null;
    }

    public List<RequestTreeNode> allOpenTabs() {
        List<RequestTreeNode> out = new ArrayList<>();
        Set<RequestTreeNode> seen = new LinkedHashSet<>();
        collectTabs(this.root, out, seen);
        return out;
    }

    private static void collectTabs(WorkspaceNode node, List<RequestTreeNode> out, Set<RequestTreeNode> seen) {
        WorkspaceWalk.walk(
                node,
                new WorkspaceVisitor<Void>() {
                    @Override
                    public Void visitGroup(TabGroupNode g) {
                        for (RequestTreeNode tab : g.tabs()) {
                            if (seen.add(tab)) {
                                out.add(tab);
                            }
                        }
                        return null;
                    }

                    @Override
                    public Void visitSplit(SplitNode s) {
                        collectTabs(s.first(), out, seen);
                        collectTabs(s.second(), out, seen);
                        return null;
                    }
                });
    }

    public List<TabGroupNode> allTabGroups() {
        List<TabGroupNode> out = new ArrayList<>();
        collectGroups(this.root, out);
        return out;
    }

    private static void collectGroups(WorkspaceNode node, List<TabGroupNode> out) {
        WorkspaceWalk.walk(
                node,
                new WorkspaceVisitor<Void>() {
                    @Override
                    public Void visitGroup(TabGroupNode g) {
                        out.add(g);
                        return null;
                    }

                    @Override
                    public Void visitSplit(SplitNode s) {
                        collectGroups(s.first(), out);
                        collectGroups(s.second(), out);
                        return null;
                    }
                });
    }

    public void addTabToFocused(RequestTreeNode node) {
        Objects.requireNonNull(node, "node");
        TabGroupNode existing = findGroupContaining(node);
        if (existing != null) {
            existing.setSelectedIndex(existing.indexOf(node));
            this.focusedTabGroupId = existing.id();
            return;
        }
        TabGroupNode focused = focusedGroup();
        focused.addTab(node);
        this.focusedTabGroupId = focused.id();
    }

    /**
     * Removes {@code node} from the workspace. Returns {@code true} when the workspace tree shape
     * changed (for example an empty split group was collapsed), {@code false} when the tab was not
     * open or only tab membership changed within an unchanged layout.
     */
    public boolean removeTab(RequestTreeNode node) {
        TabGroupNode group = findGroupContaining(node);
        if (group == null) {
            return false;
        }
        group.removeTab(node);
        return collapseEmpty();
    }

    public void moveTab(RequestTreeNode node, String fromGroupId, String toGroupId, int dropIndex) {
        if (node == null || Objects.equals(fromGroupId, toGroupId)) {
            TabGroupNode group = findGroupContaining(node);
            if (group != null && group.id().equals(toGroupId)) {
                group.reorderTab(node, dropIndex);
            }
            return;
        }
        TabGroupNode from = findGroupById(fromGroupId);
        TabGroupNode to = findGroupById(toGroupId);
        if (from == null || to == null) {
            return;
        }
        if (!from.contains(node)) {
            return;
        }
        from.removeTab(node);
        int idx = Math.max(0, Math.min(dropIndex, to.tabs().size()));
        to.insertTab(idx, node);
        this.focusedTabGroupId = to.id();
        collapseEmpty();
    }

    public SplitResult splitGroup(String groupId, SplitOrientation orientation) {
        TabGroupNode target = findGroupById(groupId);
        if (target == null || target.tabs().size() <= 1) {
            return null;
        }
        RequestTreeNode activeTab = target.selectedTab();
        TabGroupNode newGroup = new TabGroupNode(TabGroupNode.newGroupId());

        if (activeTab != null) {
            target.removeTab(activeTab);
            newGroup.addTab(activeTab);
        }

        SplitNode split = new SplitNode(orientation, 0.5, target, newGroup);
        ParentRef parentRef = findParent(this.root, groupId);
        if (parentRef == null) {
            this.root = split;
        } else if (parentRef.isFirstChild()) {
            parentRef.split().setFirst(split);
        } else {
            parentRef.split().setSecond(split);
        }

        this.focusedTabGroupId = newGroup.id();
        return new SplitResult(split, target.id(), newGroup.id());
    }

    /** Collapses empty groups and ensures a focused group still exists. Returns whether the tree shape changed. */
    public boolean collapseEmpty() {
        WorkspaceNode rootBefore = this.root;
        String focusedBefore = this.focusedTabGroupId;
        this.root = collapseNode(this.root);
        ensureAtLeastOneGroup();
        if (findGroupById(this.focusedTabGroupId) == null) {
            TabGroupNode first = firstTabGroup(this.root);
            if (first != null) {
                this.focusedTabGroupId = first.id();
            }
        }
        return this.root != rootBefore || !Objects.equals(this.focusedTabGroupId, focusedBefore);
    }

    private static WorkspaceNode collapseNode(WorkspaceNode node) {
        if (node instanceof TabGroupNode g) {
            return g;
        }
        if (node instanceof SplitNode s) {
            WorkspaceNode first = collapseNode(s.first());
            WorkspaceNode second = collapseNode(s.second());

            if (first instanceof TabGroupNode fg && fg.isEmpty() && !(second instanceof TabGroupNode sg && sg.isEmpty())) {
                return second;
            }
            if (second instanceof TabGroupNode sg && sg.isEmpty() && !(first instanceof TabGroupNode fg && fg.isEmpty())) {
                return first;
            }
            if (first instanceof TabGroupNode fg && fg.isEmpty() && second instanceof TabGroupNode sg && sg.isEmpty()) {
                return fg;
            }

            s.setFirst(first);
            s.setSecond(second);
            return s;
        }
        return node;
    }

    private void ensureAtLeastOneGroup() {
        if (firstTabGroup(this.root) == null) {
            TabGroupNode created = new TabGroupNode(TabGroupNode.newGroupId());
            this.root = created;
            this.focusedTabGroupId = created.id();
        }
    }

    private static TabGroupNode firstTabGroup(WorkspaceNode node) {
        return WorkspaceWalk.walk(
                node,
                new WorkspaceVisitor<>() {
                    @Override
                    public TabGroupNode visitGroup(TabGroupNode g) {
                        return g;
                    }

                    @Override
                    public TabGroupNode visitSplit(SplitNode s) {
                        TabGroupNode found = firstTabGroup(s.first());
                        if (found != null) {
                            return found;
                        }
                        return firstTabGroup(s.second());
                    }
                });
    }

    private static ParentRef findParent(WorkspaceNode node, String groupId) {
        if (node instanceof SplitNode s) {
            if (s.first() instanceof TabGroupNode g && g.id().equals(groupId)) {
                return new ParentRef(s, true);
            }
            if (s.second() instanceof TabGroupNode g && g.id().equals(groupId)) {
                return new ParentRef(s, false);
            }
            ParentRef left = findParent(s.first(), groupId);
            if (left != null) {
                return left;
            }
            return findParent(s.second(), groupId);
        }
        return null;
    }

    public static EditorWorkspace fromLegacyTabs(List<RequestTreeNode> tabs, RequestTreeNode activeNode) {
        TabGroupNode group = new TabGroupNode(TabGroupNode.newGroupId());
        if (tabs != null) {
            for (RequestTreeNode tab : tabs) {
                group.tabs().add(tab);
            }
        }
        if (activeNode != null) {
            int idx = group.indexOf(activeNode);
            group.setSelectedIndex(idx >= 0 ? idx : group.tabs().isEmpty() ? -1 : 0);
        } else if (!group.tabs().isEmpty()) {
            group.setSelectedIndex(0);
        }
        return new EditorWorkspace(group, group.id());
    }

    public record SplitResult(SplitNode split, String originalGroupId, String newGroupId) {}

    private record ParentRef(SplitNode split, boolean isFirstChild) {}
}
