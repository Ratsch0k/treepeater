import java.awt.Color;

import javax.swing.UIManager;
import javax.swing.ImageIcon;

public class Status {
    public static final Status TODO = new Status("TODO", UIManager.getColor("Colors.ui.groups.2.background"), UIManager.getColor("Colors.ui.groups.2.accent"), new ImageIcon(Status.class.getResource("/icons/pending.png")));
    public static final Status FINDING = new Status("Finding", UIManager.getColor("Colors.ui.groups.1.background"), UIManager.getColor("Colors.ui.groups.1.accent"), new ImageIcon(Status.class.getResource("/icons/tools.png")));
    public static final Status DONE = new Status("Done", UIManager.getColor("Colors.ui.groups.3.background"), UIManager.getColor("Colors.ui.groups.3.accent"), new ImageIcon(Status.class.getResource("/icons/check.png")));
    public static final Status COLLECTION = new Status("Collection", UIManager.getColor("Colors.ui.groups.8.background"), UIManager.getColor("Colors.ui.groups.8.accent"), new ImageIcon(Status.class.getResource("/icons/list.png")));

    private final String statusName;
    private final Color backgroundColor;
    private final Color borderColor;
    private final ImageIcon icon;

    public Status(String statusName, Color backgroundColor, Color borderColor, ImageIcon icon) {
        this.statusName = statusName;
        this.backgroundColor = backgroundColor;
        this.borderColor = borderColor;
        this.icon = icon;
    }

    public String getStatus() {
        return this.statusName;
    }

    public Color getBackgroundColor() {
        return this.backgroundColor;
    }

    public ImageIcon getIcon() {
        return this.icon;
    }

    public Color getBorderColor() {
        return this.borderColor;
    }
}