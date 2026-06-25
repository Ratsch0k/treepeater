package treepeater.workspace;

public enum SplitOrientation {
    HORIZONTAL,
    VERTICAL;

    public int swingOrientation() {
        return this == HORIZONTAL
                ? javax.swing.JSplitPane.HORIZONTAL_SPLIT
                : javax.swing.JSplitPane.VERTICAL_SPLIT;
    }
}
