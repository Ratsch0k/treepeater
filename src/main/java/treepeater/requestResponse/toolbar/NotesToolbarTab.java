package treepeater.requestResponse.toolbar;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import treepeater.TreepeaterModel;
import treepeater.icons.NotesIcon;

public class NotesToolbarTab {
    private final ToolbarIconButton button;
    private final JPanel content;
    private final JTextArea notesArea;

    public NotesToolbarTab(TreepeaterModel model) {
        this.button = new ToolbarIconButton(new NotesIcon());
        this.content = new JPanel(new BorderLayout());

        this.notesArea = new JTextArea();
        this.notesArea.setText(model.getGlobalNotes());
        this.notesArea.setLineWrap(true);
        this.notesArea.setWrapStyleWord(true);

        this.content.add(this.buildContent(), BorderLayout.CENTER);

        this.notesArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                model.setGlobalNotes(NotesToolbarTab.this.notesArea.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                model.setGlobalNotes(NotesToolbarTab.this.notesArea.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                model.setGlobalNotes(NotesToolbarTab.this.notesArea.getText());
            }
        });
    }

    public JButton getButton() {
        return this.button;
    }

    public JPanel getContent() {
        return this.content;
    }

    public void applyLocalTheme() {
        this.button.applyLocalTheme();
    }

    private JPanel buildContent() {
        JPanel panel = new JPanel(new BorderLayout());

        panel.add(new ToolbarTabTitle("Notes"), BorderLayout.NORTH);
        JScrollPane scrollPane = new JScrollPane(this.notesArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }
}
