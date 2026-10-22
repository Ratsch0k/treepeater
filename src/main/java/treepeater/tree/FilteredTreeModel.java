package treepeater.tree;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

/**
 * Filtering view model around the real {@link DefaultTreeModel}. The {@link javax.swing.JTree}
 * displays this wrapper while all mutation code keeps using the source model via
 * {@link RequestTree#getTreeModel()}; nodes are never copied, so node identity (DnD, open tabs,
 * selection, listeners) is preserved.
 */
public class FilteredTreeModel implements TreeModel {
    /**
     * How a node is visible while filtering:
     * <ul>
     *   <li>{@code DIRECT}: the node's own title matches (bold ranges, normal color).</li>
     *   <li>{@code INHERITED}: the node lives inside a matching folder but does not match itself
     *       (whole title muted, no bold).</li>
     *   <li>{@code PATH}: the node is a folder visible only because a descendant matches (normal
     *       color, plain text).</li>
     * </ul>
     */
    public enum Kind {
        DIRECT, INHERITED, PATH
    }

    /** Per-node display state for the renderer. {@code match} is non-null only for {@code DIRECT}. */
    public record NodeMatch(Kind kind, TreeTitleMatcher.Match match) {
    }

    /** How to re-fire a source event when no filter is active. */
    private interface EventDispatch {
        void dispatch(TreeModelListener listener, TreeModelEvent event);
    }

    private final DefaultTreeModel source;
    private final List<TreeModelListener> listeners = new ArrayList<>();
    private final TreeModelListener sourceListener = new TreeModelListener() {
        @Override
        public void treeNodesChanged(TreeModelEvent e) {
            FilteredTreeModel.this.onSourceEvent(e, TreeModelListener::treeNodesChanged);
        }

        @Override
        public void treeNodesInserted(TreeModelEvent e) {
            FilteredTreeModel.this.onSourceEvent(e, TreeModelListener::treeNodesInserted);
        }

        @Override
        public void treeNodesRemoved(TreeModelEvent e) {
            FilteredTreeModel.this.onSourceEvent(e, TreeModelListener::treeNodesRemoved);
        }

        @Override
        public void treeStructureChanged(TreeModelEvent e) {
            FilteredTreeModel.this.onSourceEvent(e, TreeModelListener::treeStructureChanged);
        }
    };

    /**
     * Invoked after the wrapper fired a structure change caused by a query or source update, so
     * {@link RequestTree} can re-expand paths to matches and restore its selection.
     */
    private Runnable structureChangedCallback;

    /**
     * Invoked right before the wrapper fires a structure change while filtering. The JTree
     * clears both its selection and its expansion state when it receives a root structure
     * change, so {@link RequestTree} captures them here and restores what is still visible
     * in the after-callback.
     */
    private Runnable beforeStructureChangedCallback;

    private TreeTitleMatcher.Query query = TreeTitleMatcher.parse("");

    /** Visible nodes and their display kind; identity-based, non-null only while filtering. */
    private Map<TreeNode, NodeMatch> nodeMatches;
    /** Effective ranking tier per visible node; identity-based. */
    private Map<TreeNode, TreeTitleMatcher.Tier> effectiveTiers;
    /** Visible children per visible folder (and the root), stable-sorted by effective tier. */
    private Map<TreeNode, List<TreeNode>> visibleChildren;

    public FilteredTreeModel(DefaultTreeModel source) {
        this.source = source;
        this.source.addTreeModelListener(this.sourceListener);
    }

    /** The wrapped model; the single source of truth for the tree contents. */
    public DefaultTreeModel getSource() {
        return this.source;
    }

    /** @see #structureChangedCallback */
    public void setStructureChangedCallback(Runnable callback) {
        this.structureChangedCallback = callback;
    }

    /** @see #beforeStructureChangedCallback */
    public void setBeforeStructureChangedCallback(Runnable callback) {
        this.beforeStructureChangedCallback = callback;
    }

    public boolean isFiltering() {
        return !this.query.isEmpty();
    }

