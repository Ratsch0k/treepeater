package treepeater.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

import java.util.List;

import javax.swing.JPanel;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import burp.api.montoya.http.message.HttpRequestResponse;

import treepeater.ImportTestSupport;
import treepeater.TreepeaterModel;
import treepeater.importing.ImportOptions.DirectNameMode;
import treepeater.importing.ImportOptions.DirectPlacement;
import treepeater.importing.ImportOptions.PathAwarePlacement;
import treepeater.tree.FolderTreeNode;

class ManualImportRunTest extends ImportTestSupport {

    @Test
    void applyToAllImportsRemainingRequestsWithSameOptions() {
        TreepeaterModel model = new TreepeaterModel();
        FolderTreeNode anchor = model.createFolder(root(model));
        anchor.setName("BatchTarget");

        ImportOptions options = new ImportOptions(
                ImportOptions.fromSettings().statusId(),
                new DirectPlacement(DirectNameMode.MANUAL, "Shared"));
        DirectPlacement rememberedDirect = new DirectPlacement(DirectNameMode.MANUAL, "Shared");
        PathAwarePlacement rememberedPathAware = (PathAwarePlacement) ImportOptions.fromSettings().placement();

        ManualImportDialogPresenter presenter = (parent, m, request, index, totalCount, previousState) -> {
            if (index == 1) {
                return ManualImportResult.confirmed(
                        anchor, options, rememberedDirect, rememberedPathAware, true);
            }
            return ManualImportResult.cancelled();
        };

        ManualImport.run(
                new JPanel(),
                model,
                List.of(rr("GET", "/one"), rr("GET", "/two"), rr("GET", "/three")),
                presenter);

        assertEquals(3, anchor.getChildCount());
        assertNotNull(leaf(anchor, "Shared"));
    }

    @Test
    void cancelledImportStopsBatchProcessing() {
        TreepeaterModel model = new TreepeaterModel();
        FolderTreeNode anchor = model.createFolder(root(model));

        ManualImportDialogPresenter presenter = Mockito.mock(ManualImportDialogPresenter.class);
        lenient().when(presenter.show(
                        Mockito.any(),
                        Mockito.eq(model),
                        Mockito.any(HttpRequestResponse.class),
                        Mockito.eq(1),
                        Mockito.eq(2),
                        Mockito.isNull()))
                .thenReturn(ManualImportResult.cancelled());

        ManualImport.run(
                new JPanel(),
                model,
                List.of(rr("GET", "/one"), rr("GET", "/two")),
                presenter);

        assertEquals(0, anchor.getChildCount());
        verify(presenter).show(
                Mockito.any(),
                Mockito.eq(model),
                Mockito.any(HttpRequestResponse.class),
                Mockito.eq(1),
                Mockito.eq(2),
                Mockito.isNull());
    }
}
