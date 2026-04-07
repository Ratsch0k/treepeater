package treepeater.settings;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.UIManager;

import burp.api.montoya.ui.settings.SettingsPanelWithData;

/**
 * Burp settings UI for Treepeater: wraps the Montoya-built settings row and adds shortcut capture.
 */
public final class TreepeaterSettingsPanel implements SettingsPanelWithData {
    private static final int ROW_GAP = 10;
    private static final int SECTION_GAP = 20;
    private static final int INNER_SECTION_GAP = 16;

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

        this.root.add(this.createTitledSection("Status", "Configure available statuses for Treepeater tabs", new JPanel()));
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

    @Override
    public JComponent uiComponent() {
        return root;
    }

    @Override
    public Set<String> keywords() {
        return Set.of("Treepeater", "hotkey", "shortcut", "keyboard", "repeater");
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
