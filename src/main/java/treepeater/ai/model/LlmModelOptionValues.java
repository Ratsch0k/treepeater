package treepeater.ai.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable typed value bag keyed by {@link ModelOption}. Used by the UI to hold the user's current
 * choices and by {@link LlmProvider#createClient} to read them when building an SDK request.
 */
public final class LlmModelOptionValues {
    public static final LlmModelOptionValues EMPTY = new LlmModelOptionValues(Map.of());

    private final Map<ModelOption<?>, Object> values;

    private LlmModelOptionValues(Map<ModelOption<?>, Object> values) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public static <T> LlmModelOptionValues of(ModelOption<T> k, T v) {
        Map<ModelOption<?>, Object> m = new LinkedHashMap<>();
        m.put(k, v);
        return new LlmModelOptionValues(m);
    }

    public static <T1, T2> LlmModelOptionValues of(
            ModelOption<T1> k1, T1 v1, ModelOption<T2> k2, T2 v2) {
        Map<ModelOption<?>, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return new LlmModelOptionValues(m);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(ModelOption<T> key) {
        return (T) this.values.get(key);
    }

    public <T> T getOrDefault(ModelOption<T> key, T defaultValue) {
        T v = this.get(key);
        return v != null ? v : defaultValue;
    }

    public <T> LlmModelOptionValues with(ModelOption<T> key, T value) {
        Map<ModelOption<?>, Object> m = new LinkedHashMap<>(this.values);
        if (value == null) {
            m.remove(key);
        } else {
            m.put(key, value);
        }
        return new LlmModelOptionValues(m);
    }

    public Set<ModelOption<?>> definedKeys() {
        return this.values.keySet();
    }
}
