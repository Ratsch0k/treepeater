package treepeater.importing;

import treepeater.importing.ImportOptions.DirectPlacement;
import treepeater.importing.ImportOptions.PathAwarePlacement;
import treepeater.tree.FolderTreeNode;

/**
 * Outcome of a single {@link ManualImportDialog} presentation.
 *
 * <p>When {@link #wasCancelled()} is {@code true}, {@link #folder()} and {@link #options()} are
 * {@code null}.
 */
record ManualImportResult(
        FolderTreeNode folder,
        ImportOptions options,
        DirectPlacement rememberedDirect,
        PathAwarePlacement rememberedPathAware,
        boolean applyToAll,
        boolean wasCancelled) {

    static ManualImportResult cancelled() {
        return new ManualImportResult(null, null, null, null, false, true);
    }

    static ManualImportResult confirmed(
            FolderTreeNode folder,
            ImportOptions options,
            DirectPlacement rememberedDirect,
            PathAwarePlacement rememberedPathAware,
            boolean applyToAll) {
        return new ManualImportResult(folder, options, rememberedDirect, rememberedPathAware, applyToAll, false);
    }
}
