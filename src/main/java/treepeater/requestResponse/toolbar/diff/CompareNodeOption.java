package treepeater.requestResponse.toolbar.diff;

import treepeater.Utilities;
import treepeater.tree.RequestTreeNode;

public record CompareNodeOption(int requestNodeId, String pathLabel) {

    public CompareNodeOption {
        pathLabel = pathLabel != null ? pathLabel : "";
    }

    public static CompareNodeOption from(RequestTreeNode node) {
        return new CompareNodeOption(node.getId(), Utilities.slashPathForNode(node));
    }

    @Override
    public String toString() {
        if (this.pathLabel.isEmpty()) {
            return "#" + this.requestNodeId;
        }
        return this.pathLabel;
    }
}
