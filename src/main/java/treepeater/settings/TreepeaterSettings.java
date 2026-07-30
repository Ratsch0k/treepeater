package treepeater.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import burp.api.montoya.persistence.Preferences;
import treepeater.Utilities;
import treepeater.requestResponse.Status;

public class TreepeaterSettings {
    private static TreepeaterSettings instance = null;

    private final Preferences preferences;

    private final List<TreepeaterSerttingsChangeListener> listeners = new ArrayList<>();

    public static final String SEND_HOTKEY_SETTING = "TREEPEATER_SEND_HOTKEY";
    /** Hotkey for the path-aware "Send to Treepeater (sorted)" action. */
    public static final String SEND_PATH_AWARE_HOTKEY_SETTING = "TREEPEATER_SEND_PATH_AWARE_HOTKEY";
    /** Hotkey for the manual "Send to Treepeater (manual)" action. */
    public static final String SEND_MANUAL_HOTKEY_SETTING = "TREEPEATER_SEND_MANUAL_HOTKEY";
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
    public static final String FOCUS_TREE_HOTKEY_SETTING = "FOCUS_TREE_HOTKEY";

    /**
     * Base name for user preference keys of the global default status list.
     * Entries use {@link #DEFAULT_STATUSES_COUNT_SETTING} and indexed keys such as
     * {@code TREEPEATER_DEFAULT_STATUSES_0_ID}.
     */
    public static final String DEFAULT_STATUSES_SETTING = "TREEPEATER_DEFAULT_STATUSES";

    /** Integer preference: number of stored default statuses (0 means none). */
    public static final String DEFAULT_STATUSES_COUNT_SETTING = DEFAULT_STATUSES_SETTING + "_COUNT";

    /** Bulk-path import leaf placement mode: {@link #IMPORT_LEAF_MODE_DIRECT} or {@link #IMPORT_LEAF_MODE_METHOD_FOLDER}. */
    public static final String IMPORT_LEAF_MODE_SETTING = "TREEPEATER_IMPORT_LEAF_MODE";
    /** Leaf sits directly under its path folder, named after the last path segment. */
    public static final String IMPORT_LEAF_MODE_DIRECT = "DIRECT";
    /** Leaf sits under a per-method folder (e.g. {@code [GET]}), named with the configured base name. */
    public static final String IMPORT_LEAF_MODE_METHOD_FOLDER = "METHOD_FOLDER";
    /** Base leaf name used in {@link #IMPORT_LEAF_MODE_METHOD_FOLDER} mode. */
    public static final String IMPORT_METHOD_BASE_LEAF_NAME_SETTING = "TREEPEATER_IMPORT_METHOD_BASE_LEAF_NAME";

    /**
     * When enabled, path-aware import uses {@linkplain #isImportGroupingFolderReconciliationEnabled()
     * lenient folder grouping} to attach requests under existing folders that include extra
     * leading organizational segments (e.g. {@code ServiceA/users} for {@code /users/1}).
     */
    public static final String IMPORT_GROUPING_FOLDER_RECONCILIATION_ENABLED_SETTING =
            "TREEPEATER_IMPORT_GROUPING_FOLDER_RECONCILIATION_ENABLED";
    /**
     * Maximum number of leading folder segments that lenient folder grouping may skip when
     * searching for a matching anchor in the tree.
     */
    public static final String IMPORT_GROUPING_FOLDER_RECONCILIATION_MAX_SKIP_SETTING =
            "TREEPEATER_IMPORT_GROUPING_FOLDER_RECONCILIATION_MAX_SKIP";
    /**
     * Minimum fraction of the target path (0–100, whole percent) that the matched folder suffix must
     * cover for lenient folder grouping to accept a candidate.
     */
    public static final String IMPORT_GROUPING_FOLDER_RECONCILIATION_MATCH_THRESHOLD_PERCENT_SETTING =
            "TREEPEATER_IMPORT_GROUPING_FOLDER_RECONCILIATION_MATCH_THRESHOLD_PERCENT";

