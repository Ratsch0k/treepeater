package treepeater.ai;

import java.util.ArrayList;
import java.util.List;

import treepeater.ai.model.LlmModelOptionValues;
import treepeater.ai.model.LlmModelRef;
import treepeater.ai.model.LlmRegistry;

/**
 * One persisted agent sub-tab: title, transcript, mode, and the model selection (provider id +
 * model id + per-model option values) wrapped in a single {@link LlmModelRef}.
 */
public record AgentChatSession(
        String title,
        List<ChatMessage> conversation,
        AgentMode agentMode,
        LlmModelRef modelRef) {
    public AgentChatSession {
        if (title == null) {
            title = "Chat";
        }
        if (conversation == null) {
            conversation = List.of();
        } else {
            conversation = List.copyOf(conversation);
        }
        if (agentMode == null) {
            agentMode = AgentMode.ASK;
        }
        if (modelRef == null) {
            modelRef = LlmModelRef.capture(LlmRegistry.defaultModel(), LlmModelOptionValues.EMPTY);
        }
    }

    public AgentChatSession copy() {
        return new AgentChatSession(
                this.title, new ArrayList<>(this.conversation), this.agentMode, this.modelRef);
    }
}
