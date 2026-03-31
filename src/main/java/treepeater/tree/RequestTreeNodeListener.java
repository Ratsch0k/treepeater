package treepeater.tree;

public interface RequestTreeNodeListener {
    void onSelect(RequestTreeNode node);

    void onNameChange(String newName);
}