    /**
     * When enabled, path-aware import rewrites dynamic URL segments into placeholders
     * (e.g. {@code /users/2/status} -> {@code /users/:id/status}).
     */
    public static final String IMPORT_NORMALIZE_DYNAMIC_SEGMENTS_ENABLED_SETTING =
            "TREEPEATER_IMPORT_NORMALIZE_DYNAMIC_SEGMENTS_ENABLED";

    public static final int IMPORT_GROUPING_FOLDER_RECONCILIATION_MAX_SKIP_DEFAULT = 2;
    public static final int IMPORT_GROUPING_FOLDER_RECONCILIATION_MATCH_THRESHOLD_PERCENT_DEFAULT = 60;

    public static final String LLM_OLLAMA_BASE_URL_SETTING = "TREEPEATER_LLM_OLLAMA_BASE_URL";
    public static final String LLM_OLLAMA_MODELS_SETTING = "TREEPEATER_LLM_OLLAMA_MODELS";
    public static final String LLM_OLLAMA_MODELS_COUNT_SETTING = LLM_OLLAMA_MODELS_SETTING + "_COUNT";
    public static final String LLM_ANTHROPIC_API_KEY_SETTING = "TREEPEATER_LLM_ANTHROPIC_API_KEY";
    /** Azure OpenAI / Microsoft Foundry resource endpoint (no trailing path), e.g. {@code https://myresource.openai.azure.com}. */
    public static final String LLM_AZURE_OPENAI_ENDPOINT_SETTING = "TREEPEATER_LLM_AZURE_OPENAI_ENDPOINT";
    public static final String LLM_AZURE_OPENAI_API_KEY_SETTING = "TREEPEATER_LLM_AZURE_OPENAI_API_KEY";

    private static final String DEFAULT_STATUS_FIELD_ID = "ID";
    private static final String DEFAULT_STATUS_FIELD_NAME = "NAME";
    private static final String DEFAULT_STATUS_FIELD_SVG = "SVG";

    private static final String DEFAULT_STATUS_FIELD_COLOR_MODE = "COLOR_MODE";

    private static final String COLOR_MODE_VALUE = "VALUE";
    private static final String COLOR_MODE_NAMED = "NAMED";

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

        STRING_PREFERENCE_DEFAULTS.put(SEND_HOTKEY_SETTING, "Ctrl+Alt+S");
        STRING_PREFERENCE_DEFAULTS.put(SEND_PATH_AWARE_HOTKEY_SETTING, "Ctrl+Alt+Shift+S");
        STRING_PREFERENCE_DEFAULTS.put(SEND_MANUAL_HOTKEY_SETTING, "Ctrl+Alt+M");
        STRING_PREFERENCE_DEFAULTS.put(SEND_REQUEST_HOTKEY_SETTING, "Ctrl+Shift+Space");
        STRING_PREFERENCE_DEFAULTS.put(HISTORY_BACK_HOTKEY_SETTING, "Ctrl+Minus");
        STRING_PREFERENCE_DEFAULTS.put(HISTORY_FORWARD_HOTKEY_SETTING, "Ctrl+Plus");
        STRING_PREFERENCE_DEFAULTS.put(COPY_SAME_PARENT_REQUEST_HOTKEY_SETTING, "Ctrl+Alt+Shift+C");
        STRING_PREFERENCE_DEFAULTS.put(RENAME_HOTKEY_SETTING, "Ctrl+N");
        STRING_PREFERENCE_DEFAULTS.put(CHANGE_STATUS_HOTKEY_SETTING, "Ctrl+Shift+S");
        STRING_PREFERENCE_DEFAULTS.put(EDIT_TARGET_HOTKEY_SETTING, "Ctrl+L");
        STRING_PREFERENCE_DEFAULTS.put(TAB_PREVIOUS_HOTKEY_SETTING, "Ctrl+Alt+Left");
        STRING_PREFERENCE_DEFAULTS.put(TAB_NEXT_HOTKEY_SETTING, "Ctrl+Alt+Right");
        STRING_PREFERENCE_DEFAULTS.put(FOCUS_TREE_HOTKEY_SETTING, "Ctrl+Alt+T");
        STRING_PREFERENCE_DEFAULTS.put(IMPORT_LEAF_MODE_SETTING, IMPORT_LEAF_MODE_DIRECT);
        STRING_PREFERENCE_DEFAULTS.put(IMPORT_METHOD_BASE_LEAF_NAME_SETTING, "base");
        STRING_PREFERENCE_DEFAULTS.put(IMPORT_GROUPING_FOLDER_RECONCILIATION_ENABLED_SETTING, "true");
        STRING_PREFERENCE_DEFAULTS.put(IMPORT_NORMALIZE_DYNAMIC_SEGMENTS_ENABLED_SETTING, "true");
        STRING_PREFERENCE_DEFAULTS.put(LLM_OLLAMA_BASE_URL_SETTING, "http://127.0.0.1:11434");
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

