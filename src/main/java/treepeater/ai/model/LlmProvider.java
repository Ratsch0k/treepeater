package treepeater.ai.model;

import java.util.List;
import java.util.Optional;

import treepeater.ai.StreamingChatClient;

/**
 * One LLM backend (Burp's built-in AI, Anthropic, Azure OpenAI, Ollama, ...). Each provider owns its
 * catalog of {@link LlmModelDefinition} instances and is the only place that knows how to translate
 * a generic model + value bag into a concrete {@link StreamingChatClient}.
 */
public interface LlmProvider {
    /** Stable id used for persistence and registry lookup ({@code "anthropic"}, {@code "openai"}, ...). */
    String id();

    /** Human-readable provider name for UI grouping or status messages. */
    String displayName();

    /**
     * Built-in catalog. May be dynamic (e.g. {@code OllamaProvider} reads from settings) or a fixed
     * list. The order returned drives display order in the model combo.
     */
    List<LlmModelDefinition> models();

    /**
     * Reason this model cannot be invoked right now (missing API key, missing endpoint, Burp AI
     * disabled, ...). Empty when the model is ready to use. Used by the agent panel to show a
     * blocking dialog before sending.
     */
    default Optional<UnavailableReason> unavailableReason(LlmModelDefinition model) {
        return Optional.empty();
    }

    /** Provider's chance to materialize a model definition for a persisted ref it doesn't list. */
    default Optional<LlmModelDefinition> synthesize(LlmModelRef ref) {
        return Optional.empty();
    }

    /**
     * Build a streaming client for a single chat. Throws {@link IllegalStateException} if required
     * settings are missing — callers should use {@link #unavailableReason} first to gate the UI.
     */
    StreamingChatClient createClient(LlmModelDefinition model, LlmModelOptionValues values);

    /**
     * Title + body shown in the "configure your provider" dialog when {@link #unavailableReason}
     * returns non-empty.
     */
    record UnavailableReason(String title, String message) {}
}
