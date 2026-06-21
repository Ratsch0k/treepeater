package treepeater.workspace;

public final class SplitNode implements WorkspaceNode {
    private SplitOrientation orientation;
    private double dividerProportion;
    private WorkspaceNode first;
    private WorkspaceNode second;

    public SplitNode(SplitOrientation orientation, double dividerProportion, WorkspaceNode first, WorkspaceNode second) {
        this.orientation = orientation != null ? orientation : SplitOrientation.HORIZONTAL;
        this.dividerProportion = clampProportion(dividerProportion);
        this.first = first;
        this.second = second;
    }

    public SplitOrientation orientation() {
        return this.orientation;
    }

    public void setOrientation(SplitOrientation orientation) {
        this.orientation = orientation != null ? orientation : SplitOrientation.HORIZONTAL;
    }

    public double dividerProportion() {
        return this.dividerProportion;
    }

    public void setDividerProportion(double dividerProportion) {
        this.dividerProportion = clampProportion(dividerProportion);
    }

    public WorkspaceNode first() {
        return this.first;
    }

    public void setFirst(WorkspaceNode first) {
        this.first = first;
    }

    public WorkspaceNode second() {
        return this.second;
    }

    public void setSecond(WorkspaceNode second) {
        this.second = second;
    }

    public SplitNode copy() {
        return new SplitNode(
                this.orientation,
                this.dividerProportion,
                copyNode(this.first),
                copyNode(this.second));
    }

    private static WorkspaceNode copyNode(WorkspaceNode node) {
        if (node instanceof TabGroupNode g) {
            return g.copy();
        }
        if (node instanceof SplitNode s) {
            return s.copy();
        }
        return node;
    }

    private static double clampProportion(double proportion) {
        if (Double.isNaN(proportion) || Double.isInfinite(proportion)) {
            return 0.5;
        }
        return Math.max(0.05, Math.min(0.95, proportion));
    }
}
