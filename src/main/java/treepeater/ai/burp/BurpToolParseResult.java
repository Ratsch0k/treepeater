package treepeater.ai.burp;

import java.util.List;

import treepeater.ai.ChatToolCall;

/**
 * Result of parsing synthetic {@link BurpSyntheticToolCallParser} blocks out of assistant text.
 */
public record BurpToolParseResult(String visibleText, List<ChatToolCall> toolCalls) {
    public BurpToolParseResult {
        if (visibleText == null) {
            visibleText = "";
        }
        if (toolCalls == null) {
            toolCalls = List.of();
        }
    }
}
