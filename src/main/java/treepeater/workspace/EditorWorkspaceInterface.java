package treepeater.workspace;

import treepeater.requestResponse.RequestResponsePanel;
import treepeater.tree.RequestTreeNode;

public interface EditorWorkspaceInterface {
    RequestResponsePanel getOrCreatePanel(RequestTreeNode node);

    void onTabClosed(RequestTreeNode node, RequestResponsePanel panel);

    void onTabSelected(TabGroupPanel group, RequestTreeNode node);

    void onGroupFocused(TabGroupPanel group);

    void onSplitRequested(TabGroupPanel group, SplitOrientation orientation);

    void onTabMoved(RequestTreeNode node, String fromGroupId, String toGroupId, int dropIndex);
}
