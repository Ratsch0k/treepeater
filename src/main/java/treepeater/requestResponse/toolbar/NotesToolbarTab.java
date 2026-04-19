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
    private final RequestTreeNode notesTarget;

    public NotesToolbarTab(RequestTreeNode node) {
        this.button = new ToolbarIconButton(new NotesIcon());
        this.content = new JPanel(new BorderLayout());

        this.notesArea = new JTextArea();
        this.notesArea.setLineWrap(true);
        this.notesArea.setWrapStyleWord(true);

        this.content.add(this.buildContent(), BorderLayout.CENTER);

        this.notesTarget = node;
        this.notesArea.setText(node.getNotes());
        this.notesArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                syncNotesToTarget();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                syncNotesToTarget();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                syncNotesToTarget();
            }
        });
    }

    private void syncNotesToTarget() {
        this.notesTarget.setNotes(this.notesArea.getText());
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
