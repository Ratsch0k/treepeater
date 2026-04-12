package treepeater.requestResponse;

import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.HttpRequestEditor;

import treepeater.tree.RequestTreeNode;

/**
 * Back/forward history navigation and dropdown menus for {@link RequestResponsePanel}.
 */
final class RequestHistoryNavigator {
    private final RequestTreeNode node;
    private final HttpRequestEditor requestEditor;
    private final JButton historyBackButton;
    private final JButton historyBackDropButton;
    private final JButton historyForwardButton;
    private final JButton historyForwardDropButton;
    private final Consumer<HttpResponse> setResponse;
    private final Runnable afterHistoryEntryApplied;

    RequestHistoryNavigator(
            RequestTreeNode node,
            HttpRequestEditor requestEditor,
            JButton historyBackButton,
            JButton historyBackDropButton,
            JButton historyForwardButton,
            JButton historyForwardDropButton,
            Consumer<HttpResponse> setResponse,
            Runnable afterHistoryEntryApplied) {
        this.node = node;
        this.requestEditor = requestEditor;
        this.historyBackButton = historyBackButton;
        this.historyBackDropButton = historyBackDropButton;
        this.historyForwardButton = historyForwardButton;
        this.historyForwardDropButton = historyForwardDropButton;
        this.setResponse = setResponse;
        this.afterHistoryEntryApplied = afterHistoryEntryApplied;
    }

    void refreshNavState() {
        RequestHistory h = this.node.getHistory();
        boolean hasHistory = !h.isEmpty();
        int cur = h.getCurrentIndex();
        boolean canBack = hasHistory && cur > 0;
        boolean canForward = hasHistory && cur >= 0 && cur < (h.size() - 1);

        this.historyBackButton.setEnabled(canBack);
        this.historyBackDropButton.setEnabled(canBack);
        this.historyForwardButton.setEnabled(canForward);
        this.historyForwardDropButton.setEnabled(canForward);
    }

    void navigateBack() {
        RequestHistory h = this.node.getHistory();
        if (h.getCurrentIndex() <= 0) {
            return;
        }

        h.navigateBack();
        applyHistoryIndex();
    }

    void navigateForward() {
        RequestHistory h = this.node.getHistory();

        if (h.getCurrentIndex() >= h.size() - 1) {
            return;
        }

        h.navigateForward();
        applyHistoryIndex();
    }

    void showHistoryMenu(int direction, JButton anchor) {
        RequestHistory h = this.node.getHistory();
        if (h.isEmpty() || h.getCurrentIndex() < 0) {
            return;
        }

        JPopupMenu menu = new JPopupMenu();
        int current = h.getCurrentIndex();

        if (direction < 0) {
            for (int i = current - 1; i >= 0; i--) {
                HistoryEntry entry = h.getEntry(i);
                JMenuItem item = new JMenuItem(entry.toString());
                final int idx = i;
                item.addActionListener(e -> setHistoryIndex(idx));
                menu.add(item);
            }
        } else {
            for (int i = current + 1; i < h.size(); i++) {
                HistoryEntry entry = h.getEntry(i);
                JMenuItem item = new JMenuItem(entry.toString());
                final int idx = i;
                item.addActionListener(e -> setHistoryIndex(idx));
                menu.add(item);
            }
        }

        if (menu.getComponentCount() == 0) {
            return;
        }

        menu.show(anchor, 0, anchor.getHeight());
    }

    void setHistoryIndex(int index) {
        RequestHistory h = this.node.getHistory();

        h.setCurrentIndex(index);
        applyHistoryIndex();
    }

    private void applyHistoryIndex() {
        RequestHistory h = this.node.getHistory();

        HistoryEntry selected = h.getCurrentEntry();

        this.requestEditor.setRequest(selected.request);
        this.node.setRequest(selected.request);
        if (selected.response != null) {
            this.setResponse.accept(selected.response);
        }
        this.afterHistoryEntryApplied.run();
    }
}
