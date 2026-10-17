package treepeater.requestResponse.toolbar;

import javax.swing.JButton;
import javax.swing.UIManager;

import treepeater.components.SvgIcon;
import treepeater.requestResponse.RequestResponsePanelUi;

public class ToolbarIconButton extends JButton {
    SvgIcon icon;

    public ToolbarIconButton(SvgIcon icon) {
        super(icon);
        this.icon = icon;
    }

    public void applyLocalTheme() {
        applyLocalTheme(24);
    }

    public void applyLocalTheme(int iconSize) {
        RequestResponsePanelUi.styleAsFlatButton(this);
        RequestResponsePanelUi.installHoverBackground(this);
        this.setFont(this.getFont().deriveFont(this.getFont().getSize2D() - 1f));
        this.setAlignmentX(CENTER_ALIGNMENT);
        this.setIcon(this.icon.withSize(iconSize, iconSize).withColor(UIManager.getColor("Label.foreground")));
        // SvgIcon isn't an ImageIcon, so the LAF can't gray it out for us; provide a disabled variant.
        java.awt.Color disabled = UIManager.getColor("Label.disabledForeground");
        if (disabled == null) {
            disabled = java.awt.Color.GRAY;
        }
        this.setDisabledIcon(this.icon.withSize(iconSize, iconSize).withColor(disabled));
    }
}
