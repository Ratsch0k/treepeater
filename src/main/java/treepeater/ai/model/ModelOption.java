package treepeater.ai.model;

/**
 * Typed key for a per-model configurable option. Each variant owns its own persistence codec
 * ({@link #encode(Object)} / {@link #decode(String)}) so that {@link LlmModelRef} can store
 * option values as plain string maps without provider-specific knowledge.
 *
 * <p>Adding a new option type later (e.g. an integer slider) means adding one new sealed
 * permittee plus one render branch in {@code AIAgentChatPanel.showModelOptionsMenu}.
 */
public sealed interface ModelOption<T> permits EnumOption, BooleanOption {
    /** Stable id used for persistence and comparison. */
    String id();

    /** Label shown for this option in the model-options menu. */
    String menuLabel();

    /** Value type for compile-time safety in {@link LlmModelOptionValues}. */
    Class<T> type();

    /** Encode a value to a string for persistence. Must roundtrip with {@link #decode(String)}. */
    String encode(T value);

    /** Decode a previously {@link #encode(Object) encoded} string; {@code null} on failure. */
    T decode(String stored);
}
