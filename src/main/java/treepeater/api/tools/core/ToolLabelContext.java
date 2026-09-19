package treepeater.api.tools.core;

/** History index and UI tab id for human-readable chat tool labels. */
public record ToolLabelContext(int viewerHistoryIndex, int uiSelectedRequestNodeId) {

    public static final ToolLabelContext EMPTY = new ToolLabelContext(Integer.MIN_VALUE, Integer.MIN_VALUE);
}
