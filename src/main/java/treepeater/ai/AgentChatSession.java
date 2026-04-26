package treepeater.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * One persisted agent sub-tab: title, transcript, model/mode, and per-model LLM options.
 */
public record AgentChatSession(
        String title,
        List<ChatMessage> conversation,
        AgentMode agentMode,
        AiModelRef modelRef,
        LlmRequestOptions llmRequestOptions) {
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
            modelRef = AiModelRef.fromOption(AiModelOption.defaultChoices().get(0));
        }
        if (llmRequestOptions == null) {
            llmRequestOptions = LlmRequestOptions.DEFAULTS;
        }
    }

    public AgentChatSession copy() {
        return new AgentChatSession(
                this.title, new ArrayList<>(this.conversation), this.agentMode, this.modelRef, this.llmRequestOptions);
    }
}
