package treepeater.importing;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;

import treepeater.importing.ImportOptions.PathAwarePlacement;
import treepeater.settings.TreepeaterSettings;

/**
 * A panel for configuring options for path-aware imports in Treepeater's manual import dialog.
 * Allows the user to select leaf node grouping, base leaf naming, lenient folder grouping, and
 * normalization options.
 */
final class PathAwareImportOptionsPanel extends JPanel {

    private final JRadioButton directLeafButton;
    private final JRadioButton methodLeafButton;
    private final JTextField baseLeafNameField;
    private final JCheckBox lenientCheck;
    private final JSpinner maxSkipSpinner;
    private final JSpinner thresholdSpinner;
    private final JCheckBox normalizeCheck;

    PathAwareImportOptionsPanel(PathAwarePlacement defaults) {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setBorder(BorderFactory.createEmptyBorder(4, 16, 0, 0));

        this.directLeafButton = new JRadioButton(
                "Direct: leaf named after the last path segment");
        this.methodLeafButton = new JRadioButton(
                "Method folders: leaf under a per-method folder (e.g. [GET])");
        this.directLeafButton.setOpaque(false);
        this.methodLeafButton.setOpaque(false);
        ButtonGroup leafGroup = new ButtonGroup();
        leafGroup.add(this.directLeafButton);
        leafGroup.add(this.methodLeafButton);
        boolean methodMode = defaults.isMethodFolderMode();
        this.directLeafButton.setSelected(!methodMode);
        this.methodLeafButton.setSelected(methodMode);

        this.baseLeafNameField = new JTextField(defaults.baseLeafName(), 16);
        this.baseLeafNameField.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, this.baseLeafNameField.getPreferredSize().height));

        JPanel baseNameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        baseNameRow.setOpaque(false);
        baseNameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        baseNameRow.add(new JLabel("Method-folder base leaf name:"));
        baseNameRow.add(this.baseLeafNameField);

        this.lenientCheck = new JCheckBox("Enable lenient folder grouping");
        this.lenientCheck.setOpaque(false);
        this.lenientCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        this.lenientCheck.setSelected(defaults.lenientGroupingEnabled());

        this.maxSkipSpinner = new JSpinner(new SpinnerNumberModel(
                defaults.lenientGroupingMaxSkip(), 1, 10, 1));
        this.thresholdSpinner = new JSpinner(new SpinnerNumberModel(
                defaults.lenientGroupingMatchThresholdPercent(), 0, 100, 5));

        JPanel maxSkipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        maxSkipRow.setOpaque(false);
        maxSkipRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        maxSkipRow.add(new JLabel("Max leading folders to skip:"));
        maxSkipRow.add(this.maxSkipSpinner);

        JPanel thresholdRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        thresholdRow.setOpaque(false);
        thresholdRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        thresholdRow.add(new JLabel("Minimum target path overlap (%):"));
        thresholdRow.add(this.thresholdSpinner);

        Runnable updateLenientControls = () -> {
            boolean enabled = this.lenientCheck.isSelected();
            this.maxSkipSpinner.setEnabled(enabled);
            this.thresholdSpinner.setEnabled(enabled);
        };
        this.lenientCheck.addActionListener(e -> updateLenientControls.run());
        updateLenientControls.run();

        this.normalizeCheck = new JCheckBox("Normalize dynamic path segments");
        this.normalizeCheck.setOpaque(false);
        this.normalizeCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        this.normalizeCheck.setSelected(defaults.normalizeDynamicSegmentsEnabled());

        add(this.directLeafButton);
        add(Box.createVerticalStrut(2));
        add(this.methodLeafButton);
        add(Box.createVerticalStrut(6));
        add(baseNameRow);
        add(Box.createVerticalStrut(8));
        add(createSubsectionHeader("Lenient Folder Grouping"));
        add(Box.createVerticalStrut(4));
        add(this.lenientCheck);
        add(Box.createVerticalStrut(4));
        add(maxSkipRow);
        add(Box.createVerticalStrut(4));
        add(thresholdRow);
        add(Box.createVerticalStrut(8));
        add(createSubsectionHeader("Dynamic Path Segments"));
        add(Box.createVerticalStrut(4));
        add(this.normalizeCheck);
    }

    PathAwarePlacement buildPlacement() {
        return currentPlacement();
    }

    PathAwarePlacement currentPlacement() {
        String leafMode = this.directLeafButton.isSelected()
                ? TreepeaterSettings.IMPORT_LEAF_MODE_DIRECT
                : TreepeaterSettings.IMPORT_LEAF_MODE_METHOD_FOLDER;

        return new PathAwarePlacement(
                leafMode,
                this.baseLeafNameField.getText(),
                this.lenientCheck.isSelected(),
                ((Number) this.maxSkipSpinner.getValue()).intValue(),
                ((Number) this.thresholdSpinner.getValue()).intValue(),
                this.normalizeCheck.isSelected());
    }

    private static JLabel createSubsectionHeader(String title) {
        JLabel label = new JLabel(title);
        Font font = label.getFont();
        label.setFont(font.deriveFont(Font.BOLD));
        if (UIManager.getColor("Colors.ui.text.header") != null) {
            label.setForeground(UIManager.getColor("Colors.ui.text.header"));
        }
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
}
