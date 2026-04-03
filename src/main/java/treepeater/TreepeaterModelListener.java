package treepeater;
import treepeater.tree.RequestTreeNode;

public interface TreepeaterModelListener {
    void onOpenTab(RequestTreeNode node);
    void onNewTab(RequestTreeNode node);
    void onCloseTab(RequestTreeNode node);
}
