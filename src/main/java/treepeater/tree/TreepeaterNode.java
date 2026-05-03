package treepeater.tree;

import java.util.HashSet;

import javax.swing.tree.DefaultMutableTreeNode;

import treepeater.Treepeater;
import treepeater.requestResponse.Status;
import treepeater.settings.StatusRegistry;

public abstract class TreepeaterNode extends DefaultMutableTreeNode {
    private final int id;
    private Status status;
    private String name;
    private HashSet<TreepeaterNodeListener> listener;

    protected TreepeaterNode(int id, Status status, String name) {
        super(name);
        this.id = id;
        this.status = status != null ? status : StatusRegistry.getDefault();
        this.name = name != null ? name : "#" + id;
        this.listener = new HashSet<>();
    }

    protected TreepeaterNode(int id, Status status, String name, HashSet<TreepeaterNodeListener> listener) {
        super(name);
        this.id = id;
        this.status = status != null ? status : StatusRegistry.getDefault();
        this.name = name != null ? name : "#" + id;
        this.listener = listener;
    }

    public int getId() {
        return this.id;
    }

    public Status getStatus() {
        return this.status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
        this.listener.forEach(l -> l.onNameChange(name));
        Treepeater.saveState();
    }

    public void select() {
        this.listener.forEach(l -> l.onSelect(this));
    }

    public void delete() {
        this.listener.forEach(l -> l.onDelete(this));
    }

    public void addListener(TreepeaterNodeListener l) {
        this.listener.add(l);
    }

    public void removeListener(TreepeaterNodeListener l) {
        this.listener.remove(l);
    }

    public HashSet<TreepeaterNodeListener> getListeners() {
        return this.listener;
    }
}
