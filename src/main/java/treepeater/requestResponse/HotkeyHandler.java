package treepeater.requestResponse;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Optional;
import java.awt.KeyEventDispatcher;

import javax.swing.KeyStroke;

/**
 * Configurable shortcuts for {@link RequestResponsePanel}. Maps {@code actionId} to the
 * {@link KeyStroke} that should trigger it. Load or mutate via {@link #addBinding},
 * {@link #changeBinding}, and {@link #removeBinding}; {@link #withDefaults()} matches the
 * former static default list.
 */
public class HotkeyHandler implements KeyEventDispatcher {

    public record Hotkey(KeyStroke keyStroke, Runnable action) {
        
    }

    public record Binding(String actionId, Hotkey hotkey) {
    }

    private final HashMap<String, Hotkey> bindings = new HashMap<>();

    public HotkeyHandler() {
    }

    /**
     * @return {@code true} if the binding was added; {@code false} if {@code actionId} was already present
     */
    public boolean addBinding(String actionId, KeyStroke keyStroke, Runnable action) {
        return bindings.putIfAbsent(actionId, new Hotkey(keyStroke, action)) == null;
    }

    /**
     * @return {@code true} if the binding existed and was updated
     */
    public boolean changeBinding(String actionId, KeyStroke keyStroke, Runnable action) {
        return bindings.replace(actionId, new Hotkey(keyStroke, action)) != null;
    }

    public boolean changeBinding(String actionId, KeyStroke keystroke) {
        if (!bindings.containsKey(actionId)) {
            return false;
        }
        return changeBinding(actionId, keystroke, bindings.get(actionId).action);
    }

    public boolean changeBinding(String actionId, Runnable action) {
        if (!bindings.containsKey(actionId)) {
            return false;
        }
        return changeBinding(actionId, bindings.get(actionId).keyStroke(), action);
    }

    /**
     * @return the removed {@link KeyStroke}, or {@code null} if {@code actionId} was not bound
     */
    public Hotkey removeBinding(String actionId) {
        return bindings.remove(actionId);
    }

    /**
     * First entry in map iteration order whose stroke matches the event. With {@link HashMap},
     * iteration order is unspecified; avoid binding multiple {@code actionId}s to the same chord.
     */
    public Optional<Binding> findFirstMatching(KeyEvent e) {
        if (e == null) {
            return Optional.empty();
        }

        for (var entry : bindings.entrySet()) {
            if (keyEventMatchesStroke(e, entry.getValue().keyStroke())) {
                return Optional.of(new Binding(entry.getKey(), entry.getValue()));
            }
        }
        return Optional.empty();
    }

    /**
     * Matches Burp-style {@link KeyStroke}s against the dispatch stream. Handles
     * {@link KeyEvent#KEY_TYPED} for Ctrl+Space (and similar) where {@code keyCode} is
     * undefined but {@code keyChar} is set.
     */
    public static boolean keyEventMatchesStroke(KeyEvent e, KeyStroke stroke) {
        if (e.getID() != KeyEvent.KEY_PRESSED && e.getID() != KeyEvent.KEY_TYPED) {
            return false;
        }
        int modMask = InputEvent.SHIFT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK
                | InputEvent.ALT_DOWN_MASK | InputEvent.ALT_GRAPH_DOWN_MASK;
        if ((e.getModifiersEx() & modMask) != (stroke.getModifiers() & modMask)) {
            return false;
        }
        int code = stroke.getKeyCode();
        if (e.getID() == KeyEvent.KEY_PRESSED) {
            return e.getKeyCode() == code;
        }
        if (code == KeyEvent.VK_SPACE) {
            return e.getKeyChar() == ' ';
        }
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        Optional<Binding> matched = findFirstMatching(e);
        if (matched.isEmpty()) {
            return false;
        }
        Binding binding = matched.get();
        binding.hotkey().action().run();
        e.consume();
        return true;
    }
}