    public TreeTitleMatcher.Query getQuery() {
        return this.query;
    }

    /**
     * Sets the search query, recomputes visibility and fires a single structure change for the
     * root, followed by the {@link #setStructureChangedCallback callback}.
     */
    public void setQuery(TreeTitleMatcher.Query query) {
        TreeTitleMatcher.Query next = query != null ? query : TreeTitleMatcher.parse("");
        if (next.equals(this.query)) {
            return;
        }
        this.query = next;
        this.recompute();
        this.fireFilterStructureChanged();
    }

    /** Display state of {@code node} while filtering; {@code null} when not visible or not filtering. */
    public NodeMatch getNodeMatch(TreeNode node) {
        return this.nodeMatches != null ? this.nodeMatches.get(node) : null;
    }

    /** All nodes whose own title matches the query, in source (pre-order) order. */
    public List<TreeNode> getDirectMatchNodes() {
        List<TreeNode> direct = new ArrayList<>();
        if (this.nodeMatches != null) {
            collectDirectMatches((TreeNode) this.source.getRoot(), direct);
        }
        return direct;
    }

    private void collectDirectMatches(TreeNode node, List<TreeNode> out) {
        NodeMatch match = this.nodeMatches.get(node);
        if (match != null && match.kind() == Kind.DIRECT) {
            out.add(node);
        }
        for (int i = 0; i < this.source.getChildCount(node); i++) {
            collectDirectMatches((TreeNode) this.source.getChild(node, i), out);
        }
    }

    @Override
    public Object getRoot() {
        return this.source.getRoot();
    }

    @Override
    public Object getChild(Object parent, int index) {
        if (this.isFiltering()) {
            List<TreeNode> children = this.visibleChildren.get(parent);
            if (children == null || index < 0 || index >= children.size()) {
                return null;
            }
            return children.get(index);
        }
        return this.source.getChild(parent, index);
    }

    @Override
    public int getChildCount(Object parent) {
        if (this.isFiltering()) {
            List<TreeNode> children = this.visibleChildren.get(parent);
            return children != null ? children.size() : 0;
        }
        return this.source.getChildCount(parent);
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        if (this.isFiltering()) {
            List<TreeNode> children = this.visibleChildren.get(parent);
            return children != null ? children.indexOf(child) : -1;
        }
        return this.source.getIndexOfChild(parent, child);
    }

    @Override
    public boolean isLeaf(Object node) {
        if (this.isFiltering()) {
            return this.getChildCount(node) == 0;
        }
        return this.source.isLeaf(node);
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
        this.source.valueForPathChanged(path, newValue);
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
        this.listeners.add(l);
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
        this.listeners.remove(l);
    }

    private void onSourceEvent(TreeModelEvent sourceEvent, EventDispatch dispatch) {
        if (!this.isFiltering()) {
            // Re-fire unchanged (with this wrapper as the source) so the JTree, which listens on
            // the wrapper, stays in sync with source mutations.
            TreeModelEvent forwarded =
                    new TreeModelEvent(this, sourceEvent.getPath(), sourceEvent.getChildIndices(),
                            sourceEvent.getChildren());
            for (TreeModelListener listener : new ArrayList<>(this.listeners)) {
                dispatch.dispatch(listener, forwarded);
            }
            return;
        }
        this.recompute();
        this.fireFilterStructureChanged();
    }

    private void fireFilterStructureChanged() {
        if (this.beforeStructureChangedCallback != null) {
            this.beforeStructureChangedCallback.run();
        }
        TreeModelEvent event = new TreeModelEvent(this, new Object[] { this.getRoot() });
        for (TreeModelListener listener : new ArrayList<>(this.listeners)) {
            listener.treeStructureChanged(event);
        }
        if (this.structureChangedCallback != null) {
            this.structureChangedCallback.run();
        }
    }

