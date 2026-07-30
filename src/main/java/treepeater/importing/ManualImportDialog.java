package treepeater.importing;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;

import burp.api.montoya.http.message.HttpRequestResponse;
import treepeater.Treepeater;
import treepeater.TreepeaterModel;
import treepeater.components.CustomButton;
import treepeater.components.StatusComboBox;
import treepeater.components.StatusComboBoxRenderer;
import treepeater.components.StatusComboBoxUi;
import treepeater.importing.ImportOptions.DirectPlacement;
import treepeater.importing.ImportOptions.PathAwarePlacement;
import treepeater.requestResponse.RequestDescriptions;
import treepeater.settings.TreepeaterSettings;
import treepeater.requestResponse.Status;
import treepeater.settings.StatusRegistry;
import treepeater.tree.FolderTreeNode;

/** Modal dialog for manually choosing a destination folder and import options. */
final class ManualImportDialog {

    private static final String CARD_DIRECT = "direct";
    private static final String CARD_PATH_AWARE = "pathAware";

    private ManualImportDialog() {
    }

    /**
     * Opens the manual send dialog for a single request. Blocks until the user closes it.
     */
    static ManualImportResult show(
            Component parent,
            TreepeaterModel model,
            HttpRequestResponse request,
            int index,
            int totalCount,
            ManualImportDialogState previousState) {
        Window owner = parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent);

        JDialog dialog = new JDialog(owner, "Send to Treepeater (manual)", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setMinimumSize(new Dimension(480, 480));
        dialog.setSize(580, 640);
        dialog.setResizable(true);

        ImportOptions initialOptions = previousState != null
                ? previousState.options()
                : ImportOptions.fromSettings();
        DirectPlacement initialDirect = previousState != null
                ? previousState.rememberedDirect()
                : initialDirectPlacement(initialOptions);
        PathAwarePlacement initialPathAware = previousState != null
                ? previousState.rememberedPathAware()
                : initialPathAwarePlacement(initialOptions);
        FolderTreeNode initialFolder = previousState != null ? previousState.folder() : null;
        ManualImportFolderTree folderTree = new ManualImportFolderTree(model, initialFolder);

        JLabel requestContextLabel = createRequestContextLabel(request, index, totalCount);

        JComboBox<Status> statusCombo = createStatusComboBox(initialOptions.statusId());

        JRadioButton directModeButton = new JRadioButton("Direct");
        JRadioButton pathAwareModeButton = new JRadioButton("Path-aware");
        boolean pathAwareMode = initialOptions.isPathAware();
        pathAwareModeButton.setSelected(pathAwareMode);
        directModeButton.setSelected(!pathAwareMode);
        directModeButton.setOpaque(false);
        pathAwareModeButton.setOpaque(false);
        ButtonGroup placementGroup = new ButtonGroup();
        placementGroup.add(directModeButton);
        placementGroup.add(pathAwareModeButton);

        DirectImportOptionsPanel directPanel = new DirectImportOptionsPanel(initialDirect);
        PathAwareImportOptionsPanel pathAwarePanel = new PathAwareImportOptionsPanel(initialPathAware);
        JPanel modeCards = new JPanel(new CardLayout());
        modeCards.setAlignmentX(Component.LEFT_ALIGNMENT);
        modeCards.add(directPanel, CARD_DIRECT);
        modeCards.add(pathAwarePanel, CARD_PATH_AWARE);

        Runnable updatePlacementCard = () -> {
            CardLayout layout = (CardLayout) modeCards.getLayout();
            layout.show(modeCards, directModeButton.isSelected() ? CARD_DIRECT : CARD_PATH_AWARE);
        };
        directModeButton.addActionListener(e -> updatePlacementCard.run());
        pathAwareModeButton.addActionListener(e -> updatePlacementCard.run());
        updatePlacementCard.run();

        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        statusRow.setOpaque(false);
        statusRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusRow.add(new JLabel("Status:"));
        statusRow.add(statusCombo);

        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        modeRow.setOpaque(false);
        modeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        modeRow.add(new JLabel("Import mode:"));
        modeRow.add(directModeButton);
        modeRow.add(pathAwareModeButton);

        JCheckBox applyToAllCheck = new JCheckBox("Apply to all");
        applyToAllCheck.setOpaque(false);
        applyToAllCheck.setVisible(totalCount > 1);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttonPanel.add(applyToAllCheck);
        JButton cancelButton = new JButton("Cancel");
        CustomButton okButton = new CustomButton("OK");
        okButton.setBackground(UIManager.getColor("Button.primary.background"));
        okButton.setForeground(UIManager.getColor("Button.primary.foreground"));
        okButton.setHoverBackground(UIManager.getColor("Button.primary.hoverBackground"));
        buttonPanel.add(cancelButton);
        buttonPanel.add(okButton);

        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new BoxLayout(optionsPanel, BoxLayout.Y_AXIS));
        optionsPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        optionsPanel.add(statusRow);
        optionsPanel.add(Box.createVerticalStrut(6));
        optionsPanel.add(modeRow);
        optionsPanel.add(Box.createVerticalStrut(6));
        optionsPanel.add(modeCards);

