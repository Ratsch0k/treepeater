import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;
import burp.api.montoya.ui.contextmenu.MessageEditorHttpRequestResponse;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Treepeater implements BurpExtension {
    public static MontoyaApi api;
    DefaultMutableTreeNode root;

    @Override
    public void initialize(MontoyaApi montoyaApi) {
        Treepeater.api =  montoyaApi;
        montoyaApi.extension().setName("Treepeater");

        TreepeaterModel model = new TreepeaterModel();

        TreepeaterUI ui = new TreepeaterUI(model);

        montoyaApi.userInterface().registerSuiteTab("Treepeater", ui);

        montoyaApi.userInterface().registerContextMenuItemsProvider(new ContextMenuItemsProvider() {
            @Override
            public List<Component> provideMenuItems(ContextMenuEvent event) {
                JMenuItem item = new JMenuItem("Send to Treepeater");
                
                item.addActionListener(l -> {
                    montoyaApi.logging().logToOutput("Sent to Treepeater");

                    Optional<MessageEditorHttpRequestResponse> requestAndResponse = event.messageEditorRequestResponse();

                    if (requestAndResponse.isPresent()) {
                        model.insertNode(requestAndResponse.get().requestResponse());
                    }

                    for (HttpRequestResponse r : event.selectedRequestResponses()) {
                        model.insertNode(r);
                    }
                });
                

                return List.of(item);
            }
        });
    }



    class CustomTreeSelectionListener implements TreeSelectionListener {

        @Override
        public void valueChanged(TreeSelectionEvent e) {
            Treepeater.this.api.logging().logToOutput("Selected request: " + e);
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


    