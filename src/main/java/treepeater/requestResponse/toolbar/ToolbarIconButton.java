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
        RequestResponsePanelUi.styleAsFlatButton(this);
        RequestResponsePanelUi.installHoverBackground(this);
        this.setFont(this.getFont().deriveFont(this.getFont().getSize2D() - 1f));
        this.setAlignmentX(CENTER_ALIGNMENT);
        this.setIcon(this.icon.withSize(24, 24).withColor(UIManager.getColor("Label.foreground")));
    }
}
