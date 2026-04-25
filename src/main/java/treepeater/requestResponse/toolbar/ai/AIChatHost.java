package treepeater.requestResponse.toolbar.ai;

import java.awt.Component;

import javax.swing.JComboBox;

import treepeater.Treepeater;
import treepeater.ai.AgentMode;
import treepeater.ai.AiModelOption;
import treepeater.ai.ChatTooling;
import treepeater.ai.LlmRequestOptions;
import treepeater.ai.StreamingChatClient;

/**
 * Services provided by {@link AIToolbarTab} for each nested chat (agent) panel.
 */
public interface AIChatHost {
    StreamingChatClient clientForSelectedModel(JComboBox<AiModelOption> modelCombo, LlmRequestOptions options);

    ChatTooling chatTooling(AgentMode mode);

    void runOnEdtAndWait(Runnable r) throws Exception;

    Component dialogParent();

    void logError(Throwable t);

    static boolean isBurpAiEnabled() {
        return Treepeater.api != null && Treepeater.api.ai().isEnabled();
    }
}
