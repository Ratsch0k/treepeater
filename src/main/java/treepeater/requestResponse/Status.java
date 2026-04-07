package treepeater.requestResponse;
import java.awt.Color;

import javax.swing.UIManager;

import treepeater.components.SvgIcon;

public class Status {
    public static final Status TODO = new Status("TODO", UIManager.getColor("Colors.ui.groups.2.background"), UIManager.getColor("Colors.ui.groups.2.accent"), new SvgIcon("/icons/hourglass.svg"));
    public static final Status FINDING = new Status("Finding", UIManager.getColor("Colors.ui.groups.1.background"), UIManager.getColor("Colors.ui.groups.1.accent"), new SvgIcon("/icons/warning.svg"));
    public static final Status DONE = new Status("Done", UIManager.getColor("Colors.ui.groups.3.background"), UIManager.getColor("Colors.ui.groups.3.accent"), new SvgIcon("/icons/check.svg"));
    public static final Status COLLECTION = new Status("Collection", UIManager.getColor("Colors.ui.groups.8.background"), UIManager.getColor("Colors.ui.groups.8.accent"), new SvgIcon("/icons/folder.svg"));

    private final String statusName;
    private final Color backgroundColor;
    private final Color borderColor;
    private final SvgIcon icon;

    public Status(String statusName, Color backgroundColor, Color borderColor, SvgIcon icon) {
        this.statusName = statusName;
        this.backgroundColor = backgroundColor;
        this.borderColor = borderColor;
        this.icon = icon.withSize(18, 18).withColor(borderColor);

    }

    public static Status fromName(String statusName) {
        if (statusName == null) {
            return null;
        }
        switch (statusName.toUpperCase()) {
            case "TODO":
                return TODO;
            case "FINDING":
                return FINDING;
            case "DONE":
                return DONE;
            case "COLLECTION":
                return COLLECTION;
            default:
                return null;
        }
    }

    public String getStatus() {
        return this.statusName;
    }

    public Color getBackgroundColor() {
        return this.backgroundColor;
    }

    public SvgIcon getIcon() {
        return this.icon;
    }

    public Color getBorderColor() {
        return this.borderColor;
    }
}