    public String getSendPathAwareHotkey() {
        return this.getStringWithDefault(SEND_PATH_AWARE_HOTKEY_SETTING);
    }

    public void setSendSortedHotkey(String hotkey) {
        this.preferences.setString(SEND_PATH_AWARE_HOTKEY_SETTING, hotkey);
        this.notifyListeners(SEND_PATH_AWARE_HOTKEY_SETTING, hotkey);
    }

    public String getSendManualHotkey() {
        return this.getStringWithDefault(SEND_MANUAL_HOTKEY_SETTING);
    }

    public void setSendManualHotkey(String hotkey) {
        this.preferences.setString(SEND_MANUAL_HOTKEY_SETTING, hotkey);
        this.notifyListeners(SEND_MANUAL_HOTKEY_SETTING, hotkey);
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

    public String getFocusTreeHotkey() {
        return this.getStringWithDefault(FOCUS_TREE_HOTKEY_SETTING);
    }

    public void setFocusTreeHotkey(String hotkey) {
        this.preferences.setString(FOCUS_TREE_HOTKEY_SETTING, hotkey);
        this.notifyListeners(FOCUS_TREE_HOTKEY_SETTING, hotkey);
    }

    /**
     * Bulk-path import leaf placement mode, one of {@link #IMPORT_LEAF_MODE_DIRECT} or
     * {@link #IMPORT_LEAF_MODE_METHOD_FOLDER}. Defaults to {@link #IMPORT_LEAF_MODE_DIRECT}.
     */
    public String getImportLeafMode() {
        String value = this.getStringWithDefault(IMPORT_LEAF_MODE_SETTING);
        return IMPORT_LEAF_MODE_METHOD_FOLDER.equals(value)
                ? IMPORT_LEAF_MODE_METHOD_FOLDER
                : IMPORT_LEAF_MODE_DIRECT;
    }

    public void setImportLeafMode(String mode) {
        String normalized = IMPORT_LEAF_MODE_METHOD_FOLDER.equals(mode)
                ? IMPORT_LEAF_MODE_METHOD_FOLDER
                : IMPORT_LEAF_MODE_DIRECT;
        this.preferences.setString(IMPORT_LEAF_MODE_SETTING, normalized);
        this.notifyListeners(IMPORT_LEAF_MODE_SETTING, normalized);
    }

    /** Base leaf name used in {@link #IMPORT_LEAF_MODE_METHOD_FOLDER} mode (default {@code base}). */
    public String getImportBaseLeafName() {
        return this.getStringWithDefault(IMPORT_METHOD_BASE_LEAF_NAME_SETTING);
    }

    public void setImportBaseLeafName(String name) {
        this.preferences.setString(IMPORT_METHOD_BASE_LEAF_NAME_SETTING, name);
        this.notifyListeners(IMPORT_METHOD_BASE_LEAF_NAME_SETTING, name);
    }

    /** Whether lenient folder grouping is enabled for path-aware import (default {@code true}). */
    public boolean isImportGroupingFolderReconciliationEnabled() {
        return Boolean.parseBoolean(
                this.getStringWithDefault(IMPORT_GROUPING_FOLDER_RECONCILIATION_ENABLED_SETTING));
    }

    public void setImportGroupingFolderReconciliationEnabled(boolean enabled) {
        this.preferences.setString(
                IMPORT_GROUPING_FOLDER_RECONCILIATION_ENABLED_SETTING, Boolean.toString(enabled));
        this.notifyListeners(IMPORT_GROUPING_FOLDER_RECONCILIATION_ENABLED_SETTING, enabled);
    }

    /**
     * Maximum leading folder segments that lenient folder grouping may skip (default
     * {@link #IMPORT_GROUPING_FOLDER_RECONCILIATION_MAX_SKIP_DEFAULT}, clamped to 1–10).
     */
    public int getImportGroupingFolderReconciliationMaxSkip() {
        Integer value = this.preferences.getInteger(IMPORT_GROUPING_FOLDER_RECONCILIATION_MAX_SKIP_SETTING);
        int n = value != null
                ? value.intValue()
                : IMPORT_GROUPING_FOLDER_RECONCILIATION_MAX_SKIP_DEFAULT;
        return Math.max(1, Math.min(10, n));
    }

    public void setImportGroupingFolderReconciliationMaxSkip(int maxSkip) {
        int clamped = Math.max(1, Math.min(10, maxSkip));
        this.preferences.setInteger(IMPORT_GROUPING_FOLDER_RECONCILIATION_MAX_SKIP_SETTING, clamped);
        this.notifyListeners(IMPORT_GROUPING_FOLDER_RECONCILIATION_MAX_SKIP_SETTING, clamped);
    }

    /**
     * Minimum target-path overlap required by lenient folder grouping, as a whole percent from
     * 0 to 100 (default {@link #IMPORT_GROUPING_FOLDER_RECONCILIATION_MATCH_THRESHOLD_PERCENT_DEFAULT}).
     */
    public int getImportGroupingFolderReconciliationMatchThresholdPercent() {
        Integer value =
                this.preferences.getInteger(IMPORT_GROUPING_FOLDER_RECONCILIATION_MATCH_THRESHOLD_PERCENT_SETTING);
        int n = value != null
                ? value.intValue()
                : IMPORT_GROUPING_FOLDER_RECONCILIATION_MATCH_THRESHOLD_PERCENT_DEFAULT;
        return Math.max(0, Math.min(100, n));
    }

    public void setImportGroupingFolderReconciliationMatchThresholdPercent(int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        this.preferences.setInteger(
                IMPORT_GROUPING_FOLDER_RECONCILIATION_MATCH_THRESHOLD_PERCENT_SETTING, clamped);
        this.notifyListeners(IMPORT_GROUPING_FOLDER_RECONCILIATION_MATCH_THRESHOLD_PERCENT_SETTING, clamped);
    }

    /** Overlap threshold as a fraction in {@code [0.0, 1.0]}. */
    public double getImportGroupingFolderReconciliationMatchThreshold() {
        return this.getImportGroupingFolderReconciliationMatchThresholdPercent() / 100.0;
    }

    /** Whether dynamic path segment normalization is enabled for path-aware import (default {@code false}). */
    public boolean isImportNormalizeDynamicSegmentsEnabled() {
        return Boolean.parseBoolean(
                this.getStringWithDefault(IMPORT_NORMALIZE_DYNAMIC_SEGMENTS_ENABLED_SETTING));
    }

    public void setImportNormalizeDynamicSegmentsEnabled(boolean enabled) {
        this.preferences.setString(
                IMPORT_NORMALIZE_DYNAMIC_SEGMENTS_ENABLED_SETTING, Boolean.toString(enabled));
        this.notifyListeners(IMPORT_NORMALIZE_DYNAMIC_SEGMENTS_ENABLED_SETTING, enabled);
    }

    public String getLlmOllamaBaseUrl() {
        return this.getStringWithDefault(LLM_OLLAMA_BASE_URL_SETTING);
    }

    public void setLlmOllamaBaseUrl(String baseUrl) {
        this.preferences.setString(LLM_OLLAMA_BASE_URL_SETTING, baseUrl);
        this.notifyListeners(LLM_OLLAMA_BASE_URL_SETTING, baseUrl);
    }

    /**
     * User-configured Ollama model names, or {@code null} if never set (meaning use defaults).
     */
    public List<String> getOllamaModels() {
        Integer countBox = this.preferences.getInteger(LLM_OLLAMA_MODELS_COUNT_SETTING);
        if (countBox == null) {
            return null;
        }
        int n = countBox.intValue();
        if (n <= 0) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String model = this.preferences.getString(LLM_OLLAMA_MODELS_SETTING + "_" + i);
            if (model != null && !model.isBlank()) {
                result.add(model);
            }
        }
        return result;
    }

