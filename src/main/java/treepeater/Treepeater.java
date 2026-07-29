package treepeater;
import burp.api.montoya.BurpExtension;
import burp.api.montoya.EnhancedCapability;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Registration;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import burp.api.montoya.ui.hotkey.HotKey;
import burp.api.montoya.ui.hotkey.HotKeyHandler;
import treepeater.importing.ManualImport;
import treepeater.persistence.TreepeaterPersistence;
import treepeater.requestResponse.Status;
import treepeater.settings.StatusRegistry;
import treepeater.settings.TreepeaterSettings;
import treepeater.settings.TreepeaterSettingsPanel;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Treepeater implements BurpExtension {
    public static MontoyaApi api;
    private static StatusRegistry statusRegistry;
    private static TreepeaterModel model;
    private static TreepeaterPersistence persistence;
    private static volatile boolean dirty = false;
    DefaultMutableTreeNode root;

    private Registration sendHotKeyRegistration;
    private Registration sendSortedHotKeyRegistration;
    private Registration sendManualHotKeyRegistration;
    private javax.swing.Timer autoSaveTimer;

    @Override
    public void initialize(MontoyaApi montoyaApi) {
        Treepeater.api =  montoyaApi;
        montoyaApi.extension().setName("Treepeater");

        TreepeaterSettings.init(montoyaApi.persistence().preferences());
        TreepeaterSettings settings = TreepeaterSettings.getInstance();

        Treepeater.persistence = new TreepeaterPersistence(montoyaApi.persistence());

        try {
            api.logging().logToOutput("Loading status registry");
            if (Treepeater.persistence.hasStatusRegistry()) {
                Treepeater.statusRegistry = Treepeater.persistence.loadStatusRegistry();
                Treepeater.api.logging().logToOutput("Status registry loaded from project file");
            } else {
                Treepeater.api.logging().logToOutput("No status registry saved in project");
                List<Status> defaultStatuses = settings.getDefaultStatuses();
                if (defaultStatuses != null) {
                    Treepeater.api.logging().logToOutput("Default statues found in user preferences, creating new status registry with them");
                    Treepeater.statusRegistry = new StatusRegistry(defaultStatuses);
                } else {
                    Treepeater.api.logging().logToOutput("No default statues found in user preferences, creating new status registry with standard statues");
                    Treepeater.statusRegistry = new StatusRegistry();
                }
            }
        } catch (Exception e) {
            Treepeater.api.logging().logToOutput("Error loading status registry from file: " + e.getMessage());
            Treepeater.statusRegistry = new StatusRegistry();
        }

        try {
            Treepeater.api.logging().logToOutput("Loading model");
            Treepeater.model = Treepeater.persistence.loadModel();
            Treepeater.api.logging().logToOutput("Model loaded");
        } catch (Exception e) {
            Treepeater.api.logging().logToOutput("Error loading state from file: " + e.getMessage());
            Treepeater.model = new TreepeaterModel();
        }

        montoyaApi.extension().registerUnloadingHandler(() -> {
            if (this.autoSaveTimer != null) {
                this.autoSaveTimer.stop();
            }
            Treepeater.persistence.saveStatusRegistry(Treepeater.statusRegistry);
            Treepeater.persistence.saveModel(Treepeater.model);
        });

        TreepeaterUI ui = new TreepeaterUI(model);

        montoyaApi.userInterface().registerSuiteTab("Treepeater", ui);

        montoyaApi.userInterface().registerContextMenuItemsProvider(new ContextMenuItemsProvider() {
            @Override
            public List<Component> provideMenuItems(ContextMenuEvent event) {
                JMenuItem item = new JMenuItem("Send to Treepeater (direct)");

                item.addActionListener(l -> sendSelectionToTreepeater(model,
                        event.messageEditorRequestResponse(),
                        event.selectedRequestResponses()));

                JMenuItem sortedItem = new JMenuItem("Send to Treepeater (path-aware)");

                sortedItem.addActionListener(l -> sendSelectionToTreepeaterPathAware(model,
                        event.messageEditorRequestResponse(),
                        event.selectedRequestResponses()));

                JMenuItem manualItem = new JMenuItem("Send to Treepeater (manual)");

                manualItem.addActionListener(l -> sendSelectionToTreepeaterManual(model, ui,
                        event.messageEditorRequestResponse(),
                        event.selectedRequestResponses()));

                return List.of(item, sortedItem, manualItem);
            }
        });

        montoyaApi.userInterface().registerSettingsPanel(new TreepeaterSettingsPanel());

        HotKey sendHotKey = HotKey.hotKey("Send to Treepeater", settings.getSendHotkey());
        HotKeyHandler sendHotKeyHandler = event -> {
            sendSelectionToTreepeater(model,
                event.messageEditorRequestResponse(),
                event.selectedRequestResponses());
        };
        this.sendHotKeyRegistration = montoyaApi.userInterface().registerHotKeyHandler(sendHotKey, sendHotKeyHandler);

        HotKey sendSortedHotKey = HotKey.hotKey("Send to Treepeater (path-aware)", settings.getSendPathAwareHotkey());
        HotKeyHandler sendSortedHotKeyHandler = event -> {
            sendSelectionToTreepeaterPathAware(model,
                event.messageEditorRequestResponse(),
                event.selectedRequestResponses());
        };
        this.sendSortedHotKeyRegistration = montoyaApi.userInterface().registerHotKeyHandler(sendSortedHotKey, sendSortedHotKeyHandler);

        HotKey sendManualHotKey = HotKey.hotKey("Send to Treepeater (manual)", settings.getSendManualHotkey());
        HotKeyHandler sendManualHotKeyHandler = event -> {
            sendSelectionToTreepeaterManual(model, ui,
                event.messageEditorRequestResponse(),
                event.selectedRequestResponses());
        };
        this.sendManualHotKeyRegistration = montoyaApi.userInterface().registerHotKeyHandler(sendManualHotKey, sendManualHotKeyHandler);

        settings.addListener((key, value) -> {
            if (key.equals(TreepeaterSettings.SEND_HOTKEY_SETTING)) {
                this.sendHotKeyRegistration.deregister();
                HotKey newHotkey = HotKey.hotKey("Send to Treepeater", (String) value);
                this.sendHotKeyRegistration = montoyaApi.userInterface().registerHotKeyHandler(newHotkey, sendHotKeyHandler);
            } else if (key.equals(TreepeaterSettings.SEND_PATH_AWARE_HOTKEY_SETTING)) {
                this.sendSortedHotKeyRegistration.deregister();
                HotKey newHotkey = HotKey.hotKey("Send to Treepeater (path-aware)", (String) value);
                this.sendSortedHotKeyRegistration = montoyaApi.userInterface().registerHotKeyHandler(newHotkey, sendSortedHotKeyHandler);
            } else if (key.equals(TreepeaterSettings.SEND_MANUAL_HOTKEY_SETTING)) {
                this.sendManualHotKeyRegistration.deregister();
                HotKey newHotkey = HotKey.hotKey("Send to Treepeater (manual)", (String) value);
                this.sendManualHotKeyRegistration = montoyaApi.userInterface().registerHotKeyHandler(newHotkey, sendManualHotKeyHandler);
            }
        });

        // Save the full state every 2 minutes when there are unsaved changes.
        this.autoSaveTimer = new javax.swing.Timer(120_000, e -> {
            if (Treepeater.dirty) {
                Treepeater.dirty = false;
                Treepeater.persistence.saveStatusRegistry(Treepeater.statusRegistry);
                Treepeater.persistence.saveModel(Treepeater.model);
            }
        });
        this.autoSaveTimer.start();
    }

    public java.util.Set<EnhancedCapability> enhancedCapabilities() {
        return java.util.Set.of(EnhancedCapability.AI_FEATURES);
    }

    public static StatusRegistry getStatusRegistry() {
        return Treepeater.statusRegistry;
    }

    /**
     * Mark the state as dirty so it will be persisted on the next auto-save tick.
     */
    public static void saveState() {
        Treepeater.dirty = true;
    }

    private static List<HttpRequestResponse> collectSelection(
            Optional<MessageEditorHttpRequestResponse> messageEditorRequestResponse,
            List<HttpRequestResponse> selectedRequestResponses) {
        List<HttpRequestResponse> requests = new ArrayList<>();
        messageEditorRequestResponse.ifPresent(e -> requests.add(e.requestResponse()));
        requests.addAll(selectedRequestResponses);
        return requests;
    }

    private static void sendSelectionToTreepeater(
            TreepeaterModel model,
            Optional<MessageEditorHttpRequestResponse> messageEditorRequestResponse,
            List<HttpRequestResponse> selectedRequestResponses) {
        SwingUtilities.invokeLater(() -> {
            for (HttpRequestResponse request : collectSelection(
                    messageEditorRequestResponse, selectedRequestResponses)) {
                model.insertNode(request);
            }
        });
    }

    private static void sendSelectionToTreepeaterPathAware(
            TreepeaterModel model,
            Optional<MessageEditorHttpRequestResponse> messageEditorRequestResponse,
            List<HttpRequestResponse> selectedRequestResponses) {
        SwingUtilities.invokeLater(() -> {
            for (HttpRequestResponse request : collectSelection(
                    messageEditorRequestResponse, selectedRequestResponses)) {
                model.importRequestPathAware(request);
            }
        });
    }

    private static void sendSelectionToTreepeaterManual(
            TreepeaterModel model,
            Component dialogParent,
            Optional<MessageEditorHttpRequestResponse> messageEditorRequestResponse,
            List<HttpRequestResponse> selectedRequestResponses) {
        SwingUtilities.invokeLater(() ->
                ManualImport.run(dialogParent, model, collectSelection(
                        messageEditorRequestResponse, selectedRequestResponses)));
    }

    class CustomTreeModelListener implements TreeModelListener {

        @Override
        public void treeNodesChanged(TreeModelEvent e) {
        }

        @Override
        public void treeNodesInserted(TreeModelEvent e) {
        }

        @Override
        public void treeNodesRemoved(TreeModelEvent e) {
        }

        @Override
        public void treeStructureChanged(TreeModelEvent e) {
        }
    }

}
