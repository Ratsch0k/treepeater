package treepeater.workspace;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import treepeater.requestResponse.RequestResponsePanel;

/**
 * Tracks which tab group is active when the user interacts with panel content rather than the tab
 * strip. Focus is tracked with per-component focus listeners; mouse presses are observed through a
 * single global {@link AWTEventListener} so that no mouse listeners are added to (Burp-provided)
 * child components, which would otherwise swallow their clicks.
 */
public final class WorkspaceFocus {
    private static AWTEventListener mouseTrackingListener;

    private WorkspaceFocus() {}

    public static void install(Component root, Runnable onFocused) {
        FocusAdapter focusAdapter =
                new FocusAdapter() {
                    @Override
                    public void focusGained(FocusEvent e) {
                        onFocused.run();
                    }
                };
        installFocusTracking(root, focusAdapter);
    }

    private static void installFocusTracking(Component root, FocusAdapter focusAdapter) {
        root.addFocusListener(focusAdapter);
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                installFocusTracking(child, focusAdapter);
            }
        }
    }

    /**
     * Observes mouse presses app-wide and reports the {@link RequestResponsePanel} under {@code
     * workspaceRoot} that received the press. Replaces any previously installed tracking listener.
     */
    public static synchronized void installMouseTracking(
            Component workspaceRoot, Consumer<RequestResponsePanel> onPressed) {
        uninstallMouseTracking();
        AWTEventListener listener =
                event -> {
                    if (event.getID() != MouseEvent.MOUSE_PRESSED
                            || !(event.getSource() instanceof Component source)) {
                        return;
                    }
                    Component panel =
                            source instanceof RequestResponsePanel
                                    ? source
                                    : SwingUtilities.getAncestorOfClass(RequestResponsePanel.class, source);
                    if (panel instanceof RequestResponsePanel rrPanel
                            && SwingUtilities.isDescendingFrom(rrPanel, workspaceRoot)) {
                        onPressed.accept(rrPanel);
                    }
                };
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK);
        mouseTrackingListener = listener;
    }

    public static synchronized void uninstallMouseTracking() {
        if (mouseTrackingListener != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(mouseTrackingListener);
            mouseTrackingListener = null;
        }
    }
}
