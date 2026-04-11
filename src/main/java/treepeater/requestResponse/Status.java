package treepeater.requestResponse;

import java.awt.Color;
import java.util.Optional;

import javax.swing.UIManager;

import burp.api.montoya.ui.Theme;
import treepeater.Treepeater;
import treepeater.components.SvgIcon;

public class Status {
    private final String id;
    private final String statusName;
    private final Optional<StatusColors> colors;
    private final Optional<StatusNamedColors> namedColors;
    private final String svgContent;
    private final SvgIcon icon;

    public record StatusColors(Color backgroundColor, Color borderColor, Color backgroundDarkModeColor, Color borderColorDarkModeColor) {}
    public record StatusNamedColors(String backgroundColorKey, String borderColorKey, String backgroundDarkModeColorKey, String borderColorDarkModeColorKey) {}

    public Status(String id, String statusName, StatusColors colors, String svgContent) {
        Treepeater.api.logging().logToOutput("Creating status with color values: " + colors);
        this.id = id;
        this.statusName = statusName;
        this.colors = Optional.of(colors);
        this.namedColors = Optional.empty();
        this.svgContent = svgContent;
        this.icon = SvgIcon.fromContent(svgContent).withSize(18, 18).withColor(colors.borderColor());
    }

    public Status(String id, String statusName, StatusNamedColors colors, String svgContent) {
        Treepeater.api.logging().logToOutput("Creating status with named color values: " + colors);
        this.id = id;
        this.statusName = statusName;
        this.namedColors = Optional.of(colors);
        this.colors = Optional.empty();
        this.svgContent = svgContent;
        this.icon = SvgIcon.fromContent(svgContent).withSize(18, 18);
    }

    public String getId() {
        return this.id;
    }

    public String getStatus() {
        return this.statusName;
    }

    public SvgIcon getIcon() {
        return this.icon;
    }

    public Color getBackgroundColor() {
        if (Treepeater.api.userInterface().currentTheme() == Theme.DARK) {
            return this.colors.map(StatusColors::backgroundDarkModeColor).orElse(UIManager.getColor(this.namedColors.map(StatusNamedColors::backgroundDarkModeColorKey).orElse("")));
        }

        return this.colors.map(StatusColors::backgroundColor).orElse(UIManager.getColor(this.namedColors.map(StatusNamedColors::backgroundColorKey).orElse("")));
    }

    public Color getBorderColor() {
        if (Treepeater.api.userInterface().currentTheme() == Theme.DARK) {
            return this.colors.map(StatusColors::borderColorDarkModeColor).orElse(UIManager.getColor(this.namedColors.map(StatusNamedColors::borderColorDarkModeColorKey).orElse("")));
        }

        return this.colors.map(StatusColors::borderColor).orElse(UIManager.getColor(this.namedColors.map(StatusNamedColors::borderColorKey).orElse("")));
    }

    public String getSvgContent() {
        return this.svgContent;
    }

    public Optional<StatusNamedColors> getNamedColors() {
        return this.namedColors;
    }

    public Optional<StatusColors> getColors() {
        return this.colors;
    }

}
