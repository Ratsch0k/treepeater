package treepeater.workspace;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Installs focus and mouse-press listeners on a component tree so the workspace can track which tab
 * group is active when the user interacts with panel content rather than the tab strip.
 */
public final class WorkspaceFocus {
    private WorkspaceFocus() {}

    public static void install(Component root, Runnable onFocused) {
        FocusAdapter focusAdapter =
                new FocusAdapter() {
                    @Override
                    public void focusGained(FocusEvent e) {
                        onFocused.run();
                    }
                };
        MouseAdapter mouseAdapter =
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        onFocused.run();
                    }
                };
        installInteractionTracking(root, focusAdapter, mouseAdapter);
    }

    private static void installInteractionTracking(
            Component root, FocusAdapter focusAdapter, MouseAdapter mouseAdapter) {
        root.addFocusListener(focusAdapter);
        root.addMouseListener(mouseAdapter);
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                installInteractionTracking(child, focusAdapter, mouseAdapter);
            }
        }
    }
}
