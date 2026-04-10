package treepeater.settings;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.Document;

import treepeater.Treepeater;
import treepeater.components.CustomButton;
import treepeater.components.RoundedPanel;
import treepeater.components.SvgIcon;
import treepeater.icons.CloseIcon;
import treepeater.requestResponse.Status;

/**
 * Modal dialog for creating or editing a {@link Status}.
 * Returns the resulting {@link Status} on confirmation, or {@code null} on cancel.
 */
public final class StatusEditDialog {

    private StatusEditDialog() {}

    /**
     * Opens the dialog. Blocks until the user closes it.
     *
     * @param parent  the component to center the dialog over
     * @param initial the status to pre-populate fields with, or {@code null} to create a new one
     * @return the new/edited {@link Status}, or {@code null} if the user cancelled
     */
    public static Status showDialog(Component parent, Status initial) {
        Window owner = parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent);

        String dialogTitle = initial == null ? "New Status" : "Edit Status";
        StatusDialog dialog = new StatusDialog(owner, dialogTitle, Dialog.ModalityType.APPLICATION_MODAL, initial);
        dialog.setVisible(true);

        if (!dialog.confirmed) return null;

        String name = dialog.nameField.getText().trim();
        if (name.isEmpty()) name = "Unnamed";

        if (dialog.useColorsNamedKeysCheckBox.isSelected()) {
            return new Status(dialog.getId(), dialog.getName(), dialog.getKeyedColors(), dialog.getSvgContent());
        }
        return new Status(dialog.getId(), dialog.getName(), dialog.getColors(), dialog.getSvgContent());
    }

    private static class StatusDialog extends JDialog {
        private static final int PREVIEW_ROW_STRIP_ARC = 10;

        private static final Color DARK_MODE_BACKGROUDND = new Color(50, 51, 52);
        private static final Color LIGHT_NODE_BACKGROUND = new Color(251, 251, 251);

        public String id;
        public Status.StatusColors colors;
        public Status.StatusKeyedColors keyedColors;
        public String svgContent;
        public boolean confirmed;
        public JTextField nameField;
        public JCheckBox useColorsNamedKeysCheckBox;
        public JLabel previewIconLabel;
        public JLabel previewTextLabel;
        public RoundedPanel previewStrip;
        public JButton backgroundButton;
        public JButton borderButton;
        public JButton backgroundDarkModeButton;
        public JButton borderDarkModeButton;
        public JTextField backgroundTextField;
        public JTextField borderTextField;
        public JTextField backgroundDarkModeTextField;
        public JTextField borderDarkModeTextField;
        public JCheckBox previewDarkModeCheckBox;
        public JPanel previewStatus;
        public JPanel previewBackground;
        public JPanel colorPanel;

        /**
         * Builds the dialog UI from {@code initial} state and positions it relative to {@code parent}.
         */
        public StatusDialog(Window parent, String title, Dialog.ModalityType modality, Status initial) {
            super(parent, title, modality);
            this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

            initModelFromInitial(initial);

            JTextField nameField = new JTextField(initial != null ? initial.getStatus() : "New Status", 20);
            this.nameField = nameField;
            addSimpleDocumentListener(nameField.getDocument(), this::updatePreview);

            useColorsNamedKeysCheckBox = new JCheckBox("", initial != null && initial.getKeyedColors().isPresent());

            colorPanel = createColorPanel();
            updateColorModeCard();

            buildPreviewComponent();
            buildPreviewPanel();

            JLabel svgFileLabel = createSvgFileLabel(initial);
            JButton pickSvgButton = createSvgPickerButton(svgFileLabel);

            useColorsNamedKeysCheckBox.addActionListener(e -> {
                updateColorModeCard();
                colorPanel.repaint();
                updatePreview();
            });

            JPanel formPanel = buildFormPanel(colorPanel, nameField, svgFileLabel, pickSvgButton);

            CustomButton okButton = new CustomButton("OK");
            okButton.setBackground(UIManager.getColor("Button.primary.background"));
            okButton.setForeground(UIManager.getColor("Button.primary.foreground"));
            okButton.setHoverBackground(UIManager.getColor("Button.primary.hoverBackground"));
            JButton cancelButton = new JButton("Cancel");

            okButton.addActionListener(e -> {
                this.confirmed = true;
                this.dispose();
            });
            cancelButton.addActionListener(e -> {
                this.confirmed = false;
                this.dispose();
            });

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
            buttonPanel.add(cancelButton);
            buttonPanel.add(okButton);

            JPanel content = new JPanel();
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.add(formPanel);
            content.add(Box.createVerticalStrut(4));
            content.add(buttonPanel);

            this.add(content);
            this.pack();
            this.setLocationRelativeTo(parent);
        }

        private void initModelFromInitial(Status initial) {
            id = initial != null ? initial.getId() : Treepeater.getStatusRegistry().generateId();
            colors = initial != null ? initial.getColors().orElse(StatusDialog.getDefaultColors()) : StatusDialog.getDefaultColors();
            keyedColors = initial != null ? initial.getKeyedColors().orElse(StatusDialog.getDefaultNamedColors()) : StatusDialog.getDefaultNamedColors();
            svgContent = initial != null ? initial.getSvgContent() : StatusRegistry.readSvgResource("/icons/folder.svg");
        }

        private static Status.StatusColors getDefaultColors() {
            // TODO: Use the correct colors from the UI manager
            return new Status.StatusColors(new Color(151, 106, 20), new Color(219, 160, 47), new Color(151, 106, 20), new Color(219, 160, 47));
        }

        private static Status.StatusKeyedColors getDefaultNamedColors() {
            return new Status.StatusKeyedColors("Colors.ui.groups.2.background", "Colors.ui.groups.2.accent", "Colors.ui.groups.2.background", "Colors.ui.groups.2.accent");
        }

        /**
         * Switches between the color-picker card and the named UI color key card.
         */
        private void updateColorModeCard() {
            ((CardLayout) colorPanel.getLayout()).show(colorPanel, useColorsNamedKeysCheckBox.isSelected() ? "name" : "picker");
        }

        /**
         * Registers a {@link DocumentListener} that runs {@code onChange} for any document update.
         */
        private static void addSimpleDocumentListener(Document doc, Runnable onChange) {
            doc.addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    onChange.run();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    onChange.run();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    onChange.run();
                }
            });
        }

        private static void initColorRowLabelConstraints(GridBagConstraints labelGbc) {
            labelGbc.gridx = 0;
            labelGbc.weightx = 0;
            labelGbc.fill = GridBagConstraints.NONE;
            labelGbc.anchor = GridBagConstraints.WEST;
            labelGbc.insets = new Insets(2, 0, 2, 8);
        }

        private static void initColorRowFieldConstraints(GridBagConstraints pickerGbc) {
            pickerGbc.gridx = 1;
            pickerGbc.weightx = 1;
            pickerGbc.fill = GridBagConstraints.HORIZONTAL;
            pickerGbc.insets = new Insets(2, 0, 2, 0);
        }

        private JPanel createColorPanel() {
            JPanel colorPanel = new JPanel(new CardLayout());

            colorPanel.add(createColorPickerPanel(), "picker");
            colorPanel.add(createColorNamePanel(), "name");

            return colorPanel;
        }

        private void addColorPickerRow(JPanel colorPickerPanel, GridBagConstraints labelGbc, GridBagConstraints pickerGbc,
                int row, String label, JButton button, Supplier<Color> chooserInitial) {
            labelGbc.gridy = row;
            colorPickerPanel.add(new JLabel(label), labelGbc);
            pickerGbc.gridy = row;
            button.addActionListener(e -> {
                Color newColor = pickColor(button, chooserInitial.get());
                if (newColor != null) {
                    button.setBackground(newColor);
                }
                StatusDialog.this.updatePreview();
            });
            colorPickerPanel.add(button, pickerGbc);
        }

        private JPanel createColorPickerPanel() {
            JPanel colorPickerPanel = new JPanel(new GridBagLayout());
            GridBagConstraints pickerGbc = new GridBagConstraints();
            GridBagConstraints labelGbc = new GridBagConstraints();
            initColorRowLabelConstraints(labelGbc);
            initColorRowFieldConstraints(pickerGbc);

            backgroundButton = createColorButton(colors.backgroundColor());
            addColorPickerRow(colorPickerPanel, labelGbc, pickerGbc, 0, "Background:", backgroundButton, colors::backgroundColor);

            borderButton = createColorButton(colors.borderColor());
            addColorPickerRow(colorPickerPanel, labelGbc, pickerGbc, 1, "Border:", borderButton, colors::borderColor);

            backgroundDarkModeButton = createColorButton(colors.backgroundDarkModeColor());
            addColorPickerRow(colorPickerPanel, labelGbc, pickerGbc, 2, "Dark Mode Background:", backgroundDarkModeButton, colors::backgroundDarkModeColor);

            borderDarkModeButton = createColorButton(colors.borderColorDarkModeColor());
            addColorPickerRow(colorPickerPanel, labelGbc, pickerGbc, 3, "Dark Mode Border:", borderDarkModeButton, colors::borderColorDarkModeColor);

            return colorPickerPanel;
        }

        /**
         * Shows a modal color chooser and returns the chosen color, or {@code null} if cancelled.
         */
        private Color pickColor(Component parent, Color initialColor) {
            final JColorChooser colorChooser = new JColorChooser(initialColor);
            final JDialog dialog = JColorChooser.createDialog(
                parent,
                "Pick a Color",
                true,
                colorChooser,
                null,
                null
            );

            final Color[] selectedColor = new Color[1];
            selectedColor[0] = null;

            // Override OK action to set the selected color
            dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

            // Add an action listener to the OK button
            for (Component comp : dialog.getContentPane().getComponents()) {
                if (comp instanceof JPanel) {
                    for (Component buttonComp : ((JPanel) comp).getComponents()) {
                        if (buttonComp instanceof JButton) {
                            JButton button = (JButton) buttonComp;
                            String text = button.getText();
                            if ("OK".equalsIgnoreCase(text)) {
                                button.addActionListener(e -> {
                                    selectedColor[0] = colorChooser.getColor();
                                    dialog.dispose();
                                });
                            } else if ("Cancel".equalsIgnoreCase(text)) {
                                button.addActionListener(e -> {
                                    selectedColor[0] = null;
                                    dialog.dispose();
                                });
                            }
                        }
                    }
                }
            }

            dialog.setVisible(true);
            return selectedColor[0];
        }

        private JTextField addNamedColorRow(JPanel colorNamePanel, GridBagConstraints labelGbc, GridBagConstraints pickerGbc,
                int row, String label, String key) {
            labelGbc.gridy = row;
            colorNamePanel.add(new JLabel(label), labelGbc);
            pickerGbc.gridy = row;
            JTextField textField = new JTextField(key, 20);
            colorNamePanel.add(createColorNameTextField(textField, key), pickerGbc);
            return textField;
        }

        private JPanel createColorNamePanel() {
            JPanel colorNamePanel = new JPanel(new GridBagLayout());
            GridBagConstraints pickerGbc = new GridBagConstraints();
            GridBagConstraints labelGbc = new GridBagConstraints();
            initColorRowLabelConstraints(labelGbc);
            initColorRowFieldConstraints(pickerGbc);

            backgroundTextField = addNamedColorRow(colorNamePanel, labelGbc, pickerGbc, 0, "Background:", keyedColors.backgroundColorKey());
            borderTextField = addNamedColorRow(colorNamePanel, labelGbc, pickerGbc, 1, "Border:", keyedColors.borderColorKey());
            backgroundDarkModeTextField = addNamedColorRow(colorNamePanel, labelGbc, pickerGbc, 2, "Dark Mode Background:", keyedColors.backgroundDarkModeColorKey());
            borderDarkModeTextField = addNamedColorRow(colorNamePanel, labelGbc, pickerGbc, 3, "Dark Mode Border:", keyedColors.borderColorDarkModeColorKey());

            return colorNamePanel;
        }

        /**
         * Wraps a key text field with a small swatch that resolves the key via {@link UIManager} when possible.
         */
        private JPanel createColorNameTextField(JTextField textField, String key) {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

            JPanel previewPanel = new JPanel();
            CardLayout card = new CardLayout();
            previewPanel.setLayout(card);

            JLabel unknownLabel = new JLabel(new CloseIcon().withColor(UIManager.getColor("Label.disabledForeground")));

            JLabel colorLabel = new JLabel(" ");
            colorLabel.setOpaque(true);
            colorLabel.setPreferredSize(new Dimension(16, 16));
            colorLabel.setMinimumSize(new Dimension(16, 16));

            previewPanel.add(unknownLabel, "unknown");
            previewPanel.add(colorLabel, "color");

            addSimpleDocumentListener(textField.getDocument(), () -> {
                String k = textField.getText();
                Color color = UIManager.getColor(k);
                if (color != null) {
                    card.show(previewPanel, "color");
                    colorLabel.setBackground(color);
                } else {
                    card.show(previewPanel, "unknown");
                }
                StatusDialog.this.updatePreview();
            });

            Color color = UIManager.getColor(key);
            if (color != null) {
                card.show(previewPanel, "color");
                colorLabel.setBackground(color);
            } else {
                card.show(previewPanel, "unknown");
            }

            panel.add(textField);
            panel.add(previewPanel);

            return panel;
        }

        private JButton createColorButton(Color color) {
            JButton btn = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    g.setColor(getBackground());
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                    g.setColor(UIManager.getColor("Component.borderColor") != null
                            ? UIManager.getColor("Component.borderColor")
                            : Color.GRAY);
                    g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                }
            };
            Dimension swatch = new Dimension(32, 24);
            btn.setPreferredSize(swatch);
            btn.setMinimumSize(swatch);
            btn.setBackground(color);
            btn.setOpaque(false);
            btn.setContentAreaFilled(false);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            btn.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            return btn;
        }

        /**
         * Lays out the rounded strip with icon and sample label for the preview row.
         */
        private void buildPreviewComponent() {
            previewStrip = new RoundedPanel(PREVIEW_ROW_STRIP_ARC);
            previewStrip.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
            previewStrip.setLayout(new GridBagLayout());

            previewIconLabel = new JLabel();
            previewIconLabel.setOpaque(false);

            previewTextLabel = new JLabel();
            previewTextLabel.setOpaque(false);
            previewTextLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));

            GridBagConstraints previewGbc = new GridBagConstraints();
            previewGbc.gridy = 0;
            previewGbc.insets = new Insets(2, 3, 2, 3);
            previewGbc.gridx = 0;
            previewGbc.weightx = 0;
            previewGbc.fill = GridBagConstraints.NONE;
            previewGbc.anchor = GridBagConstraints.WEST;
            previewStrip.add(previewIconLabel, previewGbc);
            previewGbc.gridx = 1;
            previewStrip.add(Box.createHorizontalStrut(4), previewGbc);
            previewGbc.gridx = 2;
            previewGbc.weightx = 1;
            previewGbc.fill = GridBagConstraints.HORIZONTAL;
            previewStrip.add(previewTextLabel, previewGbc);

            previewStrip.setPreferredSize(new Dimension(320, 28));
            previewStrip.setMinimumSize(new Dimension(200, 28));

            previewDarkModeCheckBox = new JCheckBox("Preview dark mode");
            previewDarkModeCheckBox.addActionListener(e -> updatePreview());
        }

        /**
         * Wraps the preview strip in padding and the outer background used for light/dark preview.
         */
        private void buildPreviewPanel() {
            previewStatus = new JPanel(new GridBagLayout());
            previewStatus.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            GridBagConstraints chromeGbc = new GridBagConstraints();
            chromeGbc.gridx = 0;
            chromeGbc.gridy = 0;
            chromeGbc.weightx = 1;
            chromeGbc.fill = GridBagConstraints.HORIZONTAL;
            chromeGbc.anchor = GridBagConstraints.WEST;
            previewStatus.add(previewStrip, chromeGbc);
        }

        private JLabel createSvgFileLabel(Status initial) {
            JLabel svgFileLabel = new JLabel(initial != null ? "<embedded>" : "<default>");
            svgFileLabel.setFont(svgFileLabel.getFont().deriveFont(Font.ITALIC));
            svgFileLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            return svgFileLabel;
        }

        /**
         * Opens a file chooser to replace the SVG content and updates {@code svgFileLabel} with the file name or an error.
         */
        private JButton createSvgPickerButton(JLabel svgFileLabel) {
            JButton pickSvgButton = new JButton("Choose SVG file\u2026");

            pickSvgButton.addActionListener(e -> {
                JFileChooser fc = new JFileChooser();
                fc.setDialogTitle("Select SVG Icon");
                fc.setFileFilter(new FileNameExtensionFilter("SVG Images (*.svg)", "svg"));
                fc.setAcceptAllFileFilterUsed(false);
                if (fc.showOpenDialog(StatusDialog.this) == JFileChooser.APPROVE_OPTION) {
                    File file = fc.getSelectedFile();
                    try {
                        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                        StatusDialog.this.svgContent = content;
                        svgFileLabel.setText(file.getName());
                        updatePreview();
                    } catch (IOException ex) {
                        svgFileLabel.setText("Error reading file");
                    }
                }
            });
            return pickSvgButton;
        }

        /**
         * Assembles the main grid: name, color mode, colors, SVG row, and preview block with OK/cancel wired separately.
         */
        private JPanel buildFormPanel(JPanel colorPanel, JTextField nameField, JLabel svgFileLabel, JButton pickSvgButton) {
            JPanel formPanel = new JPanel(new GridBagLayout());
            formPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

            int row = 0;

            row = addFormRow(formPanel, row, "Name:", nameField);

            row = addFormRow(formPanel, row, "Use color names:", useColorsNamedKeysCheckBox);

            row = addFormRow(formPanel, row, "Colors:", colorPanel);

            JPanel svgRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            svgRow.setOpaque(false);
            svgRow.add(pickSvgButton);
            svgRow.add(svgFileLabel);
            row = addFormRow(formPanel, row, "SVG icon:", svgRow);

            JPanel previewRow = new JPanel(new GridBagLayout());
            previewRow.setOpaque(false);
            GridBagConstraints prGbc = new GridBagConstraints();
            prGbc.gridx = 0;
            prGbc.gridy = 0;
            prGbc.anchor = GridBagConstraints.WEST;
            prGbc.insets = new Insets(0, 0, 8, 0);
            previewRow.add(previewDarkModeCheckBox, prGbc);

            previewBackground = new JPanel(new BorderLayout());
            previewBackground.add(previewStatus, BorderLayout.CENTER);
            previewBackground.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

            prGbc.gridy = 1;
            prGbc.weightx = 1;
            prGbc.fill = GridBagConstraints.HORIZONTAL;
            prGbc.insets = new Insets(0, 0, 0, 0);
            previewRow.add(previewBackground, prGbc);

            updatePreview();
            addFormRow(formPanel, row, "Preview:", previewRow);
            return formPanel;
        }

        /**
         * Gets the background color for the preview panel.
         *
         * Considers whether the user selected dark mode.
         * @return light or dark canvas color behind the status preview
         */
        private Color getPreviewBackground() {
            if (previewDarkModeCheckBox.isSelected()) {
                return DARK_MODE_BACKGROUDND;
            }
            return LIGHT_NODE_BACKGROUND;
        }

        /**
         * Gets the background color as currently configured by the user.
         *
         * It considers whether the user selected named colors or the color picker and also
         * if the user selected dark mode.
         * @return Background color for the status preview
         */
        private Color getPreviewStatusBackgroundColor() {
            if (useColorsNamedKeysCheckBox.isSelected()) {
                if (previewDarkModeCheckBox.isSelected()) {
                    return UIManager.getColor(backgroundDarkModeTextField.getText());
                }
                return UIManager.getColor(backgroundTextField.getText());
            }
            if (previewDarkModeCheckBox.isSelected()) {
                return backgroundDarkModeButton.getBackground();
            }
            return backgroundButton.getBackground();
        }

        /**
         * Gets the border color as currently configured by the user.
         *
         * It considers whether the user selected named colors or the color picker and also
         * if the user selected dark mode.
         * @return Border color for the status preview
         */
        private Color getPreviewStatusBorderColor() {
            if (useColorsNamedKeysCheckBox.isSelected()) {
                if (previewDarkModeCheckBox.isSelected()) {
                    return UIManager.getColor(borderDarkModeTextField.getText());
                }
                return UIManager.getColor(borderTextField.getText());
            }

            if (previewDarkModeCheckBox.isSelected()) {
                return borderDarkModeButton.getBackground();
            }
            return borderButton.getBackground();
        }

        /**
         * Refreshes preview backgrounds, strip colors, label text, and the SVG icon from current field values.
         */
        private void updatePreview() {
            Color fill = getPreviewStatusBackgroundColor();
            Color borderCol = getPreviewStatusBorderColor();

            previewStatus.setBackground(getPreviewBackground());
            previewBackground.setBackground(getPreviewBackground());
            previewBackground.repaint();

            previewStrip.setBackground(fill);
            previewStrip.setBorderColor(borderCol);
            previewStrip.repaint();

            String labelText = nameField.getText().trim();
            if (labelText.isEmpty()) {
                labelText = "Sample request name";
            }
            previewTextLabel.setText(labelText);
            Color fg = previewDarkModeCheckBox.isSelected() ? new Color(255, 255, 255) : new Color(0, 0, 0);
            previewTextLabel.setForeground(fg);

            if (this.svgContent == null || this.svgContent.isBlank()) {
                previewIconLabel.setIcon(null);
                return;
            }

            Icon icon = SvgIcon.fromContent(this.svgContent).withSize(18, 18).withColor(borderCol);
            previewIconLabel.setIcon(icon);
        }

        /**
         * Adds a label in column 0 and a field in column 1, with vertical spacing between rows.
         *
         * @return the next row index ({@code row + 1})
         */
        private int addFormRow(JPanel parent, int row, String labelText, Component field) {
            GridBagConstraints labelGbc = new GridBagConstraints();
            labelGbc.gridx   = 0;
            labelGbc.gridy   = row;
            labelGbc.anchor  = GridBagConstraints.FIRST_LINE_START;
            labelGbc.fill    = GridBagConstraints.NONE;
            labelGbc.weightx = 0;
            labelGbc.insets  = new Insets(row > 0 ? 8 : 0, 0, 0, 12);

            GridBagConstraints fieldGbc = new GridBagConstraints();
            fieldGbc.gridx   = 1;
            fieldGbc.gridy   = row;
            fieldGbc.anchor  = GridBagConstraints.WEST;
            fieldGbc.fill    = GridBagConstraints.HORIZONTAL;
            fieldGbc.weightx = 1;
            fieldGbc.insets  = new Insets(row > 0 ? 8 : 0, 0, 0, 0);

            parent.add(new JLabel(labelText), labelGbc);
            parent.add(field, fieldGbc);
            return row + 1;
        }

        public String getId() {
            return id;
        }

        /**
         * @return trimmed status name from the name field, or {@code null} if the field is not initialized
         */
        public String getName() {
            return nameField != null ? nameField.getText().trim() : null;
        }

        /**
         * @return picked {@link Status.StatusColors} when the dialog is in direct color mode, otherwise {@code null}
         */
        public Status.StatusColors getColors() {
            // Depending on which color mode is selected, return the values from input fields
            if (useColorsNamedKeysCheckBox != null && !useColorsNamedKeysCheckBox.isSelected()) {
                // Plain color mode: capture colors from color buttons at construction time
                Status.StatusColors c = new Status.StatusColors(
                    backgroundButton.getBackground(),
                    borderButton.getBackground(),
                    backgroundDarkModeButton.getBackground(),
                    borderDarkModeButton.getBackground()
                );
                return c;

            }
            return null;
        }

        /**
         * @return {@link Status.StatusKeyedColors} from the text fields when named keys are enabled, otherwise {@code null}
         */
        public Status.StatusKeyedColors getKeyedColors() {
            // If the color mode is keyed, return from the UI controls handling named color keys.
            if (useColorsNamedKeysCheckBox != null && useColorsNamedKeysCheckBox.isSelected()) {
                // Construct StatusKeyedColors with the color values from the keyed color text fields
                Status.StatusKeyedColors kc = new Status.StatusKeyedColors(
                    backgroundTextField != null ? backgroundTextField.getText().trim() : null,
                    borderTextField != null ? borderTextField.getText().trim() : null,
                    backgroundDarkModeTextField != null ? backgroundDarkModeTextField.getText().trim() : null,
                    borderDarkModeTextField != null ? borderDarkModeTextField.getText().trim() : null
                );
                return kc;

            }
            return null;
        }

        /**
         * @return the SVG source currently chosen for this status (file contents or default resource text)
         */
        public String getSvgContent() {
            // This should return the latest contents of the SVG editor/text field, if one exists; otherwise, fallback to the field
            return svgContent;
        }
    }
}
