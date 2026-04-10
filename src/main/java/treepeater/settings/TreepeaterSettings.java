package treepeater.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import burp.api.montoya.persistence.Preferences;
import treepeater.Treepeater;
import treepeater.Utilities;
import treepeater.requestResponse.Status;

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

    /**
     * Base name for user preference keys of the global default status list.
     * Entries use {@link #DEFAULT_STATUSES_COUNT_SETTING} and indexed keys such as
     * {@code TREEPEATER_DEFAULT_STATUSES_0_ID}.
     */
    public static final String DEFAULT_STATUSES_SETTING = "TREEPEATER_DEFAULT_STATUSES";

    /** Integer preference: number of stored default statuses (0 means none). */
    public static final String DEFAULT_STATUSES_COUNT_SETTING = DEFAULT_STATUSES_SETTING + "_COUNT";

    private static final String DEFAULT_STATUS_FIELD_ID = "ID";
    private static final String DEFAULT_STATUS_FIELD_NAME = "NAME";
    private static final String DEFAULT_STATUS_FIELD_SVG = "SVG";
    /** {@code VALUE} = {@link Status.StatusColors}; {@code KEYED} = {@link Status.StatusKeyedColors}. */
    private static final String DEFAULT_STATUS_FIELD_COLOR_MODE = "COLOR_MODE";

    private static final String COLOR_MODE_VALUE = "VALUE";
    private static final String COLOR_MODE_KEYED = "KEYED";

    private static final String DEFAULT_STATUS_FIELD_COLOR_BG_LIGHT = "COLOR_BG_LIGHT";
    private static final String DEFAULT_STATUS_FIELD_COLOR_BORDER_LIGHT = "COLOR_BORDER_LIGHT";
    private static final String DEFAULT_STATUS_FIELD_COLOR_BG_DARK = "COLOR_BG_DARK";
    private static final String DEFAULT_STATUS_FIELD_COLOR_BORDER_DARK = "COLOR_BORDER_DARK";

    private static final String DEFAULT_STATUS_FIELD_KEY_BG_LIGHT = "KEY_BG_LIGHT";
    private static final String DEFAULT_STATUS_FIELD_KEY_BORDER_LIGHT = "KEY_BORDER_LIGHT";
    private static final String DEFAULT_STATUS_FIELD_KEY_BG_DARK = "KEY_BG_DARK";
    private static final String DEFAULT_STATUS_FIELD_KEY_BORDER_DARK = "KEY_BORDER_DARK";

    /** Legacy keys (resolved colors only); removed on write, still read for migration. */
    private static final String DEFAULT_STATUS_FIELD_LEGACY_BACKGROUND = "BACKGROUND";
    private static final String DEFAULT_STATUS_FIELD_LEGACY_BORDER = "BORDER";

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

    /**
     * Persists the user's default status list (order and full definition) in Burp preferences.
     *
     * @param statuses statuses to store; {@code null} is treated as an empty list
     */
    public void setDefaultStatuses(List<Status> statuses) {
        Treepeater.api.logging().logToOutput("Setting default statuses: " + statuses);

        List<Status> list = statuses != null ? statuses : List.of();
        Integer previousCountBox = this.preferences.getInteger(DEFAULT_STATUSES_COUNT_SETTING);
        int previousCount = previousCountBox != null ? previousCountBox.intValue() : 0;

        int n = list.size();
        for (int i = 0; i < n; i++) {
            Status s = list.get(i);
            this.deleteDefaultStatusIndex(i);
            this.preferences.setString(
                    defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_ID), s.getId());
            this.preferences.setString(
                    defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_NAME), s.getStatus());
            this.preferences.setString(
                    defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_SVG), s.getSvgContent());
            if (s.getColors().isPresent()) {
                Status.StatusColors c = s.getColors().get();
                this.preferences.setString(
                        defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_COLOR_MODE), COLOR_MODE_VALUE);
                this.preferences.setString(
                        defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_COLOR_BG_LIGHT),
                        Utilities.colorToHex(c.backgroundColor()));
                this.preferences.setString(
                        defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_COLOR_BORDER_LIGHT),
                        Utilities.colorToHex(c.borderColor()));
                this.preferences.setString(
                        defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_COLOR_BG_DARK),
                        Utilities.colorToHex(c.backgroundDarkModeColor()));
                this.preferences.setString(
                        defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_COLOR_BORDER_DARK),
                        Utilities.colorToHex(c.borderColorDarkModeColor()));
            } else if (s.getKeyedColors().isPresent()) {
                Status.StatusKeyedColors k = s.getKeyedColors().get();
                this.preferences.setString(
                        defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_COLOR_MODE), COLOR_MODE_KEYED);
                this.preferences.setString(
                        defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_KEY_BG_LIGHT),
                        k.backgroundColorKey());
                this.preferences.setString(
                        defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_KEY_BORDER_LIGHT),
                        k.borderColorKey());
                this.preferences.setString(
                        defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_KEY_BG_DARK),
                        k.backgroundDarkModeColorKey());
                this.preferences.setString(
                        defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_KEY_BORDER_DARK),
                        k.borderColorDarkModeColorKey());
            } else {
                throw new IllegalStateException("Status has neither colors nor keyed colors");
            }
        }
        for (int i = n; i < previousCount; i++) {
            deleteDefaultStatusIndex(i);
        }
        if (n == 0) {
            this.preferences.deleteInteger(DEFAULT_STATUSES_COUNT_SETTING);
        } else {
            this.preferences.setInteger(DEFAULT_STATUSES_COUNT_SETTING, n);
        }
        this.notifyListeners(DEFAULT_STATUSES_SETTING, list);
    }

    /**
     * Loads the user's default status list from preferences, or {@code null} if unset, or an empty list if invalid.
     */
    public List<Status> getDefaultStatuses() {
        Treepeater.api.logging().logToOutput("Getting default statuses");
        Integer countBox = this.preferences.getInteger(DEFAULT_STATUSES_COUNT_SETTING);
        
        // In this case the user has never saved a default status list, so we return null.
        if (countBox == null) {
            return null;
        }

        // In this case the user has saved a default status list, but it is empty, so we return an empty list.
        if (countBox.intValue() <= 0) {
            return Collections.emptyList();
        }

        int n = countBox.intValue();
        List<Status> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String id = this.preferences.getString(defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_ID));
            String name = this.preferences.getString(defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_NAME));
            String svg = this.preferences.getString(defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_SVG));
            String mode = this.preferences.getString(defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_COLOR_MODE));
            if (id == null || name == null || svg == null) {
                return Collections.emptyList();
            }
            try {
                if (COLOR_MODE_KEYED.equals(mode)) {
                    String bgL = this.preferences.getString(defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_KEY_BG_LIGHT));
                    String brL = this.preferences.getString(defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_KEY_BORDER_LIGHT));
                    String bgD = this.preferences.getString(defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_KEY_BG_DARK));
                    String brD = this.preferences.getString(defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_KEY_BORDER_DARK));
                    if (bgL == null || brL == null || bgD == null || brD == null) {
                        return Collections.emptyList();
                    }
                    Status.StatusKeyedColors keyed = new Status.StatusKeyedColors(bgL, brL, bgD, brD);
                    result.add(new Status(id, name, keyed, svg));
                } else if (COLOR_MODE_VALUE.equals(mode)) {
                    String bgL = this.preferences.getString(defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_COLOR_BG_LIGHT));
                    String brL = this.preferences.getString(defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_COLOR_BORDER_LIGHT));
                    String bgD = this.preferences.getString(defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_COLOR_BG_DARK));
                    String brD = this.preferences.getString(defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_COLOR_BORDER_DARK));
                    if (bgL == null || brL == null || bgD == null || brD == null) {
                        return Collections.emptyList();
                    }
                    Status.StatusColors colors = new Status.StatusColors(
                            Utilities.hexToColor(bgL),
                            Utilities.hexToColor(brL),
                            Utilities.hexToColor(bgD),
                            Utilities.hexToColor(brD));
                    result.add(new Status(id, name, colors, svg));
                } else {
                    String legacyBg = this.preferences.getString(
                            defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_LEGACY_BACKGROUND));
                    String legacyBr = this.preferences.getString(
                            defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_LEGACY_BORDER));
                    if (legacyBg == null || legacyBr == null) {
                        return Collections.emptyList();
                    }
                    Status.StatusColors colors = new Status.StatusColors(
                            Utilities.hexToColor(legacyBg),
                            Utilities.hexToColor(legacyBr),
                            Utilities.hexToColor(legacyBg),
                            Utilities.hexToColor(legacyBr));
                    result.add(new Status(id, name, colors, svg));
                }
            } catch (IllegalArgumentException e) {
                return Collections.emptyList();
            }
        }
        return result;
    }

    private static String defaultStatusFieldKey(int index, String field) {
        return DEFAULT_STATUSES_SETTING + "_" + index + "_" + field;
    }

    private void deleteDefaultStatusIndex(int index) {
        this.preferences.deleteString(defaultStatusFieldKey(index, DEFAULT_STATUS_FIELD_ID));
        this.preferences.deleteString(defaultStatusFieldKey(index, DEFAULT_STATUS_FIELD_NAME));
        this.preferences.deleteString(defaultStatusFieldKey(index, DEFAULT_STATUS_FIELD_SVG));
        this.preferences.deleteString(defaultStatusFieldKey(index, DEFAULT_STATUS_FIELD_COLOR_MODE));
        this.preferences.deleteString(defaultStatusFieldKey(index, DEFAULT_STATUS_FIELD_COLOR_BG_LIGHT));
        this.preferences.deleteString(defaultStatusFieldKey(index, DEFAULT_STATUS_FIELD_COLOR_BORDER_LIGHT));
        this.preferences.deleteString(defaultStatusFieldKey(index, DEFAULT_STATUS_FIELD_COLOR_BG_DARK));
        this.preferences.deleteString(defaultStatusFieldKey(index, DEFAULT_STATUS_FIELD_COLOR_BORDER_DARK));
        this.preferences.deleteString(defaultStatusFieldKey(index, DEFAULT_STATUS_FIELD_KEY_BG_LIGHT));
        this.preferences.deleteString(defaultStatusFieldKey(index, DEFAULT_STATUS_FIELD_KEY_BORDER_LIGHT));
        this.preferences.deleteString(defaultStatusFieldKey(index, DEFAULT_STATUS_FIELD_KEY_BG_DARK));
        this.preferences.deleteString(defaultStatusFieldKey(index, DEFAULT_STATUS_FIELD_KEY_BORDER_DARK));
        this.preferences.deleteString(defaultStatusFieldKey(index, DEFAULT_STATUS_FIELD_LEGACY_BACKGROUND));
        this.preferences.deleteString(defaultStatusFieldKey(index, DEFAULT_STATUS_FIELD_LEGACY_BORDER));
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
