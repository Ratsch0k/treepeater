package treepeater;
import treepeater.tree.RequestTreeNode;

public interface TreepeaterModelListener {
    void onOpenTab(RequestTreeNode node);
    void onNewTab(RequestTreeNode node);
    void onCloseTab(RequestTreeNode node);

    /** Tree node reference changed (e.g. after DnD) while the logical tab id stays the same. */
    void onTabNodeReplaced(RequestTreeNode staleNode, RequestTreeNode currentNode);
}
