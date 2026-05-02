package treepeater.ai.model;

/**
 * Catalog of well-known {@link ModelOption} keys used by providers. Adding a new generic knob
 * means adding one constant here and declaring it in the provider catalog where supported.
 */
public final class ModelOptions {
    private ModelOptions() {}

    /**
     * Reasoning / output effort level. Anthropic providers expose {@code LOW..MAX}, OpenAI exposes
     * {@code MINIMAL..HIGH}; both clip out-of-range values when building the SDK request.
     */
    public static final EnumOption<EffortLevel> EFFORT =
            new EnumOption<>("effort", "Effort", EffortLevel.class, EffortLevel::label);

    /**
     * Whether the model should run with extended thinking / chain-of-thought output enabled.
     * Currently used by Anthropic 4.6+ models.
     */
    public static final BooleanOption EXTENDED_THINKING =
            new BooleanOption("extendedThinking", "Extended thinking");
}
