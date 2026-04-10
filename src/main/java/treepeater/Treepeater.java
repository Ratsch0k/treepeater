package treepeater;
import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.Registration;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;
import burp.api.montoya.ui.hotkey.HotKey;
import burp.api.montoya.ui.hotkey.HotKeyHandler;
import treepeater.persistence.TreepeaterPersistence;
import treepeater.settings.StatusRegistry;
import treepeater.settings.TreepeaterSettings;
import treepeater.settings.TreepeaterSettingsPanel;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.List;
import java.util.Optional;

public class Treepeater implements BurpExtension {
    public static MontoyaApi api;
    private static StatusRegistry statusRegistry;
    private static TreepeaterModel model;
    private static TreepeaterPersistence persistence;
    DefaultMutableTreeNode root;

    private Registration sendHotKeyRegistration;

    @Override
    public void initialize(MontoyaApi montoyaApi) {
        Treepeater.api =  montoyaApi;
        montoyaApi.extension().setName("Treepeater");

        TreepeaterSettings.init(montoyaApi.persistence().preferences());
        TreepeaterSettings settings = TreepeaterSettings.getInstance();

        Treepeater.persistence = new TreepeaterPersistence(montoyaApi.persistence());

        try {
            api.logging().logToOutput("Loading status registry");
            Treepeater.statusRegistry = Treepeater.persistence.loadStatusRegistry();
            Treepeater.api.logging().logToOutput("Status registry loaded");
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

        montoyaApi.extension().registerUnloadingHandler(() -> Treepeater.persistence.saveModel(Treepeater.model));

        TreepeaterUI ui = new TreepeaterUI(model);

        montoyaApi.userInterface().registerSuiteTab("Treepeater", ui);

        montoyaApi.userInterface().registerContextMenuItemsProvider(new ContextMenuItemsProvider() {
            @Override
            public List<Component> provideMenuItems(ContextMenuEvent event) {
                JMenuItem item = new JMenuItem("Send to Treepeater");

                item.addActionListener(l -> sendSelectionToTreepeater(montoyaApi, model,
                        event.messageEditorRequestResponse(),
                        event.selectedRequestResponses()));

                return List.of(item);
            }
        });

        montoyaApi.userInterface().registerSettingsPanel(new TreepeaterSettingsPanel());

        HotKey sendHotKey = HotKey.hotKey("Send to Treepeater", settings.getSendHotkey());
        HotKeyHandler sendHotKeyHandler = event -> {
            sendSelectionToTreepeater(montoyaApi, model,
                event.messageEditorRequestResponse(),
                event.selectedRequestResponses());
        };
        this.sendHotKeyRegistration = montoyaApi.userInterface().registerHotKeyHandler(sendHotKey, sendHotKeyHandler);

        settings.addListener((key, value) -> {
            Treepeater.api.logging().logToOutput("Settings changed: " + key);
            if (key.equals(TreepeaterSettings.SEND_HOTKEY_SETTING)) {
                this.sendHotKeyRegistration.deregister();
                HotKey newHotkey = HotKey.hotKey("Send to Treepeater", (String) value);
                this.sendHotKeyRegistration = montoyaApi.userInterface().registerHotKeyHandler(newHotkey, sendHotKeyHandler);
            }
        });
    }

    public static StatusRegistry getStatusRegistry() {
        return Treepeater.statusRegistry;
    }

    /**
     * Save the current state of the model using a background thread.
     */
    public static void saveState() {
        SwingUtilities.invokeLater(() ->  {
            Treepeater.api.logging().logToOutput("Saving state");
            Treepeater.persistence.saveStatusRegistry(Treepeater.statusRegistry);
            Treepeater.persistence.saveModel(Treepeater.model);
        });
    }

    private static void sendSelectionToTreepeater(
            MontoyaApi api,
            TreepeaterModel model,
            Optional<MessageEditorHttpRequestResponse> messageEditorRequestResponse,
            List<HttpRequestResponse> selectedRequestResponses) {
        api.logging().logToOutput("Sent to Treepeater");
        messageEditorRequestResponse.ifPresent(e -> model.insertNode(e.requestResponse()));
        for (HttpRequestResponse r : selectedRequestResponses) {
            model.insertNode(r);
        }
    }


    class CustomTreeSelectionListener implements TreeSelectionListener {

        @Override
        public void valueChanged(TreeSelectionEvent e) {
            Treepeater.api.logging().logToOutput("Selected request: " + e);
        }

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
