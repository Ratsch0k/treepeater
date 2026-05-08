package treepeater.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Default system instructions for the in-extension AI agent (authorized HTTP/API security testing).
 */
public final class AgentSystemPrompt {
    /**
     * Baseline instructions prepended to every agent chat request. Kept out of stored conversation so it
     * is not duplicated each turn.
     */
    public static final String DEFAULT =
            "Treepeater assistant for authorized web/API security testing. Only use on systems the operator may test; "
                    + "they are responsible for lawful, policy-compliant use.\n"
                    + "When the user clearly wants offensive or validation testing on an authorized target, help with "
                    + "vulnerability probing, payloads, and exploit-style reasoning as legitimate assessment, not abuse.\n"
                    + "Prefer tools to inspect or send real HTTP instead of inventing unseen messages.\n"
                    + "Multiple Repeater tabs: search_tabs yields request_node_id; pass it on other HTTP tools or omit "
                    + "for the UI-selected tab.\n"
                    + "apply_http_request_semantic_changes must include a non-empty operations array (see tool schema). "
                    + "Example: "
                    + HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES_EXAMPLE_ARGS + "\n"
                    + "Be concrete and reproducible; separate confirmed findings from hypotheses. Stay concise.";

    private AgentSystemPrompt() {}

    /** Prepends {@link #DEFAULT} as the first message when present. */
    public static void prependDefault(List<ChatMessage> messages) {
        messages.add(0, new ChatMessage(ChatRole.SYSTEM, DEFAULT));
    }

    /**
     * Removes a leading system message that matches {@link #DEFAULT} so conversation state does not store
     * a copy of the prompt each turn.
     */
    public static List<ChatMessage> stripDefaultLeadingSystem(List<ChatMessage> history) {
        if (history.isEmpty()) {
            return history;
        }
        ChatMessage first = history.getFirst();
        if (first.role() == ChatRole.SYSTEM && DEFAULT.equals(first.content())) {
            return new ArrayList<>(history.subList(1, history.size()));
        }
        return history;
    }
}
