package treepeater.ai;

import java.util.List;

/**
 * A single chat turn with a role and text content. Assistant turns may include parallel tool calls;
 * {@link ChatRole#TOOL} turns carry a tool result tied to a prior {@link ChatToolCall#id()}.
 */
public record ChatMessage(
        ChatRole role,
        String content,
        List<ChatToolCall> assistantToolCalls,
        String toolCallId) {
    public ChatMessage(ChatRole role, String content) {
        this(role, content, List.of(), null);
    }

    public ChatMessage {
        if (content == null) {
            content = "";
        }
        if (assistantToolCalls == null) {
            assistantToolCalls = List.of();
        }
    }

    public boolean hasAssistantToolCalls() {
        return !this.assistantToolCalls.isEmpty();
    }
}
