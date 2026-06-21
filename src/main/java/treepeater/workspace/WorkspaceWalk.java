package treepeater.workspace;

final class WorkspaceWalk {
    private WorkspaceWalk() {}

    static <T> T walk(WorkspaceNode node, WorkspaceVisitor<T> visitor) {
        if (node instanceof TabGroupNode g) {
            return visitor.visitGroup(g);
        }
        if (node instanceof SplitNode s) {
            return visitor.visitSplit(s);
        }
        throw new IllegalStateException("Unknown workspace node type");
    }
}
