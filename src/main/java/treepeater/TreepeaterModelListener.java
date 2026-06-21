package treepeater;

import treepeater.tree.RequestTreeNode;

public interface TreepeaterModelListener {
    default void onOpenTab(RequestTreeNode node, String tabGroupId) {}

    default void onNewTab(RequestTreeNode node, String tabGroupId) {}

    default void onCloseTab(RequestTreeNode node) {}

    default void onTabMoved(RequestTreeNode node, String fromGroupId, String toGroupId, int dropIndex) {}

    default void onWorkspaceLayoutChanged() {}

    default void onFocusedGroupChanged(String tabGroupId) {}

    default void onTreeChanged() {}
}
