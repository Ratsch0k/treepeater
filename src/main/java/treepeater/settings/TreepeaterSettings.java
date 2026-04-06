package treepeater.settings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import burp.api.montoya.persistence.Preferences;

public class TreepeaterSettings {
    private static TreepeaterSettings instance = null;

    private final Preferences preferences;

    private final List<TreepeaterSerttingsChangeListener> listeners = new ArrayList<>();

    public static final String SEND_HOTKEY_SETTING = "TREEPEATER_SEND_HOTKEY";
    public static final String SEND_REQUEST_HOTKEY_SETTING = "SEND_REQUEST_HOTKEY";
    public static final String HISTORY_BACK_HOTKEY_SETTING = "HISTORY_BACK_HOTKEY";
    public static final String HISTORY_FORWARD_HOTKEY_SETTING = "HISTORY_FORWARD_HOTKEY";
    public static final String COPY_SAME_PARENT_REQUEST_HOTKEY_SETTING =
            "COPY_SAME_PARENT_REQUEST_HOTKEY";
    public static final String RENAME_HOTKEY_SETTING = "RENAME_HOTKEY";
    public static final String CHANGE_STATUS_HOTKEY_SETTING = "CHANGE_STATUS_HOTKEY";
    public static final String EDIT_TARGET_HOTKEY_SETTING = "EDIT_TARGET_HOTKEY";
    public static final String TAB_PREVIOUS_HOTKEY_SETTING = "TAB_PREVIOUS_HOTKEY";
    public static final String TAB_NEXT_HOTKEY_SETTING = "TAB_NEXT_HOTKEY";

    private static final HashMap<String, String> STRING_PREFERENCE_DEFAULTS = new HashMap<>();

