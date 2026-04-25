package treepeater.ai;

import treepeater.ai.anthropic.AnthropicModelSupport;
import treepeater.ai.AiModelOption.Kind;

/**
 * Per-request model controls from the agent chat model-options menu, applied when building
 * {@link treepeater.ai.StreamingChatClient} instances.
 */
public record LlmRequestOptions(
        OpenAiReasoningEffort openAiReasoningEffort,
        AnthropicOutputEffort anthropicOutputEffort,
        boolean anthropicExtendedThinking) {
    public static final LlmRequestOptions DEFAULTS =
            new LlmRequestOptions(
                    OpenAiReasoningEffort.MEDIUM, AnthropicOutputEffort.MEDIUM, true);

    public static boolean supportsOpenAiReasoningMenu(AiModelOption opt) {
        return opt != null && opt.kind() == Kind.OPENAI;
    }

    public static boolean supportsAnthropicOutputEffortMenu(AiModelOption opt) {
        return opt != null && opt.kind() == Kind.ANTHROPIC;
    }

    public static boolean supportsAnthropicExtendedThinkingMenu(AiModelOption opt) {
        if (opt == null || opt.kind() != Kind.ANTHROPIC) {
            return false;
        }
        return AnthropicModelSupport.supportsExtendedThinking(opt.anthropicModel());
    }

    public static boolean anyConfigurable(AiModelOption opt) {
        return supportsOpenAiReasoningMenu(opt)
                || supportsAnthropicOutputEffortMenu(opt)
                || supportsAnthropicExtendedThinkingMenu(opt);
    }
}
