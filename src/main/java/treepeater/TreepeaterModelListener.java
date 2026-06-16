package treepeater;
import treepeater.tree.RequestTreeNode;

public interface TreepeaterModelListener {
    default void onOpenTab(RequestTreeNode node) {};
    default void onNewTab(RequestTreeNode node) {};
    default void onCloseTab(RequestTreeNode node) {};
    default void onTreeChanged() {}
}