    private TreepeaterSettings(Preferences preferences) {
        this.preferences = preferences;

        STRING_PREFERENCE_DEFAULTS.put(SEND_HOTKEY_SETTING, "Ctrl+Alt+Shift+T");
        STRING_PREFERENCE_DEFAULTS.put(SEND_REQUEST_HOTKEY_SETTING, "Ctrl+Shift+Space");
        STRING_PREFERENCE_DEFAULTS.put(HISTORY_BACK_HOTKEY_SETTING, "Ctrl+Minus");
        STRING_PREFERENCE_DEFAULTS.put(HISTORY_FORWARD_HOTKEY_SETTING, "Ctrl+Plus");
        STRING_PREFERENCE_DEFAULTS.put(COPY_SAME_PARENT_REQUEST_HOTKEY_SETTING, "Ctrl+Alt+Shift+C");
        STRING_PREFERENCE_DEFAULTS.put(RENAME_HOTKEY_SETTING, "Ctrl+N");
        STRING_PREFERENCE_DEFAULTS.put(CHANGE_STATUS_HOTKEY_SETTING, "Ctrl+Shift+S");
        STRING_PREFERENCE_DEFAULTS.put(EDIT_TARGET_HOTKEY_SETTING, "Ctrl+L");
        STRING_PREFERENCE_DEFAULTS.put(TAB_PREVIOUS_HOTKEY_SETTING, "Ctrl+Alt+Left");
        STRING_PREFERENCE_DEFAULTS.put(TAB_NEXT_HOTKEY_SETTING, "Ctrl+Alt+Right");
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

    /**
     * Returns the persisted string for {@code key}, or {@code null} if none is stored.
     */
    public String getString(String key) {
        return this.preferences.getString(key);
    }

    /**
     * Returns the persisted value for {@code key}, or the configured default when unset.
     */
    public String getStringWithDefault(String key) {
        String value = this.getString(key);
        return value != null ? value : STRING_PREFERENCE_DEFAULTS.get(key);
    }

    public String getSendHotkey() {
        return this.getStringWithDefault(SEND_HOTKEY_SETTING);
    }

    public void setSendHotkey(String hotkey) {
        this.preferences.setString(SEND_HOTKEY_SETTING, hotkey);
        this.notifyListeners(SEND_HOTKEY_SETTING, hotkey);
    }

    public String getSendRequestHotkey() {
        return this.getStringWithDefault(SEND_REQUEST_HOTKEY_SETTING);
    }

    public void setSendRequestHotkey(String hotkey) {
        this.preferences.setString(SEND_REQUEST_HOTKEY_SETTING, hotkey);
        this.notifyListeners(SEND_REQUEST_HOTKEY_SETTING, hotkey);
    }

    public String getHistoryBackHotkey() {
        return this.getStringWithDefault(HISTORY_BACK_HOTKEY_SETTING);
    }

    public void setHistoryBackHotkey(String hotkey) {
        this.preferences.setString(HISTORY_BACK_HOTKEY_SETTING, hotkey);
        this.notifyListeners(HISTORY_BACK_HOTKEY_SETTING, hotkey);
    }

    public String getHistoryForwardHotkey() {
        return this.getStringWithDefault(HISTORY_FORWARD_HOTKEY_SETTING);
    }

    public void setHistoryForwardHotkey(String hotkey) {
        this.preferences.setString(HISTORY_FORWARD_HOTKEY_SETTING, hotkey);
        this.notifyListeners(HISTORY_FORWARD_HOTKEY_SETTING, hotkey);
    }

    public String getCopySameParentRequestHotkey() {
        return this.getStringWithDefault(COPY_SAME_PARENT_REQUEST_HOTKEY_SETTING);
    }

    public void setCopySameParentRequestHotkey(String hotkey) {
        this.preferences.setString(COPY_SAME_PARENT_REQUEST_HOTKEY_SETTING, hotkey);
        this.notifyListeners(COPY_SAME_PARENT_REQUEST_HOTKEY_SETTING, hotkey);
    }

    public String getRenameHotkey() {
        return this.getStringWithDefault(RENAME_HOTKEY_SETTING);
    }

    public void setRenameHotkey(String hotkey) {
        this.preferences.setString(RENAME_HOTKEY_SETTING, hotkey);
        this.notifyListeners(RENAME_HOTKEY_SETTING, hotkey);
    }

    public String getChangeStatusHotkey() {
        return this.getStringWithDefault(CHANGE_STATUS_HOTKEY_SETTING);
    }

    public void setChangeStatusHotkey(String hotkey) {
        this.preferences.setString(CHANGE_STATUS_HOTKEY_SETTING, hotkey);
        this.notifyListeners(CHANGE_STATUS_HOTKEY_SETTING, hotkey);
    }

    public String getEditTargetHotkey() {
        return this.getStringWithDefault(EDIT_TARGET_HOTKEY_SETTING);
    }

    public void setEditTargetHotkey(String hotkey) {
        this.preferences.setString(EDIT_TARGET_HOTKEY_SETTING, hotkey);
        this.notifyListeners(EDIT_TARGET_HOTKEY_SETTING, hotkey);
    }

    public String getTabPreviousHotkey() {
        return this.getStringWithDefault(TAB_PREVIOUS_HOTKEY_SETTING);
    }

    public void setTabPreviousHotkey(String hotkey) {
        this.preferences.setString(TAB_PREVIOUS_HOTKEY_SETTING, hotkey);
        this.notifyListeners(TAB_PREVIOUS_HOTKEY_SETTING, hotkey);
    }

    public String getTabNextHotkey() {
        return this.getStringWithDefault(TAB_NEXT_HOTKEY_SETTING);
    }

    public void setTabNextHotkey(String hotkey) {
        this.preferences.setString(TAB_NEXT_HOTKEY_SETTING, hotkey);
        this.notifyListeners(TAB_NEXT_HOTKEY_SETTING, hotkey);
    }

    public void addListener(TreepeaterSerttingsChangeListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(TreepeaterSerttingsChangeListener listener) {
        this.listeners.remove(listener);
    }

    private void notifyListeners(String key, Object value) {
        this.listeners.forEach(l -> l.onChange(key, value));
    }
}