    public void setOllamaModels(List<String> models) {
        List<String> list = models != null ? models : List.of();
        Integer previousCountBox = this.preferences.getInteger(LLM_OLLAMA_MODELS_COUNT_SETTING);
        int previousCount = previousCountBox != null ? previousCountBox.intValue() : 0;

        int n = list.size();
        for (int i = 0; i < n; i++) {
            this.preferences.setString(LLM_OLLAMA_MODELS_SETTING + "_" + i, list.get(i));
        }
        for (int i = n; i < previousCount; i++) {
            this.preferences.deleteString(LLM_OLLAMA_MODELS_SETTING + "_" + i);
        }
        this.preferences.setInteger(LLM_OLLAMA_MODELS_COUNT_SETTING, n);
        this.notifyListeners(LLM_OLLAMA_MODELS_SETTING, list);
    }

    /**
     * Stored Anthropic API key, or {@code null} if the user has never set one.
     */
    public String getLlmAnthropicApiKey() {
        return this.getString(LLM_ANTHROPIC_API_KEY_SETTING);
    }

    public void setLlmAnthropicApiKey(String apiKey) {
        this.preferences.setString(LLM_ANTHROPIC_API_KEY_SETTING, apiKey);
        this.notifyListeners(LLM_ANTHROPIC_API_KEY_SETTING, apiKey);
    }

