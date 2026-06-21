package treepeater.workspace;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import treepeater.tree.RequestTreeNode;

public final class TabGroupNode implements WorkspaceNode {
    private final String id;
    private final LinkedList<RequestTreeNode> tabs;
    private int selectedIndex;

    public TabGroupNode(String id) {
        this.id = id != null && !id.isBlank() ? id : newGroupId();
        this.tabs = new LinkedList<>();
        this.selectedIndex = -1;
    }

    public TabGroupNode(String id, List<RequestTreeNode> tabs, int selectedIndex) {
        this.id = id != null && !id.isBlank() ? id : newGroupId();
        this.tabs = new LinkedList<>(tabs != null ? tabs : List.of());
        this.selectedIndex = selectedIndex;
        clampSelectedIndex();
    }

    public static String newGroupId() {
        return "g-" + UUID.randomUUID();
    }

    public String id() {
        return this.id;
    }

    public LinkedList<RequestTreeNode> tabs() {
        return this.tabs;
    }

    public int selectedIndex() {
        return this.selectedIndex;
    }

    public void setSelectedIndex(int selectedIndex) {
        this.selectedIndex = selectedIndex;
        clampSelectedIndex();
    }

    public RequestTreeNode selectedTab() {
        if (this.selectedIndex < 0 || this.selectedIndex >= this.tabs.size()) {
            return null;
        }
        return this.tabs.get(this.selectedIndex);
    }

    public boolean isEmpty() {
        return this.tabs.isEmpty();
    }

    public int indexOf(RequestTreeNode node) {
        return this.tabs.indexOf(node);
    }

    public boolean contains(RequestTreeNode node) {
        return this.tabs.contains(node);
    }

    public void addTab(RequestTreeNode node) {
        this.tabs.add(node);
        this.selectedIndex = this.tabs.size() - 1;
    }

    public void insertTab(int index, RequestTreeNode node) {
        int idx = Math.max(0, Math.min(index, this.tabs.size()));
        this.tabs.add(idx, node);
        this.selectedIndex = idx;
    }

    public RequestTreeNode removeTab(RequestTreeNode node) {
        int idx = this.tabs.indexOf(node);
        if (idx < 0) {
            return null;
        }
        RequestTreeNode removed = this.tabs.remove(idx);
        if (this.tabs.isEmpty()) {
            this.selectedIndex = -1;
        } else if (this.selectedIndex >= this.tabs.size()) {
            this.selectedIndex = this.tabs.size() - 1;
        } else if (this.selectedIndex > idx) {
            this.selectedIndex--;
        }
        return removed;
    }

    public RequestTreeNode removeTabAt(int index) {
        if (index < 0 || index >= this.tabs.size()) {
            return null;
        }
        RequestTreeNode removed = this.tabs.remove(index);
        if (this.tabs.isEmpty()) {
            this.selectedIndex = -1;
        } else if (this.selectedIndex >= this.tabs.size()) {
            this.selectedIndex = this.tabs.size() - 1;
        } else if (this.selectedIndex > index) {
            this.selectedIndex--;
        }
        return removed;
    }

    public TabGroupNode copy() {
        return new TabGroupNode(this.id, this.tabs, this.selectedIndex);
    }

    private void clampSelectedIndex() {
        if (this.tabs.isEmpty()) {
            this.selectedIndex = -1;
        } else if (this.selectedIndex < 0) {
            this.selectedIndex = 0;
        } else if (this.selectedIndex >= this.tabs.size()) {
            this.selectedIndex = this.tabs.size() - 1;
        }
    }
}
