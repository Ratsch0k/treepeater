package treepeater.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

import org.junit.jupiter.api.Test;

import treepeater.settings.StatusRegistry;
import treepeater.tree.FilteredTreeModel.Kind;
import treepeater.tree.FilteredTreeModel.NodeMatch;
import treepeater.tree.TreeTitleMatcher.Tier;

class FilteredTreeModelTest {
    private final FolderTreeNode root = new FolderTreeNode(0, StatusRegistry.getDefault(), "Root");
    private final DefaultTreeModel source = new DefaultTreeModel(this.root);
    private final FilteredTreeModel filtered = new FilteredTreeModel(this.source);

    private final List<TreeModelEvent> structureEvents = new ArrayList<>();

    FilteredTreeModelTest() {
        this.filtered.addTreeModelListener(new TreeModelListener() {
            @Override
            public void treeNodesChanged(TreeModelEvent e) {
            }

            @Override
            public void treeNodesInserted(TreeModelEvent e) {
            }

            @Override
            public void treeNodesRemoved(TreeModelEvent e) {
            }

            @Override
            public void treeStructureChanged(TreeModelEvent e) {
                FilteredTreeModelTest.this.structureEvents.add(e);
            }
        });
    }

    // -- fixtures --------------------------------------------------------

    /**
     * <pre>
     * root
     * ├── FolderA "Alpha"              (matching folder)
     * │   ├── R1 "Login page"
     * │   └── R2 "Dashboard"
     * ├── FolderZ "zzz"                (path-only folder)
     * │   └── R3 "Alpha login"
     * ├── R4 "Gamma session"           (unrelated)
     * └── R5 "Alpha gamma"
     * </pre>
     */
    private void buildStandardTree() {
        FolderTreeNode folderA = folder(1, "Alpha");
        this.source.insertNodeInto(request(10, "Login page"), folderA, folderA.getChildCount());
        this.source.insertNodeInto(request(11, "Dashboard"), folderA, folderA.getChildCount());
        this.source.insertNodeInto(folderA, this.root, this.root.getChildCount());

        FolderTreeNode folderZ = folder(2, "zzz");
        this.source.insertNodeInto(request(12, "Alpha login"), folderZ, folderZ.getChildCount());
        this.source.insertNodeInto(folderZ, this.root, this.root.getChildCount());

        this.source.insertNodeInto(request(13, "Gamma session"), this.root, this.root.getChildCount());
        this.source.insertNodeInto(request(14, "Alpha gamma"), this.root, this.root.getChildCount());
    }

    private FolderTreeNode folder(int id, String name) {
        return new FolderTreeNode(id, StatusRegistry.getDefault(), name);
    }

    private RequestTreeNode request(int id, String name) {
        return new RequestTreeNode(id, name, null, null);
    }