        JPanel mainBody = new JPanel(new BorderLayout(0, 0));
        mainBody.add(folderTree.getPanel(), BorderLayout.NORTH);
        mainBody.add(optionsPanel, BorderLayout.CENTER);

        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        content.add(requestContextLabel, BorderLayout.NORTH);
        content.add(mainBody, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setLayout(new BorderLayout());
        dialog.add(content, BorderLayout.CENTER);
        dialog.add(south, BorderLayout.SOUTH);
        dialog.setLocationRelativeTo(owner);

        final ManualImportResult[] resultHolder = new ManualImportResult[1];

        cancelButton.addActionListener(e -> {
            resultHolder[0] = ManualImportResult.cancelled();
            dialog.dispose();
        });

        okButton.addActionListener(e -> {
            FolderTreeNode folder = folderTree.getSelectedFolder();
            if (folder == null) {
                JOptionPane.showMessageDialog(dialog, "Select a destination folder.", "Manual import",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            String statusId = selectedStatusId(statusCombo);
            DirectPlacement rememberedDirect = directPanel.currentPlacement();
            PathAwarePlacement rememberedPathAware = pathAwarePanel.currentPlacement();
            ImportOptions options;
            if (directModeButton.isSelected()) {
                DirectPlacement direct = directPanel.buildPlacement(dialog);
                if (direct == null) {
                    return;
                }
                options = new ImportOptions(statusId, direct);
            } else {
                options = new ImportOptions(statusId, pathAwarePanel.buildPlacement());
            }

            resultHolder[0] = ManualImportResult.confirmed(
                    folder, options, rememberedDirect, rememberedPathAware, applyToAllCheck.isSelected());
            dialog.dispose();
        });

        dialog.setVisible(true);

        if (resultHolder[0] == null) {
            return ManualImportResult.cancelled();
        }
        return resultHolder[0];
    }

    private static DirectPlacement initialDirectPlacement(ImportOptions options) {
        if (options.placement() instanceof DirectPlacement direct) {
            return direct;
        }
        return new DirectPlacement(TreepeaterSettings.getInstance().getDirectImportNameMode(), "");
    }

    private static PathAwarePlacement initialPathAwarePlacement(ImportOptions options) {
        if (options.placement() instanceof PathAwarePlacement pathAware) {
            return pathAware;
        }
        return (PathAwarePlacement) ImportOptions.fromSettings().placement();
    }

    private static JComboBox<Status> createStatusComboBox(String initialStatusId) {
        JComboBox<Status> box = new StatusComboBox();
        box.setRenderer(new StatusComboBoxRenderer(true));
        StatusComboBoxUi.install(box);
        refreshStatusComboModel(box);
        selectStatusById(box, initialStatusId);
        return box;
    }

    private static void refreshStatusComboModel(JComboBox<Status> box) {
        List<Status> statuses = Treepeater.getStatusRegistry().getAll();
        box.setModel(new DefaultComboBoxModel<>(statuses.toArray(new Status[0])));
    }

    private static void selectStatusById(JComboBox<Status> box, String statusId) {
        Status status = Treepeater.getStatusRegistry().getById(statusId);
        if (status != null) {
            box.setSelectedItem(status);
        } else {
            box.setSelectedItem(StatusRegistry.getDefault());
        }
    }

    private static String selectedStatusId(JComboBox<Status> box) {
        Status selected = (Status) box.getSelectedItem();
        return selected != null ? selected.getId() : StatusRegistry.getDefault().getId();
    }

    private static JLabel createRequestContextLabel(
            HttpRequestResponse requestResponse, int index, int totalCount) {
        String method = RequestDescriptions.method(requestResponse);
        String url = RequestDescriptions.url(requestResponse);
        String urlPrefix = method + " ";
        String batchPrefix = totalCount > 1 ? "Request " + index + " of " + totalCount + ": " : "";
        String linePrefix = batchPrefix + urlPrefix;

        JLabel label = new JLabel();
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setToolTipText(method + " " + url);
        label.setFont(label.getFont().deriveFont(Font.ITALIC));

        label.setText(linePrefix + url);
        return label;
    }
}
