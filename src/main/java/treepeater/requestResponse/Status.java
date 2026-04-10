package treepeater.requestResponse;

import java.awt.Color;
import java.util.Optional;

import javax.swing.UIManager;

import treepeater.components.SvgIcon;

public class Status {
    private final String id;
    private final String statusName;
    private final Optional<StatusColors> colors;
    private final Optional<StatusKeyedColors> keyedColors;
    private final String svgContent;
    private final SvgIcon icon;

    public record StatusColors(Color backgroundColor, Color borderColor, Color backgroundDarkModeColor, Color borderColorDarkModeColor) {}
    public record StatusKeyedColors(String backgroundColorKey, String borderColorKey, String backgroundDarkModeColorKey, String borderColorDarkModeColorKey) {}

    public Status(String id, String statusName, StatusColors colors, String svgContent) {
        this.id = id;
        this.statusName = statusName;
        this.colors = Optional.of(colors);
        this.keyedColors = Optional.empty();
        this.svgContent = svgContent;
        this.icon = SvgIcon.fromContent(svgContent).withSize(18, 18).withColor(colors.borderColor());
    }

    public Status(String id, String statusName, StatusKeyedColors colors, String svgContent) {
        this.id = id;
        this.statusName = statusName;
        this.keyedColors = Optional.of(colors);
        this.colors = Optional.empty();
        this.svgContent = svgContent;
        this.icon = SvgIcon.fromContent(svgContent).withSize(18, 18).withColor(UIManager.getColor(colors.borderColorKey()));
    }

    public String getId() {
        return this.id;
    }

    public String getStatus() {
        return this.statusName;
    }

    public Color getBackgroundColor() {
        return this.colors.map(StatusColors::backgroundColor).orElse(UIManager.getColor(this.keyedColors.map(StatusKeyedColors::backgroundColorKey).orElse("")));
    }

    public SvgIcon getIcon() {
        return this.icon;
    }

    public Color getBorderColor() {
        return this.colors.map(StatusColors::borderColor).orElse(UIManager.getColor(this.keyedColors.map(StatusKeyedColors::borderColorKey).orElse("")));
    }

    public String getSvgContent() {
        return this.svgContent;
    }

    public Optional<StatusKeyedColors> getKeyedColors() {
        return this.keyedColors;
    }

    public Optional<StatusColors> getColors() {
        return this.colors;
    }

}
