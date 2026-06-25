package treepeater.workspace;

interface WorkspaceVisitor<T> {
    T visitGroup(TabGroupNode group);

    T visitSplit(SplitNode split);
}
