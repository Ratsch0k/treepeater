package treepeater.ai.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistent identity of a model selection: provider id, model id, display name (kept for legacy
 * fallback rendering), and a string-encoded option-value map keyed by {@link ModelOption#id()}.
 *
 * <p>Round-trips through {@link #capture(LlmModelDefinition, LlmModelOptionValues)} on save and
 * {@link #materializeValues(LlmModelDefinition, LlmModelRef)} on load, with the live registry
 * resolving the provider/model on the way back in.
 */
public final class LlmModelRef {
    private final String providerId;
    private final String modelId;
    private final String displayName;
    private final Map<String, String> optionValues;

    public LlmModelRef(String providerId, String modelId, String displayName, Map<String, String> optionValues) {
        this.providerId = providerId != null ? providerId : "";
        this.modelId = modelId != null ? modelId : "";
        this.displayName = displayName != null ? displayName : "";
        this.optionValues =
                optionValues != null
                        ? Collections.unmodifiableMap(new LinkedHashMap<>(optionValues))
                        : Map.of();
    }

    public String providerId() {
        return this.providerId;
    }

    public String modelId() {
        return this.modelId;
    }

    public String displayName() {
        return this.displayName;
    }

    public Map<String, String> optionValues() {
        return this.optionValues;
    }

    /**
     * Captures the current selection + value bag as a serializable ref. Only options declared on
     * the model are written, and only when their value is non-null.
     */
    public static LlmModelRef capture(LlmModelDefinition def, LlmModelOptionValues values) {
        Map<String, String> m = new LinkedHashMap<>();
        if (def != null) {
            for (ModelOption<?> opt : def.supportedOptions()) {
                Object v = values != null ? values.get(opt) : null;
                if (v == null) {
                    continue;
                }
                String encoded = encode(opt, v);
                if (encoded != null) {
                    m.put(opt.id(), encoded);
                }
            }
        }
        return new LlmModelRef(
                def != null ? def.provider().id() : "",
                def != null ? def.modelId() : "",
                def != null ? def.displayName() : "",
                m);
    }

    /**
     * Re-hydrates option values declared on the model from a previously {@link #capture captured}
     * ref. Unknown / unsupported keys are ignored; missing keys fall back to {@code def.defaults()}.
     */
    public static LlmModelOptionValues materializeValues(LlmModelDefinition def, LlmModelRef ref) {
        if (def == null) {
            return LlmModelOptionValues.EMPTY;
        }
        LlmModelOptionValues vals = def.defaults();
        if (ref == null) {
            return vals;
        }
        for (ModelOption<?> opt : def.supportedOptions()) {
            String stored = ref.optionValues().get(opt.id());
            if (stored == null) {
                continue;
            }
            vals = decoded(opt, stored, vals);
        }
        return vals;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String encode(ModelOption opt, Object v) {
        return opt.encode(v);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static LlmModelOptionValues decoded(ModelOption opt, String stored, LlmModelOptionValues vals) {
        Object decoded = opt.decode(stored);
        if (decoded == null) {
            return vals;
        }
        return vals.with(opt, decoded);
    }
}
