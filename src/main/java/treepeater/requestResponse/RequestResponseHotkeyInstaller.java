package treepeater.requestResponse;

import java.awt.KeyboardFocusManager;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import treepeater.settings.HotkeyCaptureDialog;
import treepeater.settings.TreepeaterSettings;

/**
 * Registers {@link HotkeyHandler} bindings from settings and attaches / detaches the dispatcher
 * when the panel is shown or hidden in the component hierarchy.
 */
final class RequestResponseHotkeyInstaller {

    private RequestResponseHotkeyInstaller() {
    }

    static void install(JComponent host, HotkeyHandler hotkeyHandler, Map<String, Runnable> hotkeyActions, AtomicBoolean hotkeyHandlerRegistered) {
        TreepeaterSettings settings = TreepeaterSettings.getInstance();
        for (Map.Entry<String, Runnable> entry : hotkeyActions.entrySet()) {
            KeyStroke ks = HotkeyCaptureDialog.parseBurpHotkeyToKeyStroke(settings.getStringWithDefault(entry.getKey()));
            if (ks != null) {
                hotkeyHandler.addBinding(entry.getKey(), ks, entry.getValue());
            }
        }

        settings.addListener((key, value) -> {
            if (!hotkeyActions.containsKey(key) || !(value instanceof String newHotkey)) {
                return;
            }
            KeyStroke newKs = HotkeyCaptureDialog.parseBurpHotkeyToKeyStroke(newHotkey);
            if (newKs != null) {
                hotkeyHandler.changeBinding(key, newKs);
            }
        });

        host.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                if (hotkeyHandlerRegistered.get()) {
                    return;
                }
                KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(hotkeyHandler);
                hotkeyHandlerRegistered.set(true);
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
                if (!hotkeyHandlerRegistered.get()) {
                    return;
                }
                KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(hotkeyHandler);
                hotkeyHandlerRegistered.set(false);
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {
            }
        });
    }
}
