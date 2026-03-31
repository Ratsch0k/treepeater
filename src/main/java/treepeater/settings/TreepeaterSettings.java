package treepeater.settings;
import java.util.ArrayList;
import java.util.List;

import burp.api.montoya.persistence.Preferences;

public class TreepeaterSettings {
    private static TreepeaterSettings instance = null;

    private final Preferences preferences;

    private final List<TreepeaterSerttingsChangeListener> listeners = new ArrayList<>();

    public static final String SEND_HOTKEY_SETTING = "TREEPEATER_SEND_HOTKEY";
    private static final String DEFAULT_SEND_HOTKEY = "Ctrl+Alt+Shift+T";

    private TreepeaterSettings(Preferences preferences) {
        this.preferences = preferences;
    }

    public static void init(Preferences preferences) {
        if (TreepeaterSettings.instance != null) return;

        TreepeaterSettings.instance = new TreepeaterSettings(preferences);
    }

    public static TreepeaterSettings getInstance() {
        if (TreepeaterSettings.instance == null) {
            throw new IllegalStateException("TreepeaterSettings not initialized");
        }
        return TreepeaterSettings.instance;
    }

    public String getSendHotkey() {
        String hotkey = this.preferences.getString(SEND_HOTKEY_SETTING);
        return hotkey != null ? hotkey : DEFAULT_SEND_HOTKEY;
    }

    public void setSendHotkey(String hotkey) {
        this.preferences.setString(SEND_HOTKEY_SETTING, hotkey);
        this.notifyListeners(SEND_HOTKEY_SETTING);
    }

    public void addListener(TreepeaterSerttingsChangeListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(TreepeaterSerttingsChangeListener listener) {
        this.listeners.remove(listener);
    }

    private void notifyListeners(String key) {
        this.listeners.forEach(l -> l.onChange(key));
    }
}
