package treepeater.importing;

import treepeater.importing.ImportOptions.DirectPlacement;
import treepeater.importing.ImportOptions.PathAwarePlacement;
import treepeater.tree.FolderTreeNode;

/** Folder and options remembered from the prior manual import dialog in a multi-import batch. */
record ManualImportDialogState(
        FolderTreeNode folder,
        ImportOptions options,
        DirectPlacement rememberedDirect,
        PathAwarePlacement rememberedPathAware) {
}
