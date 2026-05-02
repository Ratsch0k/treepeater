package treepeater.requestResponse.toolbar.ai;

import java.awt.Component;
import java.util.List;

import treepeater.Treepeater;
import treepeater.ai.AgentMode;
import treepeater.ai.AgentTabMention;
import treepeater.ai.AgentToolContext;
import treepeater.ai.ChatTooling;
import treepeater.ai.StreamingChatClient;
import treepeater.ai.model.LlmModelDefinition;
import treepeater.ai.model.LlmModelOptionValues;

/**
 * Services provided by {@link AIToolbarTab} for each nested chat (agent) panel.
 */
public interface AIChatHost {
    /**
     * Build a streaming client for the given model + per-request option values. The host delegates
     * to {@link LlmModelDefinition#provider()}'s {@code createClient} — the agent panel has no
     * provider-specific knowledge beyond the model selection itself.
     */
    StreamingChatClient clientForSelectedModel(LlmModelDefinition model, LlmModelOptionValues values);

    ChatTooling chatTooling(AgentMode mode);

    void runOnEdtAndWait(Runnable r) throws Exception;

    Component dialogParent();

    void logError(Throwable t);

    /**
     * Repeater request/response access for the active tab, used to preview tool mutations in the tool card. May be
     * {@code null} or return {@code null} when the host has no in-editor request.
     */
    default AgentToolContext agentToolContextForToolPreview() {
        return null;
    }

    /**
     * Open repeater tabs for the {@code @}-mention popup (slash-separated paths). Default: none.
     */
    default List<AgentTabMention> agentTabMentionsForAtPopup() {
        return List.of();
    }

    static boolean isBurpAiEnabled() {
        return Treepeater.api != null && Treepeater.api.ai().isEnabled();
    }
}
