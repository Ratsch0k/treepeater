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
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;

import burp.api.montoya.ui.settings.SettingsPanelWithData;
import treepeater.Treepeater;
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

        JPanel llmPanel = this.createTitledSection(
            "LLMs",
            "Configure connection details for Ollama, Anthropic, and Azure OpenAI / Microsoft Foundry. "
                + "The AI tab reads these values from here; pick the provider and model (or deployment name) in the AI toolbar.",
            this.createLlmSettingsPanel()
        );
        llmPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, SECTION_GAP, 0));
        this.root.add(llmPanel);


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
        this.addHotkeySetting(root,row, "Next request tab hotkey:", this.settings::getTabNextHotkey, this.settings::setTabNextHotkey);
    
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

    private JComponent createLlmSettingsPanel() {
        JPanel outer = new JPanel();
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
        outer.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel ollama = new JPanel(new GridBagLayout());
        ollama.setAlignmentX(Component.LEFT_ALIGNMENT);
        ollama.setBorder(BorderFactory.createTitledBorder("Ollama"));
        int row = 0;
        row = this.addPersistedTextRow(
                ollama,
                row,
                "Base URL:",
                this.settings.getLlmOllamaBaseUrl(),
                this.settings::setLlmOllamaBaseUrl,
                false);

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
                "LLM",
                "Ollama",
                "Anthropic",
                "AI",
                "model",
                "API");
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
