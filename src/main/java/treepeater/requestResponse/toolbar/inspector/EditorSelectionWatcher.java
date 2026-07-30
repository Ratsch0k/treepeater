package treepeater.requestResponse.toolbar.inspector;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import javax.swing.event.CaretListener;
import javax.swing.text.JTextComponent;

import burp.api.montoya.core.Range;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;

import treepeater.requestResponse.RequestResponsePanel;

/** Watches caret/selection changes in request and response syntax editors. */
final class EditorSelectionWatcher {

    enum Source {
        REQUEST,
        RESPONSE,
        NONE
    }

    interface Listener {
        void onSelectionChanged(Source source, String raw, Range offsets);
    }

    private static final String SYNTAX_TEXT_AREA_NAME = "syntaxTextArea";

    private final List<EditorCaretBinding> editorCaretBindings = new ArrayList<>();
    private RequestResponsePanel activePanel;
    private Listener listener;
    private Source lastSource = Source.NONE;
    private String lastRawSeen;

    void bind(RequestResponsePanel panel, Listener listener) {
        unbind();
        this.activePanel = panel;
        this.listener = listener;
        this.lastSource = Source.NONE;
        this.lastRawSeen = null;

        HttpRequestEditor requestEditor = panel.getRequestEditor();
        if (requestEditor != null) {
            bindSyntaxTextAreaCaretListener(requestEditor.uiComponent(), Source.REQUEST);
        }
        HttpResponseEditor responseEditor = panel.getResponseEditor();
        if (responseEditor != null) {
            bindSyntaxTextAreaCaretListener(responseEditor.uiComponent(), Source.RESPONSE);
        }
    }

    void unbind() {
        for (EditorCaretBinding binding : this.editorCaretBindings) {
            binding.component().removeCaretListener(binding.listener());
        }
        this.editorCaretBindings.clear();
        this.activePanel = null;
        this.listener = null;
        this.lastSource = Source.NONE;
        this.lastRawSeen = null;
    }

    private void bindSyntaxTextAreaCaretListener(Component root, Source source) {
        JTextComponent syntaxTextArea = findSyntaxTextArea(root);
        if (syntaxTextArea == null) {
            return;
        }
        CaretListener caretListener = e -> handleEditorSelection(syntaxTextArea, source);
        syntaxTextArea.addCaretListener(caretListener);
        this.editorCaretBindings.add(new EditorCaretBinding(syntaxTextArea, caretListener));
    }

    private static JTextComponent findSyntaxTextArea(Component root) {
        if (root instanceof JTextComponent textComponent && SYNTAX_TEXT_AREA_NAME.equals(textComponent.getName())) {
            return textComponent;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                JTextComponent found = findSyntaxTextArea(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void handleEditorSelection(JTextComponent textComponent, Source source) {
        if (this.activePanel == null || this.listener == null) {
            return;
        }
        String raw = textComponent.getSelectedText();
        if (raw == null || raw.isEmpty()) {
            // No selection: keep the last inspected value visible so the user can still edit/apply it.
            return;
        }

        Range offsets = resolveSelectionOffsets(textComponent, source);
        if (offsets == null) {
            return;
        }

        if (source == this.lastSource && raw.equals(this.lastRawSeen)) {
            return;
        }

        this.lastSource = source;
        this.lastRawSeen = raw;
        this.listener.onSelectionChanged(source, raw, offsets);
    }

    private Range resolveSelectionOffsets(JTextComponent textComponent, Source source) {
        RequestResponsePanel panel = this.activePanel;
        if (panel == null) {
            return null;
        }
        Selection sel = null;
        if (source == Source.REQUEST) {
            HttpRequestEditor editor = panel.getRequestEditor();
            if (editor != null) {
                sel = editor.selection().orElse(null);
            }
        } else if (source == Source.RESPONSE) {
            HttpResponseEditor editor = panel.getResponseEditor();
            if (editor != null) {
                sel = editor.selection().orElse(null);
            }
        }
        if (sel != null) {
            Range offsets = sel.offsets();
            if (offsets != null) {
                return offsets;
            }
        }
        int start = textComponent.getSelectionStart();
        int end = textComponent.getSelectionEnd();
        if (start < 0 || end < start) {
            return null;
        }
        return Range.range(start, end);
    }

    private record EditorCaretBinding(JTextComponent component, CaretListener listener) {}
}
