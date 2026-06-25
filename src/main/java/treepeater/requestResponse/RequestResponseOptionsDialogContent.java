package treepeater.requestResponse;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.UIManager;

import treepeater.Treepeater;

public class RequestResponseOptionsDialogContent extends JPanel {
    private final JCheckBox updateContentLength;

    public RequestResponseOptionsDialogContent(RequestResponseOptions current) {
        super(new GridBagLayout());

        RequestResponseOptions options = current != null ? current : RequestResponseOptions.DEFAULT;

        this.updateContentLength = new JCheckBox("Update Content-Length", options.updateContentLength());
        this.updateContentLength.setToolTipText(
                "When enabled, Content-Length is set from the request body before sending (omitted for chunked encoding).");

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.gridx = 0;
        gbc.gridy = 0;

        this.add(this.updateContentLength, gbc);
        applyLocalTheme();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyLocalTheme();
    }

    private void applyLocalTheme() {
        if (Treepeater.api != null) {
            Treepeater.api.userInterface().applyThemeToComponent(this);
        }
        Color bg = UIManager.getColor("OptionPane.background");
        if (bg == null) {
            bg = UIManager.getColor("Panel.background");
        }
        setOpaque(true);
        setBackground(bg);
        if (this.updateContentLength != null) {
            this.updateContentLength.setOpaque(false);
            this.updateContentLength.setBackground(bg);
            this.updateContentLength.setContentAreaFilled(false);
        }
    }

    public RequestResponseOptions getOptions() {
        return new RequestResponseOptions(this.updateContentLength.isSelected());
    }
}
