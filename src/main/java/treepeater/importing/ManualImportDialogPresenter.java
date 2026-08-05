package treepeater.importing;

import java.awt.Component;

import burp.api.montoya.http.message.HttpRequestResponse;
import treepeater.TreepeaterModel;

/** Presents {@link ManualImportDialog} for one request in a batch. */
@FunctionalInterface
interface ManualImportDialogPresenter {

    ManualImportResult show(
            Component parent,
            TreepeaterModel model,
            HttpRequestResponse request,
            int index,
            int totalCount,
            ManualImportDialogState previousState);
}
