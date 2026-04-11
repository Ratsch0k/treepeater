package treepeater.requestResponse;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.UIManager;

import treepeater.Treepeater;
import treepeater.icons.DoubleArrowLeftIcon;
import treepeater.icons.NotesIcon;

/**
 * Narrow vertical strip of actions to the right of the request/response editors.
 */
final class RequestResponseSideToolbar extends JPanel {

    private final JButton expandButton;
    private final JButton notesButton;

    RequestResponseSideToolbar() {
        super(new BorderLayout());
        setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 1, 0, 0, RequestResponsePanelUi.uiBorderColor()),
                        BorderFactory.createEmptyBorder(8, 6, 8, 6)));

        this.expandButton = new JButton(new DoubleArrowLeftIcon().withSize(24, 24).withColor(UIManager.getColor("Label.foreground")));
        this.notesButton = new JButton(new NotesIcon().withSize(24, 24).withColor(UIManager.getColor("Label.foreground")));
        RequestResponsePanelUi.styleAsFlatButton(this.expandButton);
        RequestResponsePanelUi.styleAsFlatButton(this.notesButton);
        RequestResponsePanelUi.installHoverBackground(this.expandButton);
        RequestResponsePanelUi.installHoverBackground(this.notesButton);

        this.expandButton.setFont(this.expandButton.getFont().deriveFont(this.expandButton.getFont().getSize2D() - 1f));
        this.notesButton.setFont(this.notesButton.getFont().deriveFont(this.notesButton.getFont().getSize2D() - 1f));

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        this.expandButton.setAlignmentX(CENTER_ALIGNMENT);
        this.notesButton.setAlignmentX(CENTER_ALIGNMENT);
        column.add(this.expandButton);
        column.add(Box.createVerticalStrut(6));
        column.add(this.notesButton);
        add(column, BorderLayout.NORTH);

        if (Treepeater.api != null) {
            Treepeater.api.userInterface().applyThemeToComponent(this);
        }
        applyLocalTheme();
    }

    JButton getExpandButton() {
        return this.expandButton;
    }

    JButton getNotesButton() {
        return this.notesButton;
    }

    void applyLocalTheme() {
        if (this.notesButton != null) {
            this.notesButton.setIcon(new NotesIcon().withSize(24, 24).withColor(UIManager.getColor("Label.foreground")));
            RequestResponsePanelUi.restyleFlatToolbarButton(this.notesButton);
            RequestResponsePanelUi.installHoverBackground(this.notesButton);
        }
        if (this.expandButton != null) {
            this.expandButton.setIcon(new DoubleArrowLeftIcon().withSize(24, 24).withColor(UIManager.getColor("Label.foreground")));
            RequestResponsePanelUi.restyleFlatToolbarButton(this.expandButton);
            RequestResponsePanelUi.installHoverBackground(this.expandButton);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        int w = Math.max(44, d.width);
        return new Dimension(w, d.height);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 1, 0, 0, RequestResponsePanelUi.uiBorderColor()),
                        BorderFactory.createEmptyBorder(8, 6, 8, 6)));
        applyLocalTheme();
    }
}