    private static List<String> childTitles(FilteredTreeModel model, Object parent) {
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < model.getChildCount(parent); i++) {
            titles.add(model.getChild(parent, i).toString());
        }
        return titles;
    }

    // -- tests -----------------------------------------------------------

    @Test
    void ancestorsOfDeepMatchesAreVisibleAndUnrelatedBranchesHidden() {
        this.buildStandardTree();

        this.filtered.setQuery(TreeTitleMatcher.parse("alpha"));

        assertEquals(List.of("Alpha", "zzz", "Alpha gamma"), childTitles(this.filtered, this.root));
        // FolderZ is only a path to the matching request inside it.
        assertEquals(List.of("Alpha login"), childTitles(this.filtered, this.filtered.getChild(this.root, 1)));
    }

    @Test
    void matchingFolderShowsItsWholeSubtree() {
        this.buildStandardTree();

        this.filtered.setQuery(TreeTitleMatcher.parse("alpha"));

        Object folderA = this.filtered.getChild(this.root, 0);
        assertEquals(List.of("Login page", "Dashboard"), childTitles(this.filtered, folderA));

        assertEquals(Kind.DIRECT, this.filtered.getNodeMatch((TreeNode) folderA).kind());
        Object r1 = this.filtered.getChild(folderA, 0);
        assertEquals(Kind.INHERITED, this.filtered.getNodeMatch((TreeNode) r1).kind());
    }

    @Test
    void kindsFollowDirectInheritedPathRules() {
        FolderTreeNode matchingFolder = folder(1, "Alpha");
        this.source.insertNodeInto(request(10, "unrelated"), matchingFolder, matchingFolder.getChildCount());
        FolderTreeNode nonMatchingSubFolder = folder(2, "zzz");
        this.source.insertNodeInto(request(11, "alpha deep"), nonMatchingSubFolder, nonMatchingSubFolder.getChildCount());
        this.source.insertNodeInto(nonMatchingSubFolder, matchingFolder, matchingFolder.getChildCount());
        this.source.insertNodeInto(request(12, "alpha page"), matchingFolder, matchingFolder.getChildCount());
        this.source.insertNodeInto(matchingFolder, this.root, this.root.getChildCount());

        this.filtered.setQuery(TreeTitleMatcher.parse("alpha"));

        assertEquals(Kind.DIRECT, this.filtered.getNodeMatch(matchingFolder).kind());
        assertEquals(Kind.INHERITED, this.filtered.getNodeMatch((TreeNode) this.source.getChild(matchingFolder, 0)).kind());
        // A non-matching folder inside a matching folder is INHERITED...
        assertEquals(Kind.INHERITED, this.filtered.getNodeMatch(nonMatchingSubFolder).kind());
        // ...but its own matching children are still DIRECT.
        assertEquals(Kind.DIRECT, this.filtered.getNodeMatch((TreeNode) this.source.getChild(nonMatchingSubFolder, 0)).kind());
        assertEquals(Kind.DIRECT, this.filtered.getNodeMatch((TreeNode) this.source.getChild(matchingFolder, 2)).kind());
    }

    @Test
    void siblingsAreSortedByTierWithOriginalOrderWithinATier() {
        // Order chosen so sorting must move a fuzzy match behind substring matches.
        this.source.insertNodeInto(request(1, "Current user"), this.root, this.root.getChildCount());
        FolderTreeNode pathFolder = folder(2, "nothing");
        this.source.insertNodeInto(request(3, "audit usr page"), pathFolder, pathFolder.getChildCount());
        this.source.insertNodeInto(pathFolder, this.root, this.root.getChildCount());
        this.source.insertNodeInto(request(4, "audit usr page"), this.root, this.root.getChildCount());
        this.source.insertNodeInto(request(5, "apple banana"), this.root, this.root.getChildCount());

        this.filtered.setQuery(TreeTitleMatcher.parse("usr"));

        // "nothing" is PATH and ranks by its substring descendant; the fuzzy "Current user"
        // moves last; the two "audit usr page" rows keep their original relative order; the
        // unrelated "apple banana" disappears.
        assertEquals(List.of("nothing", "audit usr page", "Current user"), childTitles(this.filtered, this.root));
    }

    @Test
    void queryChangeFiresASingleRootStructureChange() {
        this.buildStandardTree();
        this.structureEvents.clear();

        this.filtered.setQuery(TreeTitleMatcher.parse("alpha"));

        assertEquals(1, this.structureEvents.size());
        assertSame(this.filtered, this.structureEvents.get(0).getSource());
        assertSame(this.root, this.structureEvents.get(0).getPath()[0]);

        // Same query again: no additional events.
        this.filtered.setQuery(TreeTitleMatcher.parse("alpha"));
        assertEquals(1, this.structureEvents.size());
    }

    @Test
    void sourceEventsRecomputeVisibilityWhileFiltering() {
        this.buildStandardTree();
        this.filtered.setQuery(TreeTitleMatcher.parse("alpha"));
        this.structureEvents.clear();

        // Insert a matching request into the unrelated top level.
        this.source.insertNodeInto(request(20, "alpha fresh"), this.root, this.root.getChildCount());
        assertEquals(1, this.structureEvents.size());
        assertTrue(childTitles(this.filtered, this.root).contains("alpha fresh"));

        // Rename an inherited node so it matches on its own.
        FolderTreeNode folderA = (FolderTreeNode) this.source.getChild(this.root, 0);
        RequestTreeNode r1 = (RequestTreeNode) this.source.getChild(folderA, 0);
        r1.setName("alpha login");
        this.source.nodeChanged(r1);
        assertEquals(2, this.structureEvents.size());
        assertEquals(Kind.DIRECT, this.filtered.getNodeMatch(r1).kind());

        // Remove it again; it disappears from the filtered view.
        this.source.removeNodeFromParent(r1);
        assertEquals(3, this.structureEvents.size());
        assertNull(this.filtered.getNodeMatch(r1));
    }

    @Test
    void sourceEventsAreForwardedUnchangedWithoutFilter() {
        this.buildStandardTree();
        List<TreeModelEvent> inserted = new ArrayList<>();
        this.filtered.addTreeModelListener(new TreeModelListener() {
            @Override
            public void treeNodesChanged(TreeModelEvent e) {
            }

            @Override
            public void treeNodesInserted(TreeModelEvent e) {
                inserted.add(e);
            }

            @Override
            public void treeNodesRemoved(TreeModelEvent e) {
            }

            @Override
            public void treeStructureChanged(TreeModelEvent e) {
            }
        });

        this.source.insertNodeInto(request(30, "inserted"), this.root, this.root.getChildCount());

        assertEquals(1, inserted.size());
        assertSame(this.filtered, inserted.get(0).getSource());
        assertSame(this.root, inserted.get(0).getPath()[0]);
        assertEquals(0, this.structureEvents.size());
    }

    @Test
    void clearingTheQueryPassesEverythingThroughUnchanged() {
        this.buildStandardTree();
        this.filtered.setQuery(TreeTitleMatcher.parse("alpha"));
        this.structureEvents.clear();

        this.filtered.setQuery(TreeTitleMatcher.parse(""));

        assertEquals(this.root.getChildCount(), this.filtered.getChildCount(this.root));
        for (int i = 0; i < this.root.getChildCount(); i++) {
            assertSame(this.source.getChild(this.root, i), this.filtered.getChild(this.root, i));
            assertNull(this.filtered.getNodeMatch((TreeNode) this.filtered.getChild(this.root, i)));
        }
        assertTrue(this.filtered.getDirectMatchNodes().isEmpty());
    }

    @Test
    void nodeMatchExposesOwnMatchOnlyForDirectNodes() {
        this.buildStandardTree();
        this.filtered.setQuery(TreeTitleMatcher.parse("alpha"));

        Object alphaFolder = this.filtered.getChild(this.root, 0);
        NodeMatch folderMatch = this.filtered.getNodeMatch((TreeNode) alphaFolder);
        assertEquals(Tier.SUBSTRING, folderMatch.match().tier());

        Object inherited = this.filtered.getChild(alphaFolder, 0);
        assertNull(this.filtered.getNodeMatch((TreeNode) inherited).match());
    }

    @Test
    void isLeafAndIndexOfFollowVisibleChildrenWhileFiltering() {
        this.buildStandardTree();
        this.filtered.setQuery(TreeTitleMatcher.parse("alpha"));

        // The unrelated branch is gone, so the root's visible child count drops.
        assertEquals(3, this.filtered.getChildCount(this.root));

        Object alphaFolder = this.filtered.getChild(this.root, 0);
        assertEquals(0, this.filtered.getIndexOfChild(this.root, alphaFolder));

        // A hidden node is not in any visible child list and behaves like an empty leaf.
        TreeNode gamma = (TreeNode) this.source.getChild(this.root, 2);
        assertEquals(-1, this.filtered.getIndexOfChild(this.root, gamma));
        assertEquals(0, this.filtered.getChildCount(gamma));
        assertTrue(this.filtered.isLeaf(gamma));
        assertNull(this.filtered.getNodeMatch(gamma));
    }
}
