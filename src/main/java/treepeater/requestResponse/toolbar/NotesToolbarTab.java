package treepeater.requestResponse.toolbar;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import treepeater.icons.NotesIcon;
import treepeater.tree.RequestTreeNode;


public class NotesToolbarTab {
    private final ToolbarIconButton button;
    private final JPanel content;
    private final JTextArea notesArea;

    public NotesToolbarTab(RequestTreeNode node) {
        this.button = new ToolbarIconButton(new NotesIcon());
        this.content = new JPanel(new BorderLayout());

        this.notesArea = new JTextArea();
        this.notesArea.setText(node.getNotes());
        this.notesArea.setLineWrap(true);
        this.notesArea.setWrapStyleWord(true);

        this.notesArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                node.setNotes(NotesToolbarTab.this.notesArea.getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                node.setNotes(NotesToolbarTab.this.notesArea.getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                node.setNotes(NotesToolbarTab.this.notesArea.getText());
            }
        });

        this.content.add(this.buildContent(), BorderLayout.CENTER);
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
