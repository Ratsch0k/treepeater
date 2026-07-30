package treepeater.importing;

import treepeater.Treepeater;
import treepeater.requestResponse.Status;
import treepeater.settings.StatusRegistry;
import treepeater.settings.TreepeaterSettings;

/**
 * Per-import configuration for placing a request in the tree. Used by the manual import dialog
 * (dialog-local values) and by automatic path-aware import via {@link #fromSettings()}.
 */
public record ImportOptions(String statusId, Placement placement) {

    public enum DirectNameMode {
        URL,
        ID,
        MANUAL
    }

    /** How a request is placed under its destination folder. */
    public sealed interface Placement permits DirectPlacement, PathAwarePlacement {
    }

    /** Places the request as a single leaf under the chosen folder. */
    public record DirectPlacement(DirectNameMode nameMode, String manualName) implements Placement {
    }

    /** Builds folders from the request path under the chosen folder. */
    public record PathAwarePlacement(
            String leafMode,
            String baseLeafName,
            boolean lenientGroupingEnabled,
            int lenientGroupingMaxSkip,
            int lenientGroupingMatchThresholdPercent,
            boolean normalizeDynamicSegmentsEnabled) implements Placement {

        public boolean isMethodFolderMode() {
            return TreepeaterSettings.IMPORT_LEAF_MODE_METHOD_FOLDER.equals(this.leafMode);
        }
    }

    /** Builds path-aware defaults from the current global settings. */
    public static ImportOptions fromSettings() {
        TreepeaterSettings settings = TreepeaterSettings.getInstance();
        return new ImportOptions(
                StatusRegistry.getDefault().getId(),
                new PathAwarePlacement(
                        settings.getImportLeafMode(),
                        settings.getImportBaseLeafName(),
                        settings.isImportGroupingFolderReconciliationEnabled(),
                        settings.getImportGroupingFolderReconciliationMaxSkip(),
                        settings.getImportGroupingFolderReconciliationMatchThresholdPercent(),
                        settings.isImportNormalizeDynamicSegmentsEnabled()));
    }

    /** Resolves the configured status from the live registry, falling back to the default. */
    public Status resolveStatus() {
        Status status = Treepeater.getStatusRegistry().getById(this.statusId);
        return status != null ? status : StatusRegistry.getDefault();
    }

    public boolean isPathAware() {
        return this.placement instanceof PathAwarePlacement;
    }

    public PathAwarePlacement pathAwarePlacement() {
        if (this.placement instanceof PathAwarePlacement pathAware) {
            return pathAware;
        }
        throw new IllegalStateException("Not a path-aware import: " + this.placement);
    }

    public DirectPlacement directPlacement() {
        if (this.placement instanceof DirectPlacement direct) {
            return direct;
        }
        throw new IllegalStateException("Not a direct import: " + this.placement);
    }
}
