package treepeater.importing;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import treepeater.importing.ImportOptions.DirectNameMode;
import treepeater.importing.ImportOptions.DirectPlacement;

/**
 * A panel for selecting options for direct imports in Treepeater's manual import dialog.
 * Allows the user to choose the node naming mode (URL, path, ID, or manual) and enter a manual name if needed.
 */
final class DirectImportOptionsPanel extends JPanel {

    private final JRadioButton urlButton;
    private final JRadioButton pathButton;
    private final JRadioButton idButton;
    private final JRadioButton manualButton;
    private final JTextField manualNameField;

    DirectImportOptionsPanel(DirectPlacement defaults) {
        super(new GridBagLayout());
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        this.urlButton = new JRadioButton("URL");
        this.pathButton = new JRadioButton("Path");
        this.idButton = new JRadioButton("ID");
        this.manualButton = new JRadioButton("Manual");
        this.urlButton.setOpaque(false);
        this.pathButton.setOpaque(false);
        this.idButton.setOpaque(false);
        this.manualButton.setOpaque(false);

        ButtonGroup nameGroup = new ButtonGroup();
        nameGroup.add(this.urlButton);
        nameGroup.add(this.pathButton);
        nameGroup.add(this.idButton);
        nameGroup.add(this.manualButton);
        this.idButton.setSelected(defaults.nameMode() == DirectNameMode.ID);
        this.pathButton.setSelected(defaults.nameMode() == DirectNameMode.PATH);
        this.urlButton.setSelected(defaults.nameMode() == DirectNameMode.URL);
        this.manualButton.setSelected(defaults.nameMode() == DirectNameMode.MANUAL);

        this.manualNameField = new JTextField(defaults.manualName(), 24);
        this.manualNameField.setEnabled(this.manualButton.isSelected());

        Runnable updateManualField = () -> this.manualNameField.setEnabled(this.manualButton.isSelected());
        this.urlButton.addActionListener(e -> updateManualField.run());
        this.pathButton.addActionListener(e -> updateManualField.run());
        this.idButton.addActionListener(e -> updateManualField.run());
        this.manualButton.addActionListener(e -> updateManualField.run());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, 4, 0);
        add(new JLabel("Node name:"), gbc);

        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 2, 0);
        add(this.urlButton, gbc);

        gbc.gridy++;
        add(this.pathButton, gbc);

        gbc.gridy++;
        add(this.idButton, gbc);

        gbc.gridy++;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        add(this.manualButton, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 8, 2, 0);
        add(this.manualNameField, gbc);
    }

    DirectPlacement buildPlacement(Component dialogParent) {
        DirectNameMode nameMode = selectedNameMode();
        String manualName = this.manualNameField.getText();
        if (nameMode == DirectNameMode.MANUAL && (manualName == null || manualName.isBlank())) {
            JOptionPane.showMessageDialog(dialogParent, "Enter a name for manual naming.", "Manual import",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return new DirectPlacement(nameMode, manualName != null ? manualName : "");
    }

    DirectPlacement currentPlacement() {
        return new DirectPlacement(selectedNameMode(), this.manualNameField.getText());
    }

    private DirectNameMode selectedNameMode() {
        if (this.urlButton.isSelected()) {
            return DirectNameMode.URL;
        }
        if (this.pathButton.isSelected()) {
            return DirectNameMode.PATH;
        }
        if (this.idButton.isSelected()) {
            return DirectNameMode.ID;
        }
        return DirectNameMode.MANUAL;
    }
}
