package treepeater.importing;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import treepeater.TreepeaterModel;
import treepeater.tree.FolderTreeNode;

/** Folder-only tree picker used by {@link ManualImportDialog}. */
final class ManualImportFolderTree {

    private static final int PREFERRED_HEIGHT = 200;
    private static final int PANEL_PADDING = 6;

    private final JTree tree;
    private final JPanel panel;

    ManualImportFolderTree(TreepeaterModel model, FolderTreeNode initialSelection) {
        DefaultTreeModel treeModel = buildFolderTreeModel(model);
        this.tree = new JTree(treeModel);
        // BasicTreeUI avoids Burp/FlatLaf tree chrome that clashes with the dialog layout.
        this.tree.setUI(new BasicTreeUI());
        this.tree.setRootVisible(true);
        this.tree.setShowsRootHandles(false);
        this.tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setLeafIcon(renderer.getClosedIcon());
        this.tree.setCellRenderer(renderer);
        expandAllRows(this.tree);
        if (initialSelection != null) {
            selectFolder(this.tree, treeModel, initialSelection);
        } else {
            selectRoot(this.tree, treeModel);
        }

        JScrollPane scrollPane = new JScrollPane(this.tree);
        // Extra height accounts for the panel border padding around the scroll pane.
        scrollPane.setPreferredSize(new Dimension(0, PREFERRED_HEIGHT + PANEL_PADDING * 2));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        this.panel = new JPanel(new BorderLayout());
        this.panel.add(scrollPane, BorderLayout.CENTER);
        applyTheme(this.tree, scrollPane, this.panel);
    }

    JPanel getPanel() {
        return this.panel;
    }

    FolderTreeNode getSelectedFolder() {
        TreePath path = this.tree.getSelectionPath();
        if (path != null && path.getLastPathComponent() instanceof DefaultMutableTreeNode node
                && node.getUserObject() instanceof FolderTreeNode folder) {
            return folder;
        }
        return null;
    }

    private static DefaultTreeModel buildFolderTreeModel(TreepeaterModel model) {
        Object rootObject = model.getTree().getTreeModel().getRoot();
        if (!(rootObject instanceof FolderTreeNode liveRoot)) {
            throw new IllegalStateException("Treepeater root is not a folder node");
        }
        // Snapshot into DefaultMutableTreeNodes: live FolderTreeNode instances cannot be shared
        // with a second TreeModel, so we mirror the folder hierarchy and store each live folder
        // as the userObject for selection mapping back to the model.
        return new DefaultTreeModel(buildFolderSnapshot(liveRoot));
    }

    private static DefaultMutableTreeNode buildFolderSnapshot(FolderTreeNode liveFolder) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(liveFolder, true);
        for (int i = 0; i < liveFolder.getChildCount(); i++) {
            TreeNode child = liveFolder.getChildAt(i);
            if (child instanceof FolderTreeNode folder) {
                node.add(buildFolderSnapshot(folder));
            }
        }
        return node;
    }

    private static void selectFolder(JTree tree, DefaultTreeModel treeModel, FolderTreeNode target) {
        Object root = treeModel.getRoot();
        if (!(root instanceof DefaultMutableTreeNode rootNode)) {
            selectRoot(tree, treeModel);
            return;
        }
        DefaultMutableTreeNode match = findFolderNode(rootNode, target);
        if (match != null) {
            tree.setSelectionPath(new TreePath(match.getPath()));
            tree.scrollPathToVisible(new TreePath(match.getPath()));
        } else {
            selectRoot(tree, treeModel);
        }
    }

    private static DefaultMutableTreeNode findFolderNode(DefaultMutableTreeNode node, FolderTreeNode target) {
        if (node.getUserObject() == target) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) node.getChildAt(i);
            DefaultMutableTreeNode match = findFolderNode(child, target);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static void selectRoot(JTree tree, DefaultTreeModel treeModel) {
        Object root = treeModel.getRoot();
        if (root instanceof DefaultMutableTreeNode rootNode) {
            tree.setSelectionPath(new TreePath(rootNode.getPath()));
        }
    }

    private static void expandAllRows(JTree tree) {
        for (int row = 0; row < tree.getRowCount(); row++) {
            tree.expandRow(row);
        }
    }

    /** Applies Burp theme colors manually because the picker is not embedded in a themed tree row. */
    private static void applyTheme(JTree tree, JScrollPane scrollPane, JPanel panel) {
        Color background = UIManager.getColor("Colors.ui.background.1");
        Color borderColor = UIManager.getColor("Colors.ui.background.3");

        panel.setOpaque(true);
        panel.setBackground(background);
        if (borderColor != null) {
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(borderColor),
                    BorderFactory.createEmptyBorder(
                            PANEL_PADDING, PANEL_PADDING, PANEL_PADDING, PANEL_PADDING)));
        } else {
            panel.setBorder(BorderFactory.createEmptyBorder(
                    PANEL_PADDING, PANEL_PADDING, PANEL_PADDING, PANEL_PADDING));
        }

        scrollPane.setOpaque(true);
        scrollPane.setBackground(background);
        scrollPane.getViewport().setBackground(background);

        tree.setOpaque(true);
        tree.setBackground(background);
    }
}
