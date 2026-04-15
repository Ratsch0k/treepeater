package treepeater.tree;

import treepeater.requestResponse.Status;

public class FolderTreeNode extends TreepeaterNode {

    public FolderTreeNode(int id, Status status, String name) {
        super(id, status, name);
    }

    public FolderTreeNode(FolderTreeNode copy) {
        super(copy.getId(), copy.getStatus(), copy.getName(), copy.getListeners());
    }

    @Override
    public boolean getAllowsChildren() {
        return true;
    }
}
