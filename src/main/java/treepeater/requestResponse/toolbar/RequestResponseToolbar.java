package treepeater.requestResponse.toolbar;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.util.List;
import java.util.function.Supplier;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.UIManager;

import treepeater.ai.AgentToolContext;
import treepeater.Treepeater;
import treepeater.TreepeaterModel;
import treepeater.icons.DoubleArrowLeftIcon;
import treepeater.requestResponse.RequestResponsePanelUi;
import treepeater.requestResponse.toolbar.ai.AIToolbarTab;

/**
 * Narrow vertical strip of actions to the right of the request/response editors.
 */
public class RequestResponseToolbar extends JPanel {
    private final InfoToolbarTab infoToolbarTab;
    private final NotesToolbarTab notesToolbarTab;
    private final AIToolbarTab magicToolbarTab;

    private final JButton expandButton;

    private final JPanel toolbarPanel;
    private final CardLayout toolbarCardLayout;

    private boolean toolbarOpen;

    private final List<RequestResponseToolbarListener> toolbarListeners = new CopyOnWriteArrayList<>();

    public RequestResponseToolbar(TreepeaterModel model, Supplier<AgentToolContext> agentToolContextSupplier) {
        super(new BorderLayout());
        setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 1, 0, 0, RequestResponsePanelUi.uiBorderColor()),
                        BorderFactory.createEmptyBorder(8, 6, 8, 6)));


        this.infoToolbarTab = new InfoToolbarTab();
        this.notesToolbarTab = new NotesToolbarTab(model);
        this.magicToolbarTab = new AIToolbarTab(model, agentToolContextSupplier);

        this.toolbarCardLayout = new CardLayout();
        this.toolbarPanel = new JPanel(this.toolbarCardLayout);

        this.toolbarPanel.add(this.infoToolbarTab.getContent(), "info");
        this.toolbarPanel.add(this.notesToolbarTab.getContent(), "notes");
        this.toolbarPanel.add(this.magicToolbarTab.getContent(), "magic");

        this.infoToolbarTab.getButton().addActionListener(e -> {
            if (!this.toolbarOpen) {
                openToolbarToCard("info");
            } else {
                this.toolbarCardLayout.show(this.toolbarPanel, "info");
            }
        });
        this.notesToolbarTab.getButton().addActionListener(e -> {
            if (!this.toolbarOpen) {
                openToolbarToCard("notes");
            } else {
                this.toolbarCardLayout.show(this.toolbarPanel, "notes");
            }
        });
        this.magicToolbarTab.getButton().addActionListener(e -> {
            if (!this.toolbarOpen) {
                openToolbarToCard("magic");
            } else {
                this.toolbarCardLayout.show(this.toolbarPanel, "magic");
            }
        });

        this.expandButton = new JButton(new DoubleArrowLeftIcon().withSize(24, 24).withColor(UIManager.getColor("Label.foreground")));
        RequestResponsePanelUi.styleAsFlatButton(this.expandButton);
       
        RequestResponsePanelUi.installHoverBackground(this.expandButton);

        this.expandButton.setFont(this.expandButton.getFont().deriveFont(this.expandButton.getFont().getSize2D() - 1f));
        this.expandButton.addActionListener(e -> {
            if (!this.toolbarOpen) {
                openToolbarToCard("info");
            } else {
                closeToolbar();
            }
        });

        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        this.expandButton.setAlignmentX(CENTER_ALIGNMENT);
        column.add(this.expandButton);
        column.add(Box.createVerticalStrut(6));
        column.add(this.infoToolbarTab.getButton());
        column.add(Box.createVerticalStrut(6));
        column.add(this.notesToolbarTab.getButton());
        column.add(Box.createVerticalStrut(6));
        column.add(this.magicToolbarTab.getButton());
        add(column, BorderLayout.NORTH);

        if (Treepeater.api != null) {
            Treepeater.api.userInterface().applyThemeToComponent(this);
        }
        applyLocalTheme();
    }

    public void addToolbarListener(RequestResponseToolbarListener listener) {
        if (listener != null) {
            this.toolbarListeners.add(listener);
        }
    }

    public void removeToolbarListener(RequestResponseToolbarListener listener) {
        this.toolbarListeners.remove(listener);
    }

    public boolean isToolbarOpen() {
        return this.toolbarOpen;
    }

    private void openToolbarToCard(String cardName) {
        boolean wasClosed = !this.toolbarOpen;
        this.toolbarOpen = true;
        this.toolbarCardLayout.show(this.toolbarPanel, cardName);
        if (wasClosed) {
            notifyToolbarOpen();
        }
    }

    private void closeToolbar() {
        if (!this.toolbarOpen) {
            return;
        }
        this.toolbarOpen = false;
        notifyToolbarClose();
    }

    private void notifyToolbarOpen() {
        for (RequestResponseToolbarListener listener : this.toolbarListeners) {
            listener.onToolbarOpen();
        }
    }

    private void notifyToolbarClose() {
        for (RequestResponseToolbarListener listener : this.toolbarListeners) {
            listener.onToolbarClose();
        }
    }

    public JButton getExpandButton() {
        return this.expandButton;
    }

    public JButton getInfoButton() {
        return this.infoToolbarTab.getButton();
    }

    public InfoToolbarTab getInfoToolbarTab() {
        return this.infoToolbarTab;
    }

    public NotesToolbarTab getNotesToolbarTab() {
        return this.notesToolbarTab;
    }

    public JButton getNotesButton() {
        return this.notesToolbarTab.getButton();
    }

    public AIToolbarTab getMagicToolbarTab() {
        return this.magicToolbarTab;
    }

    public JButton getMagicButton() {
        return this.magicToolbarTab.getButton();
    }

    public void applyLocalTheme() {
        if (this.infoToolbarTab != null) {
            this.infoToolbarTab.applyLocalTheme();
        }
        if (this.notesToolbarTab != null) {
            this.notesToolbarTab.applyLocalTheme();
        }
        if (this.magicToolbarTab != null) {
            this.magicToolbarTab.applyLocalTheme();
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
        if (this.infoToolbarTab != null) {
            this.infoToolbarTab.updateUI();
        }
    }

    public JPanel getToolbarPanel() {
        return this.toolbarPanel;
    }
}
