package treepeater.settings;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JRadioButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;

import burp.api.montoya.ui.settings.SettingsPanelWithData;
import treepeater.Treepeater;
import treepeater.ai.ollama.OllamaProvider;
import treepeater.importing.ImportOptions.DirectNameMode;
import treepeater.requestResponse.Status;

/**
 * Burp settings UI for Treepeater: wraps the Montoya-built settings row and adds shortcut capture.
 */
public final class TreepeaterSettingsPanel implements SettingsPanelWithData {
    private static final int ROW_GAP = 10;
    private static final int SECTION_GAP = 20;
    private static final int INNER_SECTION_GAP = 16;
    /** Padding around each LLM settings row (label + field). */
    private static final int LLM_ROW_PADDING = 8;

    private final TreepeaterSettings settings;
    private final JPanel root;

    public TreepeaterSettingsPanel() {
        this.settings = TreepeaterSettings.getInstance();
        this.root = new JPanel();
        this.root.setLayout(new BoxLayout(this.root, BoxLayout.Y_AXIS));
        this.root.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel hotkeysPanel = this.createTitledSection(
            "Hotkeys",
            "Customize the keyboard shortcuts for various Treepeater actions here. " +
            "To change a hotkey, simply click on the current shortcut and press your desired key combination. " +
            "Ensure that you pick combinations that do not conflict with system or Burp Suite shortcuts for the smoothest experience.",
            this.createHotkeySetting()
        );
        hotkeysPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, SECTION_GAP, 0));
        this.root.add(hotkeysPanel);

        this.root.add(new JSeparator(JSeparator.HORIZONTAL));



        this.root.add(this.createTitledSection(
            "Status",
            "Configure the statuses available for Treepeater nodes. " +
            "You can add your own, edit existing ones, reorder them, or delete ones you don\u2019t need. " +
            "Each status has a name, background and border/icon color, and an SVG icon.",
            this.createStatusPanel()
        ));

        this.root.add(new JSeparator(JSeparator.HORIZONTAL));

        JPanel importPanel = this.createTitledSection(
            "Import",
            "Configure how imported requests are named and placed. "
                + "Direct import (\"Send to Treepeater (direct)\") names each request at the tree root using ID, path, or URL. "
                + "Path-aware import (\"Send to Treepeater (path-aware)\") builds folders from the request path. "
                + "In direct leaf mode the request becomes a leaf named after the last path segment, sitting next to any folder for deeper paths. "
                + "In method-folder mode the request is placed under a per-method folder (e.g. [GET]) with the base leaf name configured below. "
                + "Lenient folder grouping (optional) lets path-aware import reuse existing folders that include extra leading organizational segments. "
                + "Dynamic path segments (optional) rewrite recognizable dynamic URL parts into placeholders such as :id or :uuid.",
            this.createImportSettingsPanel()
        );
        this.root.add(importPanel);

        this.root.add(new JSeparator(JSeparator.HORIZONTAL));

        JPanel llmPanel = this.createTitledSection(
            "LLMs",
            "Configure connection details for Ollama, Anthropic, and Azure OpenAI / Microsoft Foundry. "
                + "The AI tab reads these values from here; pick the provider and model (or deployment name) in the AI toolbar.",
            this.createLlmSettingsPanel()
        );
        this.root.add(llmPanel);

        this.root.add(new JSeparator(JSeparator.HORIZONTAL));

        JPanel apiPanel = this.createTitledSection(
            "API & MCP server",
            "Expose Treepeater to local tooling and MCP clients through a loopback HTTP server. "
                + "The server is off by default; enable it only while you need it, and grant the write and "
                + "execute permissions deliberately, since they let callers change your tree and send traffic.",
            this.createApiSettingsPanel()
        );
        this.root.add(apiPanel);
    }

    private JPanel createTitledSection(String title, String description, JComponent content) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(SECTION_GAP, 0, SECTION_GAP, 0));

        JLabel titleLabel = new JLabel(title);
        Font titleFont = titleLabel.getFont();
        titleLabel.setForeground(UIManager.getColor("Colors.ui.text.header"));
        titleLabel.setFont(titleFont.deriveFont(Font.BOLD).deriveFont(titleFont.getSize2D() + 2f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea descriptionLabel = new JTextArea(description);
        descriptionLabel.setEditable(false);
        descriptionLabel.setFocusable(false);
        descriptionLabel.setLineWrap(true);
        descriptionLabel.setWrapStyleWord(true);
        descriptionLabel.setOpaque(false);
        descriptionLabel.setBorder(null);
        descriptionLabel.setFont(UIManager.getFont("Label.font"));
        descriptionLabel.setForeground(UIManager.getColor("Label.foreground"));
        descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(INNER_SECTION_GAP));
        panel.add(descriptionLabel);

        panel.add(Box.createVerticalStrut(INNER_SECTION_GAP));
        panel.add(content);

        return panel;
    }

    private JComponent createHotkeySetting() {
        JPanel root = new JPanel(new GridBagLayout());
        root.setAlignmentX(Component.LEFT_ALIGNMENT);

        int row = 0;
        row = this.addHotkeySetting(root, row, "Send to Treepeater hotkey:", this.settings::getSendHotkey, this.settings::setSendHotkey);
        row = this.addHotkeySetting(root, row, "Send to Treepeater (path-aware) hotkey:", this.settings::getSendPathAwareHotkey, this.settings::setSendSortedHotkey);
        row = this.addHotkeySetting(root, row, "Send to Treepeater (manual) hotkey:", this.settings::getSendManualHotkey, this.settings::setSendManualHotkey);
        row = this.addHotkeySetting(root,row, "Send request hotkey:", this.settings::getSendRequestHotkey, this.settings::setSendRequestHotkey);
        row = this.addHotkeySetting(root,row, "History back hotkey:", this.settings::getHistoryBackHotkey, this.settings::setHistoryBackHotkey);
        row = this.addHotkeySetting(root,row, "History forward hotkey:", this.settings::getHistoryForwardHotkey, this.settings::setHistoryForwardHotkey);
        row = this.addHotkeySetting(
                root,row,
                "Copy same-parent request hotkey:",
                this.settings::getCopySameParentRequestHotkey,
                this.settings::setCopySameParentRequestHotkey);
        row = this.addHotkeySetting(root,row, "Rename hotkey:", this.settings::getRenameHotkey, this.settings::setRenameHotkey);
        row = this.addHotkeySetting(root,row, "Change status hotkey:", this.settings::getChangeStatusHotkey, this.settings::setChangeStatusHotkey);
        row = this.addHotkeySetting(root,row, "Edit target hotkey:", this.settings::getEditTargetHotkey, this.settings::setEditTargetHotkey);
        row = this.addHotkeySetting(root,row, "Previous request tab hotkey:", this.settings::getTabPreviousHotkey, this.settings::setTabPreviousHotkey);
        row = this.addHotkeySetting(root,row, "Next request tab hotkey:", this.settings::getTabNextHotkey, this.settings::setTabNextHotkey);
        this.addHotkeySetting(root,row, "Focus request tree hotkey:", this.settings::getFocusTreeHotkey, this.settings::setFocusTreeHotkey);
    
        return root;
    }

    private int addHotkeySetting(JPanel parent, int row, String labelText, Supplier<String> getter, Consumer<String> setter) {
        JLabel hotkeyLabel = new JLabel(labelText);
        JButton hotkeyButton = new JButton(getter.get());
        hotkeyButton.addActionListener(e -> {
            String hotkey = HotkeyCaptureDialog.showDialog(this.root);
            if (hotkey != null) {
                setter.accept(hotkey);
                hotkeyButton.setText(hotkey);
            }
        });

        Insets labelInsets = new Insets(row > 0 ? ROW_GAP : 0, 0, 0, 8);
        Insets buttonInsets = new Insets(row > 0 ? ROW_GAP : 0, 0, 0, 4);

        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.gridx = 0;
        labelGbc.gridy = row;
        labelGbc.anchor = GridBagConstraints.WEST;
        labelGbc.fill = GridBagConstraints.NONE;
        labelGbc.weightx = 0;
        labelGbc.insets = labelInsets;

        GridBagConstraints buttonGbc = new GridBagConstraints();
        buttonGbc.gridx = 1;
        buttonGbc.gridy = row;
        buttonGbc.anchor = GridBagConstraints.WEST;
        buttonGbc.fill = GridBagConstraints.NONE;
        buttonGbc.weightx = 1;
        buttonGbc.insets = buttonInsets;

        parent.add(hotkeyLabel, labelGbc);
        parent.add(hotkeyButton, buttonGbc);
        return row + 1;
    }

    private JComponent createImportSettingsPanel() {
        JPanel outer = new JPanel();
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.setAlignmentX(Component.LEFT_ALIGNMENT);

        outer.add(this.createDirectImportNamingPanel());
        outer.add(Box.createVerticalStrut(INNER_SECTION_GAP));
        outer.add(this.createSubsectionHeader("Path-aware import"));

        JRadioButton directButton = new JRadioButton(
                "Direct: leaf named after the last path segment, next to any nesting folder");
        JRadioButton methodButton = new JRadioButton(
                "Method folders: leaf under a per-method folder (e.g. [GET])");
        directButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        methodButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        directButton.setOpaque(false);
        methodButton.setOpaque(false);

        ButtonGroup group = new ButtonGroup();
        group.add(directButton);
        group.add(methodButton);

        boolean methodMode = TreepeaterSettings.IMPORT_LEAF_MODE_METHOD_FOLDER
                .equals(this.settings.getImportLeafMode());
        directButton.setSelected(!methodMode);
        methodButton.setSelected(methodMode);

        directButton.addActionListener(e ->
                this.settings.setImportLeafMode(TreepeaterSettings.IMPORT_LEAF_MODE_DIRECT));
        methodButton.addActionListener(e ->
                this.settings.setImportLeafMode(TreepeaterSettings.IMPORT_LEAF_MODE_METHOD_FOLDER));

        JPanel baseNamePanel = new JPanel(new GridBagLayout());
        baseNamePanel.setOpaque(false);
        baseNamePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        this.addPersistedTextRow(
                baseNamePanel,
                0,
                "Method-folder base leaf name:",
                this.settings.getImportBaseLeafName(),
                this.settings::setImportBaseLeafName,
                false);

        outer.add(directButton);
        outer.add(Box.createVerticalStrut(4));
        outer.add(methodButton);
        outer.add(Box.createVerticalStrut(INNER_SECTION_GAP));
        outer.add(baseNamePanel);
        outer.add(Box.createVerticalStrut(INNER_SECTION_GAP));
        outer.add(this.createLenientFolderGroupingPanel());
        outer.add(Box.createVerticalStrut(INNER_SECTION_GAP));
        outer.add(this.createDynamicSegmentPanel());
        return outer;
    }

    private JComponent createDirectImportNamingPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(this.createSubsectionHeader("Direct import"));

        JRadioButton idButton = new JRadioButton("ID: sequential number (1, 2, …)");
        JRadioButton pathButton = new JRadioButton("Path: request path without query (e.g. /api/users)");
        JRadioButton urlButton = new JRadioButton("URL: full URL without query");
        idButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        pathButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        urlButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        idButton.setOpaque(false);
        pathButton.setOpaque(false);
        urlButton.setOpaque(false);

        ButtonGroup group = new ButtonGroup();
        group.add(idButton);
        group.add(pathButton);
        group.add(urlButton);

        DirectNameMode currentMode = this.settings.getDirectImportNameMode();
        idButton.setSelected(currentMode == DirectNameMode.ID);
        pathButton.setSelected(currentMode == DirectNameMode.PATH);
        urlButton.setSelected(currentMode == DirectNameMode.URL);

        idButton.addActionListener(e -> this.settings.setDirectImportNameMode(DirectNameMode.ID));
        pathButton.addActionListener(e -> this.settings.setDirectImportNameMode(DirectNameMode.PATH));
        urlButton.addActionListener(e -> this.settings.setDirectImportNameMode(DirectNameMode.URL));

        panel.add(idButton);
        panel.add(Box.createVerticalStrut(4));
        panel.add(pathButton);
        panel.add(Box.createVerticalStrut(4));
        panel.add(urlButton);
        return panel;
    }

    private JComponent createDynamicSegmentPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel header = this.createSubsectionHeader("Dynamic Path Segments");

        JCheckBox enabledCheck = new JCheckBox("Normalize dynamic path segments");
        enabledCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        enabledCheck.setOpaque(false);
        enabledCheck.setSelected(this.settings.isImportNormalizeDynamicSegmentsEnabled());

        JTextArea explanation = new JTextArea(
                "When enabled, path-aware import rewrites recognizable dynamic URL segments into "
                        + "placeholders before building the folder tree. Examples: /users/2/status "
                        + "becomes users/:id/status; UUIDs become :uuid; OData entity keys such as "
                        + "Products(ID=1) become Products(ID=:id). API version segments (v1, v2) and "
                        + "slugs are left unchanged. Existing literal folders are not merged; enabling "
                        + "this later may create :id siblings next to existing numeric folders. The "
                        + "original request URL is always preserved on the leaf node.");
        explanation.setEditable(false);
        explanation.setFocusable(false);
        explanation.setLineWrap(true);
        explanation.setWrapStyleWord(true);
        explanation.setOpaque(false);
        explanation.setBorder(null);
        explanation.setFont(UIManager.getFont("Label.font"));
        explanation.setForeground(UIManager.getColor("Label.foreground"));
        explanation.setAlignmentX(Component.LEFT_ALIGNMENT);

        enabledCheck.addActionListener(e ->
                this.settings.setImportNormalizeDynamicSegmentsEnabled(enabledCheck.isSelected()));

        panel.add(header);
        panel.add(Box.createVerticalStrut(4));
        panel.add(enabledCheck);
        panel.add(Box.createVerticalStrut(4));
        panel.add(explanation);
        return panel;
    }

    private JComponent createLenientFolderGroupingPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel header = this.createSubsectionHeader("Lenient Folder Grouping");

        JCheckBox enabledCheck = new JCheckBox("Enable lenient folder grouping");
        enabledCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        enabledCheck.setOpaque(false);
        enabledCheck.setSelected(this.settings.isImportGroupingFolderReconciliationEnabled());

        JTextArea explanation = new JTextArea(
                "When enabled, path-aware import can attach requests under existing folders whose path "
                        + "includes extra leading organizational segments that are not part of the URL "
                        + "(for example ServiceA/users for /users/1). Strict prefix matching is always used "
                        + "first; lenient folder grouping only applies when no exact folder chain matches. "
                        + "Raise the overlap threshold to reduce coincidental matches.");
        explanation.setEditable(false);
        explanation.setFocusable(false);
        explanation.setLineWrap(true);
        explanation.setWrapStyleWord(true);
        explanation.setOpaque(false);
        explanation.setBorder(null);
        explanation.setFont(UIManager.getFont("Label.font"));
        explanation.setForeground(UIManager.getColor("Label.foreground"));
        explanation.setAlignmentX(Component.LEFT_ALIGNMENT);

        JSpinner maxSkipSpinner = new JSpinner(
                new SpinnerNumberModel(
                        this.settings.getImportGroupingFolderReconciliationMaxSkip(), 1, 10, 1));
        maxSkipSpinner.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextField thresholdField = new JTextField(
                Integer.toString(this.settings.getImportGroupingFolderReconciliationMatchThresholdPercent()),
                6);
        thresholdField.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel maxSkipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        maxSkipRow.setOpaque(false);
        maxSkipRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        maxSkipRow.add(new JLabel("Max leading folders to skip:"));
        maxSkipRow.add(maxSkipSpinner);

        JPanel thresholdRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        thresholdRow.setOpaque(false);
        thresholdRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        thresholdRow.add(new JLabel("Minimum target path overlap (%):"));
        thresholdRow.add(thresholdField);

        Runnable updateEnabledState = () -> {
            boolean enabled = enabledCheck.isSelected();
            maxSkipSpinner.setEnabled(enabled);
            thresholdField.setEnabled(enabled);
            maxSkipRow.setEnabled(enabled);
            thresholdRow.setEnabled(enabled);
        };

        enabledCheck.addActionListener(e -> {
            this.settings.setImportGroupingFolderReconciliationEnabled(enabledCheck.isSelected());
            updateEnabledState.run();
        });

        maxSkipSpinner.addChangeListener(e ->
                this.settings.setImportGroupingFolderReconciliationMaxSkip(
                        ((Number) maxSkipSpinner.getValue()).intValue()));

        thresholdField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                try {
                    int percent = Integer.parseInt(thresholdField.getText().trim());
                    TreepeaterSettingsPanel.this.settings.setImportGroupingFolderReconciliationMatchThresholdPercent(percent);
                    thresholdField.setText(Integer.toString(
                            TreepeaterSettingsPanel.this.settings.getImportGroupingFolderReconciliationMatchThresholdPercent()));
                } catch (NumberFormatException ex) {
                    thresholdField.setText(Integer.toString(
                            TreepeaterSettingsPanel.this.settings.getImportGroupingFolderReconciliationMatchThresholdPercent()));
                }
            }
        });

        panel.add(header);
        panel.add(Box.createVerticalStrut(4));
        panel.add(enabledCheck);
        panel.add(Box.createVerticalStrut(4));
        panel.add(explanation);
        panel.add(Box.createVerticalStrut(INNER_SECTION_GAP));
        panel.add(maxSkipRow);
        panel.add(Box.createVerticalStrut(ROW_GAP));
        panel.add(thresholdRow);
        updateEnabledState.run();
        return panel;
    }

    /** Small bold subsection header used inside a settings section (e.g. Import). */
    private JLabel createSubsectionHeader(String title) {
        JLabel label = new JLabel(title);
        Font font = label.getFont();
        label.setFont(font.deriveFont(Font.BOLD));
        if (UIManager.getColor("Colors.ui.text.header") != null) {
            label.setForeground(UIManager.getColor("Colors.ui.text.header"));
        }
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JComponent createLlmSettingsPanel() {
        JPanel outer = new JPanel();
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel ollama = new JPanel();
        ollama.setLayout(new BoxLayout(ollama, BoxLayout.Y_AXIS));
        ollama.setAlignmentX(Component.LEFT_ALIGNMENT);
        ollama.setBorder(BorderFactory.createTitledBorder("Ollama"));

        JPanel ollamaFields = new JPanel(new GridBagLayout());
        ollamaFields.setAlignmentX(Component.LEFT_ALIGNMENT);
        int row = 0;
        row = this.addPersistedTextRow(
                ollamaFields,
                row,
                "Base URL:",
                this.settings.getLlmOllamaBaseUrl(),
                this.settings::setLlmOllamaBaseUrl,
                false);
        ollama.add(ollamaFields);
        ollama.add(Box.createVerticalStrut(ROW_GAP));
        ollama.add(this.createOllamaModelsPanel());

        JPanel anthropic = new JPanel(new GridBagLayout());
        anthropic.setAlignmentX(Component.LEFT_ALIGNMENT);
        anthropic.setBorder(BorderFactory.createTitledBorder("Anthropic"));
        row = 0;
        String apiKey = this.settings.getLlmAnthropicApiKey();
        row = this.addPersistedTextRow(
                anthropic,
                row,
                "API key:",
                apiKey != null ? apiKey : "",
                this.settings::setLlmAnthropicApiKey,
                true);

        JPanel azure = new JPanel(new GridBagLayout());
        azure.setAlignmentX(Component.LEFT_ALIGNMENT);
        azure.setBorder(BorderFactory.createTitledBorder("Azure OpenAI / Foundry"));
        row = 0;
        String azureEndpoint = this.settings.getLlmAzureOpenAiEndpoint();
        row = this.addPersistedTextRow(
                azure,
                row,
                "Endpoint:",
                azureEndpoint != null ? azureEndpoint : "",
                this.settings::setLlmAzureOpenAiEndpoint,
                false);
        String azureKey = this.settings.getLlmAzureOpenAiApiKey();
        row = this.addPersistedTextRow(
                azure,
                row,
                "API key:",
                azureKey != null ? azureKey : "",
                this.settings::setLlmAzureOpenAiApiKey,
                true);

        outer.add(ollama);
        outer.add(Box.createVerticalStrut(INNER_SECTION_GAP));
        outer.add(anthropic);
        outer.add(Box.createVerticalStrut(INNER_SECTION_GAP));
        outer.add(azure);
        return outer;
    }

    private JComponent createOllamaModelsPanel() {
        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setOpaque(false);
        wrapper.setBorder(BorderFactory.createEmptyBorder(0, LLM_ROW_PADDING, LLM_ROW_PADDING, LLM_ROW_PADDING));

        JLabel modelsLabel = new JLabel("Models:");
        modelsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(modelsLabel);
        wrapper.add(Box.createVerticalStrut(4));

        JTextArea warning = new JTextArea(
                "\u26A0 Models listed here must be pulled/installed in your Ollama instance before they can be used. "
                        + "Run \"ollama pull <model>\" for each model you add.");
        warning.setEditable(false);
        warning.setFocusable(false);
        warning.setLineWrap(true);
        warning.setWrapStyleWord(true);
        warning.setOpaque(false);
        warning.setBorder(null);
        warning.setFont(UIManager.getFont("Label.font"));
        warning.setForeground(UIManager.getColor("Objects.YellowDark") != null
                ? UIManager.getColor("Objects.YellowDark")
                : new Color(0xB8860B));
        warning.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.add(warning);
        wrapper.add(Box.createVerticalStrut(8));

        DefaultListModel<String> listModel = new DefaultListModel<>();
        List<String> persisted = this.settings.getOllamaModels();
        if (persisted != null) {
            persisted.forEach(listModel::addElement);
        } else {
            for (String m : OllamaProvider.FALLBACK_MODELS) {
                listModel.addElement(m);
            }
        }

        JList<String> modelList = new JList<>(listModel);
        modelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        modelList.setVisibleRowCount(6);
        modelList.setFixedCellHeight(24);
        modelList.setPreferredSize(new Dimension(280, 144));
        modelList.setMaximumSize(new Dimension(280, 144));
        modelList.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        modelList.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton addBtn = new JButton("Add");
        JButton removeBtn = new JButton("Remove");
        removeBtn.setEnabled(false);

        modelList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                removeBtn.setEnabled(modelList.getSelectedIndex() >= 0);
            }
        });

        Runnable persistModels = () -> {
            List<String> models = new ArrayList<>(listModel.size());
            for (int i = 0; i < listModel.size(); i++) {
                models.add(listModel.get(i));
            }
            this.settings.setOllamaModels(models);
        };

        addBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(
                    this.root,
                    "Enter the Ollama model name (e.g. llama3.2, mistral, codellama):",
                    "Add Ollama Model",
                    JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                String trimmed = name.trim();
                for (int i = 0; i < listModel.size(); i++) {
                    if (listModel.get(i).equalsIgnoreCase(trimmed)) {
                        return;
                    }
                }
                listModel.addElement(trimmed);
                persistModels.run();
            }
        });

        removeBtn.addActionListener(e -> {
            int idx = modelList.getSelectedIndex();
            if (idx >= 0) {
                listModel.remove(idx);
                int newSel = Math.min(idx, listModel.size() - 1);
                if (newSel >= 0) modelList.setSelectedIndex(newSel);
                persistModels.run();
            }
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.add(addBtn);
        buttons.add(removeBtn);

        wrapper.add(modelList);
        wrapper.add(Box.createVerticalStrut(4));
        wrapper.add(buttons);

        return wrapper;
    }

    private JComponent createApiSettingsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JCheckBox enabledCheck = new JCheckBox("Enable local API and MCP server");
        enabledCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        enabledCheck.setOpaque(false);
        enabledCheck.setSelected(this.settings.isApiEnabled());

        JSpinner portSpinner = new JSpinner(
                new SpinnerNumberModel(this.settings.getApiPort(), 1024, 65535, 1));
        portSpinner.setEditor(new JSpinner.NumberEditor(portSpinner, "#"));
        portSpinner.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel portLabel = new JLabel("Port:");
        JPanel portRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        portRow.setOpaque(false);
        portRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        portRow.add(portLabel);
        portRow.add(portSpinner);

        JTextField tokenField = new JTextField(this.settings.getOrCreateApiToken(), 40);
        tokenField.setEditable(false);
        JButton copyButton = new JButton("Copy");
        JButton regenerateButton = new JButton("Regenerate");

        JLabel tokenLabel = new JLabel("Bearer token:");
        JPanel tokenRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tokenRow.setOpaque(false);
        tokenRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        tokenRow.add(tokenLabel);
        tokenRow.add(tokenField);
        tokenRow.add(copyButton);
        tokenRow.add(regenerateButton);

        JCheckBox allowWriteCheck = new JCheckBox("Allow write tools (modify requests and the tree)");
        allowWriteCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        allowWriteCheck.setOpaque(false);
        allowWriteCheck.setSelected(this.settings.isApiAllowWrite());

        JCheckBox allowExecuteCheck = new JCheckBox("Allow execute tools (send HTTP requests)");
        allowExecuteCheck.setAlignmentX(Component.LEFT_ALIGNMENT);
        allowExecuteCheck.setOpaque(false);
        allowExecuteCheck.setSelected(this.settings.isApiAllowExecute());

        JTextArea endpointsLabel = new JTextArea(apiEndpointsText(this.settings.getApiPort()));
        endpointsLabel.setEditable(false);
        endpointsLabel.setFocusable(false);
        endpointsLabel.setLineWrap(true);
        endpointsLabel.setWrapStyleWord(true);
        endpointsLabel.setOpaque(false);
        endpointsLabel.setBorder(null);
        endpointsLabel.setFont(UIManager.getFont("Label.font"));
        endpointsLabel.setForeground(UIManager.getColor("Label.foreground"));
        endpointsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        Runnable updateEnabledState = () -> {
            boolean enabled = enabledCheck.isSelected();
            portLabel.setEnabled(enabled);
            portSpinner.setEnabled(enabled);
            portRow.setEnabled(enabled);
            tokenLabel.setEnabled(enabled);
            tokenField.setEnabled(enabled);
            copyButton.setEnabled(enabled);
            regenerateButton.setEnabled(enabled);
            tokenRow.setEnabled(enabled);
            allowWriteCheck.setEnabled(enabled);
            allowExecuteCheck.setEnabled(enabled);
        };

        enabledCheck.addActionListener(e -> {
            this.settings.setApiEnabled(enabledCheck.isSelected());
            updateEnabledState.run();
        });

        portSpinner.addChangeListener(e -> {
            this.settings.setApiPort(((Number) portSpinner.getValue()).intValue());
            endpointsLabel.setText(apiEndpointsText(this.settings.getApiPort()));
        });

        copyButton.addActionListener(e -> {
            StringSelection selection = new StringSelection(tokenField.getText());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        });

        regenerateButton.addActionListener(e -> {
            this.settings.setApiToken(TreepeaterSettings.generateApiToken());
            tokenField.setText(this.settings.getOrCreateApiToken());
        });

        allowWriteCheck.addActionListener(e -> this.settings.setApiAllowWrite(allowWriteCheck.isSelected()));
        allowExecuteCheck.addActionListener(e -> this.settings.setApiAllowExecute(allowExecuteCheck.isSelected()));

        panel.add(enabledCheck);
        panel.add(Box.createVerticalStrut(INNER_SECTION_GAP));
        panel.add(portRow);
        panel.add(Box.createVerticalStrut(ROW_GAP));
        panel.add(tokenRow);
        panel.add(Box.createVerticalStrut(INNER_SECTION_GAP));
        panel.add(this.createSubsectionHeader("Permissions"));
        panel.add(Box.createVerticalStrut(4));
        panel.add(allowWriteCheck);
        panel.add(Box.createVerticalStrut(4));
        panel.add(allowExecuteCheck);
        panel.add(Box.createVerticalStrut(INNER_SECTION_GAP));
        panel.add(endpointsLabel);
        updateEnabledState.run();
        return panel;
    }

    private static String apiEndpointsText(int port) {
        return "The server listens on 127.0.0.1 only and never accepts connections from other hosts. "
                + "Every request must carry the bearer token above in an "
                + "Authorization: Bearer <token> header. The MCP endpoint is "
                + "http://127.0.0.1:" + port + "/mcp and the REST API is at "
                + "http://127.0.0.1:" + port + "/api.";
    }

    private int addPersistedTextRow(
            JPanel parent,
            int row,
            String labelText,
            String initial,
            Consumer<String> setter,
            boolean password) {
        JLabel rowLabel = new JLabel(labelText);
        JComponent field;
        if (password) {
            JPasswordField pf = new JPasswordField(initial, 40);
            field = pf;
            pf.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    setter.accept(new String(pf.getPassword()).trim());
                }
            });
        } else {
            JTextField tf = new JTextField(initial, 40);
            field = tf;
            tf.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    setter.accept(tf.getText().trim());
                }
            });
        }

        JPanel rowPanel = new JPanel(new GridBagLayout());
        rowPanel.setOpaque(false);
        rowPanel.setBorder(BorderFactory.createEmptyBorder(
                LLM_ROW_PADDING,
                LLM_ROW_PADDING,
                LLM_ROW_PADDING,
                LLM_ROW_PADDING));

        Insets labelInsets = new Insets(0, 0, 0, 8);
        Insets fieldInsets = new Insets(0, 0, 0, 0);

        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.gridx = 0;
        labelGbc.gridy = 0;
        labelGbc.anchor = GridBagConstraints.WEST;
        labelGbc.fill = GridBagConstraints.NONE;
        labelGbc.weightx = 0;
        labelGbc.insets = labelInsets;

        GridBagConstraints fieldGbc = new GridBagConstraints();
        fieldGbc.gridx = 1;
        fieldGbc.gridy = 0;
        fieldGbc.anchor = GridBagConstraints.WEST;
        fieldGbc.fill = GridBagConstraints.HORIZONTAL;
        fieldGbc.weightx = 1;
        fieldGbc.insets = fieldInsets;

        rowPanel.add(rowLabel, labelGbc);
        rowPanel.add(field, fieldGbc);

        GridBagConstraints rowGbc = new GridBagConstraints();
        rowGbc.gridx = 0;
        rowGbc.gridy = row;
        rowGbc.gridwidth = 2;
        rowGbc.anchor = GridBagConstraints.WEST;
        rowGbc.fill = GridBagConstraints.HORIZONTAL;
        rowGbc.weightx = 1;
        rowGbc.insets = new Insets(row > 0 ? ROW_GAP : 0, 0, 0, 0);

        parent.add(rowPanel, rowGbc);
        return row + 1;
    }

    private JComponent createStatusPanel() {
        StatusRegistry registry = Treepeater.getStatusRegistry();

        DefaultListModel<Status> model = new DefaultListModel<>();
        registry.getAll().forEach(model::addElement);

        JList<Status> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new StatusListCellRenderer());
        list.setFixedCellHeight(32);

        // Sync list model when registry changes (e.g. from another panel reload)
        registry.addChangeListener(() -> {
            int selected = list.getSelectedIndex();
            model.clear();
            registry.getAll().forEach(model::addElement);
            if (selected >= 0 && selected < model.size()) {
                list.setSelectedIndex(selected);
            }
        });

        JButton addButton = new JButton("Add");
        JButton editButton = new JButton("Edit");
        JButton deleteButton = new JButton("Delete");
        JButton upButton = new JButton("\u25B2");
        JButton downButton = new JButton("\u25BC");
        JButton saveButton = new JButton("Save as Default");
        JButton loadDefaultButton = new JButton("Load Default");
        JButton resetToDefaultButton = new JButton("Reset to Default");

        editButton.setEnabled(false);
        deleteButton.setEnabled(false);
        upButton.setEnabled(false);
        downButton.setEnabled(false);

        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int idx = list.getSelectedIndex();
            // Don't allow the default status to be changed
            boolean sel = idx > 0;
            editButton.setEnabled(sel);
            deleteButton.setEnabled(sel);
            upButton.setEnabled(sel && idx > 0);
            downButton.setEnabled(sel && idx < model.size() - 1);
        });

        addButton.addActionListener(e -> {
            Status created = StatusEditDialog.showDialog(this.root, null);
            if (created != null) {
                registry.add(created);
            }
        });

        editButton.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx < 0) return;
            Status edited = StatusEditDialog.showDialog(this.root, model.get(idx));
            if (edited != null) {
                registry.update(idx, edited);
                list.setSelectedIndex(idx);
            }
        });

        deleteButton.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx < 0 || model.size() <= 1) return;
            registry.remove(idx);
            int newSel = Math.min(idx, model.size() - 1);
            if (newSel >= 0) list.setSelectedIndex(newSel);
        });

        upButton.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx <= 0) return;
            registry.moveUp(idx);
            list.setSelectedIndex(idx - 1);
        });

        downButton.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx < 0 || idx >= model.size() - 1) return;
            registry.moveDown(idx);
            list.setSelectedIndex(idx + 1);
        });

        loadDefaultButton.addActionListener(e -> {
            registry.clear();

            List<Status> defaultStatuses = this.settings.getDefaultStatuses();

            if (defaultStatuses != null) {
                defaultStatuses.forEach(registry::add);
            } else {
                StatusRegistry.getStandardStatuses().forEach(registry::add);
            }
        });

        saveButton.addActionListener(e -> {
            List<Status> statuses = registry.getAll();
            this.settings.setDefaultStatuses(statuses.subList(1, statuses.size()));
        });

        resetToDefaultButton.addActionListener(e -> {
            registry.clear();
            StatusRegistry.getStandardStatuses().forEach(registry::add);
        });

        // Button toolbar
        JPanel toolbar = new JPanel(new GridBagLayout());
        toolbar.setAlignmentX(Component.LEFT_ALIGNMENT);
        toolbar.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 2;
        gbc.weightx = 0;
        gbc.insets = new Insets(2, 2, 2, 2);
        toolbar.add(addButton, gbc);

        gbc.gridy = 1;
        toolbar.add(editButton, gbc);

        gbc.gridy = 2;
        toolbar.add(deleteButton, gbc);

        gbc.gridy = 3;
        gbc.gridwidth = 1;
        gbc.weightx = 1;
        toolbar.add(upButton, gbc);

        gbc.gridy = 3;
        gbc.gridx = 1;
        toolbar.add(downButton, gbc);

        // Seperate the default buttons from the rest of the toolbar
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.VERTICAL;
        toolbar.add(Box.createVerticalGlue(), gbc);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0;
        toolbar.add(saveButton, gbc);

        gbc.gridy = 6;
        toolbar.add(loadDefaultButton, gbc);

        gbc.gridy = 7;
        toolbar.add(resetToDefaultButton, gbc);

        list.setPreferredSize(new Dimension(240, 200));
        list.setMaximumSize(list.getPreferredSize());
        list.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1));

        // Override getMaximumSize() so BoxLayout never stretches this panel
        // beyond the space its children actually need.
        JPanel panel = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        panel.setLayout(new BoxLayout(panel, BoxLayout.LINE_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setAlignmentY(Component.TOP_ALIGNMENT);
        panel.setOpaque(false);
        toolbar.setAlignmentY(Component.TOP_ALIGNMENT);
        list.setAlignmentY(Component.TOP_ALIGNMENT);
        panel.add(toolbar);
        panel.add(Box.createHorizontalStrut(8));
        panel.add(list);

        return panel;
    }


    private static final class StatusListCellRenderer extends JPanel implements ListCellRenderer<Status> {
        private final JLabel iconLabel = new JLabel();
        private final JLabel nameLabel = new JLabel();
        private final JPanel colorSwatch = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(UIManager.getColor("Component.borderColor") != null
                        ? UIManager.getColor("Component.borderColor") : Color.GRAY);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 4, 4);
            }
        };

        StatusListCellRenderer() {
            setLayout(new BorderLayout(8, 0));
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            iconLabel.setPreferredSize(new Dimension(24, 24));
            iconLabel.setHorizontalAlignment(JLabel.CENTER);
            colorSwatch.setPreferredSize(new Dimension(16, 16));
            colorSwatch.setOpaque(true);

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            left.setOpaque(false);
            left.add(iconLabel);
            left.add(nameLabel);

            add(left, BorderLayout.WEST);
            add(colorSwatch, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends Status> list, Status status, int index,
                boolean isSelected, boolean cellHasFocus) {
            iconLabel.setIcon(status.getIcon().withColor(status.getBorderColor()));
            nameLabel.setText(status.getStatus());
            colorSwatch.setBackground(status.getBackgroundColor());

            if (isSelected) {
                setBackground(list.getSelectionBackground());
                nameLabel.setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                nameLabel.setForeground(list.getForeground());
            }
            setOpaque(true);
            return this;
        }
    }

    @Override
    public JComponent uiComponent() {
        return root;
    }

    @Override
    public Set<String> keywords() {
        return Set.of(
                "Treepeater",
                "hotkey",
                "shortcut",
                "keyboard",
                "repeater",
                "import",
                "lenient",
                "grouping",
                "path-aware",
                "LLM",
                "Ollama",
                "Anthropic",
                "AI",
                "model",
                "API",
                "MCP");
    }

    @Override
    public String getString(String name) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getString'");
    }

    @Override
    public boolean getBoolean(String name) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getBoolean'");
    }

    @Override
    public int getInteger(String name) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getInteger'");
    }
}
