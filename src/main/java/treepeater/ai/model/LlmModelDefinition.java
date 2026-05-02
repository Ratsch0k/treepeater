package treepeater.ai.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single, generic LLM model entry. Each provider declares its own catalog of these instances —
 * UI components, persistence, and the agent runtime work exclusively with this type and never know
 * about provider-specific SDK enums.
 *
 * <p>{@link #toString()} returns {@link #displayName()} so the type can be dropped into a Swing
 * combo box without a custom renderer.
 */
public final class LlmModelDefinition {
    private final LlmProvider provider;
    private final String modelId;
    private final String displayName;
    private final List<ModelOption<?>> supportedOptions;
    private final Map<ModelOption<?>, List<?>> allowedValues;
    private final LlmModelOptionValues defaults;

    public LlmModelDefinition(
            LlmProvider provider,
            String modelId,
            String displayName,
            List<ModelOption<?>> supportedOptions,
            Map<ModelOption<?>, List<?>> allowedValues,
            LlmModelOptionValues defaults) {
        this.provider = provider;
        this.modelId = modelId != null ? modelId : "";
        this.displayName = displayName != null ? displayName : "";
        this.supportedOptions = supportedOptions != null ? List.copyOf(supportedOptions) : List.of();
        this.allowedValues =
                allowedValues != null
                        ? Collections.unmodifiableMap(new LinkedHashMap<>(allowedValues))
                        : Map.of();
        this.defaults = defaults != null ? defaults : LlmModelOptionValues.EMPTY;
    }

    public LlmProvider provider() {
        return this.provider;
    }

    /** Identifier sent to the provider SDK (e.g. {@code claude-opus-4-7-...}). */
    public String modelId() {
        return this.modelId;
    }

    /** Human-readable name shown in menus / combos. */
    public String displayName() {
        return this.displayName;
    }

    public List<ModelOption<?>> supportedOptions() {
        return this.supportedOptions;
    }

    /** Default values for this model's supported options; serves as the initial UI state. */
    public LlmModelOptionValues defaults() {
        return this.defaults;
    }

    /**
     * Returns the subset of an enum option's values this model accepts, or empty if all values of
     * the option's enum type are allowed.
     */
    @SuppressWarnings("unchecked")
    public <T> List<T> allowedValues(ModelOption<T> option) {
        List<?> list = this.allowedValues.get(option);
        return list != null ? (List<T>) list : List.of();
    }

    @Override
    public String toString() {
        return this.displayName;
    }
}
