package treepeater.ai.anthropic;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Model-id heuristics shared with the UI; kept in sync with
 * {@link AnthropicStreamingChatClient#applyExtendedThinkingConfig}.
 */
public final class AnthropicModelSupport {
    private static final Pattern CLAUDE_SONNET_4_DATED =
            Pattern.compile("claude-sonnet-4-(\\d+)-\\d{8}");
    private static final Pattern CLAUDE_OPUS_4_DATED = Pattern.compile("claude-opus-4-(\\d+)-\\d{8}");

    private AnthropicModelSupport() {}

    /**
     * True when the Messages API can use extended thinking for this model (adaptive 4.6+ or fixed-budget
     * 4.x Sonnet/Opus). Haiku and unrelated ids are false.
     */
    public static boolean supportsExtendedThinking(String modelId) {
        String m = modelId == null ? "" : modelId.toLowerCase(Locale.ROOT);
        if (m.isEmpty()) {
            return false;
        }
        return isClaude46FamilyOrLater(m) || likelySupportsFixedBudgetExtendedThinking(m);
    }

    static boolean isClaude46FamilyOrLater(String m) {
        int sonnetMinor = minorFromClaude4Dated(m, CLAUDE_SONNET_4_DATED);
        if (sonnetMinor >= 6) {
            return true;
        }
        int opusMinor = minorFromClaude4Dated(m, CLAUDE_OPUS_4_DATED);
        if (opusMinor >= 6) {
            return true;
        }
        return m.contains("sonnet-4-6")
                || m.contains("sonnet-4-7")
                || m.contains("opus-4-6")
                || m.contains("opus-4-7");
    }

    private static int minorFromClaude4Dated(String m, Pattern p) {
        Matcher x = p.matcher(m);
        if (!x.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(x.group(1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static boolean likelySupportsFixedBudgetExtendedThinking(String m) {
        if (m.contains("haiku")) {
            return false;
        }
        return m.contains("sonnet-4") || m.contains("opus-4");
    }
}
