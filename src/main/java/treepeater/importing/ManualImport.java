package treepeater.importing;

import java.awt.Component;
import java.util.List;

import burp.api.montoya.http.message.HttpRequestResponse;
import treepeater.TreepeaterModel;

/**
 * Orchestrates manual import: presents {@link ManualImportDialog} and applies results to the model.
 *
 * <p>Each pending request is processed on the EDT; {@link ManualImportDialog} is modal and blocks
 * until the user confirms or cancels. When the user selects "Apply to all", the current request and
 * all remaining requests in the batch are imported with the same folder and options.
 */
public final class ManualImport {

    private ManualImport() {
    }

    /**
     * Presents the manual import dialog for each pending request (or once with apply-to-all)
     * and imports confirmed selections into the tree.
     */
    public static void run(
            Component parent,
            TreepeaterModel model,
            List<HttpRequestResponse> requests) {
        run(parent, model, requests, ManualImportDialog::show);
    }

    static void run(
            Component parent,
            TreepeaterModel model,
            List<HttpRequestResponse> requests,
            ManualImportDialogPresenter presenter) {
        if (requests == null || requests.isEmpty()) {
            return;
        }

        ManualImportDialogState previousState = null;
        for (int i = 0; i < requests.size(); i++) {
            ManualImportResult result = presenter.show(
                    parent, model, requests.get(i), i + 1, requests.size(), previousState);
            if (result.wasCancelled()) {
                return;
            }
            if (result.applyToAll()) {
                for (int j = i; j < requests.size(); j++) {
                    model.importRequestManual(result.folder(), requests.get(j), result.options());
                }
                return;
            }
            model.importRequestManual(result.folder(), requests.get(i), result.options());
            previousState = new ManualImportDialogState(
                    result.folder(),
                    result.options(),
                    result.rememberedDirect(),
                    result.rememberedPathAware());
        }
    }
}
