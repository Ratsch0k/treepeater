package treepeater.tree;

public interface TreepeaterNodeListener {
    void onSelect(TreepeaterNode node);

    void onNameChange(String newName);

    void onDelete(TreepeaterNode node);
}