    /**
     * Azure OpenAI / Foundry endpoint base URL, or {@code null} if unset.
     */
    public String getLlmAzureOpenAiEndpoint() {
        return this.getString(LLM_AZURE_OPENAI_ENDPOINT_SETTING);
    }

    public void setLlmAzureOpenAiEndpoint(String endpoint) {
        this.preferences.setString(LLM_AZURE_OPENAI_ENDPOINT_SETTING, endpoint);
        this.notifyListeners(LLM_AZURE_OPENAI_ENDPOINT_SETTING, endpoint);
    }

    /**
     * API key for Azure OpenAI / Foundry, or {@code null} if unset.
     */
    public String getLlmAzureOpenAiApiKey() {
        return this.getString(LLM_AZURE_OPENAI_API_KEY_SETTING);
    }

    public void setLlmAzureOpenAiApiKey(String apiKey) {
        this.preferences.setString(LLM_AZURE_OPENAI_API_KEY_SETTING, apiKey);
        this.notifyListeners(LLM_AZURE_OPENAI_API_KEY_SETTING, apiKey);
    }

    /**
     * Persists the user's default status list (order and full definition) in Burp preferences.
     *
     * @param statuses statuses to store; {@code null} is treated as an empty list
     */
    public void setDefaultStatuses(List<Status> statuses) {

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
            } else if (s.getNamedColors().isPresent()) {
                Status.StatusNamedColors k = s.getNamedColors().get();
                this.preferences.setString(
                        defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_COLOR_MODE), COLOR_MODE_NAMED);
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

        this.preferences.setInteger(DEFAULT_STATUSES_COUNT_SETTING, n);
        this.notifyListeners(DEFAULT_STATUSES_SETTING, list);
    }

    /**
     * Loads the user's default status list from preferences, or {@code null} if unset, or an empty list if invalid.
     */
    public List<Status> getDefaultStatuses() {
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
                if (COLOR_MODE_NAMED.equals(mode)) {
                    String bgL = this.preferences.getString(defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_KEY_BG_LIGHT));
                    String brL = this.preferences.getString(defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_KEY_BORDER_LIGHT));
                    String bgD = this.preferences.getString(defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_KEY_BG_DARK));
                    String brD = this.preferences.getString(defaultStatusFieldKey(i, DEFAULT_STATUS_FIELD_KEY_BORDER_DARK));
                    if (bgL == null || brL == null || bgD == null || brD == null) {
                        return Collections.emptyList();
                    }
                    Status.StatusNamedColors keyed = new Status.StatusNamedColors(bgL, brL, bgD, brD);
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
