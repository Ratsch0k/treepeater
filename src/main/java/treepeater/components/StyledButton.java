package treepeater.components;

import javax.swing.JButton;

import com.formdev.flatlaf.FlatClientProperties;

public class StyledButton extends JButton {
    public enum Style {
        DEFAULT,
        PRIMARY,
        AI,
        CUSTOM,
    }

    private Style style;

    public StyledButton() {
        this.style = Style.DEFAULT;
    }

    public StyledButton(String text) {
        super(text);
        this.style = Style.DEFAULT;
        applyStyle();
    }

    public void setStyle(Style style) {
        this.style = style;
        applyStyle();
    }

    private void applyStyle() {
        switch (this.style) {
            case DEFAULT:
                this.putClientProperty(FlatClientProperties.STYLE, buildStyleString("default"));
                break;
            case PRIMARY:
                this.putClientProperty(FlatClientProperties.STYLE, buildStyleString("primary"));
                break;
            case AI:
                this.putClientProperty(FlatClientProperties.STYLE, buildStyleString("ai"));
                break;
            case CUSTOM:
                this.putClientProperty(FlatClientProperties.STYLE, buildStyleString("custom"));
                break;
        }
    }

    private String buildStyleString(String style) {
        String formatString = "background: $Button.%1$s.background;"
            + "hoverBackground: $Button.%1$s.hoverBackground;"
            + "pressedBackground: $Button.%1$s.pressedBackground;"
            + "selectedBackground: $Button.%1$s.selectedBackground;"
            + "focusedBackground: $Button.%1$s.focusedBackground;"
            + "disabledBackground: $Button.%1$s.disabledBackground;"
            + "foreground: $Button.%1$s.foreground;"
            + "hoverForeground: $Button.%1$s.hoverForeground;"
            + "disabledText: $Button.%1$s.disabledText;"
            + "pressedForeground: $Button.%1$s.pressedForeground;"
            + "focusedForeground: $Button.%1$s.focusedForeground;";

        if (!style.equals("default")) {
            formatString += "borderColor: $Button.%1$s.background;"
                + "disabledBorderColor: $Button.%1$s.disabledBackground;"
                + "hoverBorderColor: $Button.%1$s.hoverBackground;"
                + "pressedBorderColor: $Button.%1$s.pressedBackground;"
                + "focusedBorderColor: $Button.%1$s.focusedBackground;";
        }
        
        String styleString = String.format(formatString, style);
            
        return styleString;
    }
}
