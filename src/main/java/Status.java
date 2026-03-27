import java.awt.Color;
import java.awt.Image;

import javax.swing.UIManager;
import javax.swing.ImageIcon;

public class Status {
    public static final Status TODO = new Status("TODO", UIManager.getColor("Actions.Yellow"), new ImageIcon(Status.class.getResource("/icons/pending.png")));
    public static final Status WORKING_ON_IT = new Status("Working on it", UIManager.getColor("Actions.Blue"), new ImageIcon(Status.class.getResource("/icons/tools.png")));
    public static final Status DONE = new Status("Done", UIManager.getColor("Actions.Green"), new ImageIcon(Status.class.getResource("/icons/check.png")));
    public static final Status COLLECTION = new Status("Collection", UIManager.getColor("Actions.Red"), new ImageIcon(Status.class.getResource("/icons/list.png")));

    private final String statusName;
    private final Color color;
    private final ImageIcon icon;

    public Status(String statusName, Color color, ImageIcon icon) {
        this.statusName = statusName;
        this.color = color;
        this.icon = icon;
    }

    public String getStatus() {
        return this.statusName;
    }

    public Color getColor() {
        return this.color;
    }

    public ImageIcon getIcon() {
        return this.icon;
    }
}