    /**
     * Recomputes visibility in a single post-order pass: own matches, {@link Kind} assignment
     * (with {@code DIRECT} taking precedence inside matching folders), effective tiers (best of
     * own match, visible descendants and the nearest matching ancestor folder) and
     * tier-stable-sorted visible child lists.
     */
    private void recompute() {
        this.nodeMatches = null;
        this.effectiveTiers = null;
        this.visibleChildren = null;
        if (!this.isFiltering()) {
            return;
        }

        TreeNode root = (TreeNode) this.getRoot();
        if (root == null) {
            return;
        }

        this.nodeMatches = new IdentityHashMap<>();
        this.effectiveTiers = new IdentityHashMap<>();
        this.visibleChildren = new IdentityHashMap<>();
        this.computeNode(root, false, null);

        // The root is always visible, even without its own match.
        this.nodeMatches.putIfAbsent(root, new NodeMatch(Kind.PATH, null));
        this.effectiveTiers.putIfAbsent(root, null);
        this.buildVisibleChildren(root);
    }

    /**
     * Post-order pass computing the display kind, match and effective tier for {@code node}.
     *
     * @param hasMatchingAncestor a folder on the path to this node (exclusive) matched the query
     * @param matchingAncestorTier own match tier of the nearest matching ancestor folder
     * @return the node's kind, or {@code null} when it is not visible
     */
    private Kind computeNode(TreeNode node, boolean hasMatchingAncestor,
            TreeTitleMatcher.Tier matchingAncestorTier) {
        String title = node instanceof TreepeaterNode tp ? tp.getName() : node.toString();
        TreeTitleMatcher.Match own = this.query.match(title == null ? "" : title).orElse(null);

        Kind kind = null;
        if (own != null) {
            kind = Kind.DIRECT;
        } else if (hasMatchingAncestor) {
            kind = Kind.INHERITED;
        }

        TreeTitleMatcher.Tier best = bestTier(own != null ? own.tier() : null, matchingAncestorTier);

        boolean anyVisibleChild = false;
        boolean childrenMatchAncestor = hasMatchingAncestor || own != null;
        TreeTitleMatcher.Tier childrenAncestorTier = own != null ? own.tier() : matchingAncestorTier;
        for (int i = 0; i < this.source.getChildCount(node); i++) {
            TreeNode child = (TreeNode) this.source.getChild(node, i);
            Kind childKind = this.computeNode(child, childrenMatchAncestor, childrenAncestorTier);
            if (childKind != null) {
                anyVisibleChild = true;
                best = bestTier(best, this.effectiveTiers.get(child));
            }
        }

        if (kind == null && anyVisibleChild) {
            kind = Kind.PATH;
        }
        if (kind != null) {
            this.nodeMatches.put(node, new NodeMatch(kind, own));
            this.effectiveTiers.put(node, best);
        }
        return kind;
    }

    private static TreeTitleMatcher.Tier bestTier(TreeTitleMatcher.Tier a, TreeTitleMatcher.Tier b) {
        if (a == null) {
            return b;
        }
        if (b == null || a.ordinal() < b.ordinal()) {
            return a;
        }
        return b;
    }

    /**
     * Builds the visible child list for every visible folder: source children that survived
     * filtering, stable-sorted by effective tier (view-only; the source order is untouched).
     */
    private void buildVisibleChildren(TreeNode node) {
        if (!node.getAllowsChildren()) {
            return;
        }
        List<TreeNode> visible = new ArrayList<>();
        for (int i = 0; i < this.source.getChildCount(node); i++) {
            TreeNode child = (TreeNode) this.source.getChild(node, i);
            if (this.nodeMatches.containsKey(child)) {
                visible.add(child);
            }
        }
        visible.sort((a, b) -> Integer.compare(this.tierOrdinal(a), this.tierOrdinal(b)));
        this.visibleChildren.put(node, visible);
        for (TreeNode child : visible) {
            this.buildVisibleChildren(child);
        }
    }

    private int tierOrdinal(TreeNode node) {
        TreeTitleMatcher.Tier tier = this.effectiveTiers.get(node);
        return tier != null ? tier.ordinal() : Integer.MAX_VALUE;
    }
}
