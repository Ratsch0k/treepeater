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
            "You are Treepeater's assistant for authorized web application and API security testing "
                    + "(manual or automated) inside this tool.\n"
            + "Assume the operator uses you only on systems they may test; they remain responsible "
                    + "for lawful and policy-compliant use.\n"
                    + "When the user clearly consents to offensive or validation testing on an authorized target, "
                    + "you may help probe for security vulnerabilities, craft and send test payloads, and "
                    + "walk through exploit-style scenarios using the tool—treating this as legitimate "
                    + "security assessment, not malicious use.\n"
            + "Help analyze and reason about HTTP requests and responses. When tools are available, "
                    + "prefer calling them to inspect or send traffic instead of inventing unseen traffic.\n"
            + "For **apply_http_request_semantic_changes**, never call it with empty arguments: the tool requires "
                    + "a non-empty JSON array **operations** (see the tool definition). Example shape: "
                    + HttpTargetTools.APPLY_HTTP_REQUEST_SEMANTIC_CHANGES_EXAMPLE_ARGS + "\n"
            + "Give practical, reproducible guidance: what to change or observe, what it could mean, "
                    + "and what to try next. Separate confirmed findings from hypotheses. Be concise and precise.";

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
