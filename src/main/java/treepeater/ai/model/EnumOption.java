package treepeater.ai.model;

import java.util.function.Function;

/**
 * {@link ModelOption} backed by an enum. The {@code valueLabel} function provides per-value
 * display text rendered as radio-button menu items.
 */
public final class EnumOption<E extends Enum<E>> implements ModelOption<E> {
    private final String id;
    private final String menuLabel;
    private final Class<E> type;
    private final Function<E, String> valueLabel;

    public EnumOption(String id, String menuLabel, Class<E> type, Function<E, String> valueLabel) {
        this.id = id;
        this.menuLabel = menuLabel;
        this.type = type;
        this.valueLabel = valueLabel;
    }

    @Override
    public String id() {
        return this.id;
    }

    @Override
    public String menuLabel() {
        return this.menuLabel;
    }

    @Override
    public Class<E> type() {
        return this.type;
    }

    public String valueLabel(E value) {
        return this.valueLabel.apply(value);
    }

    @Override
    public String encode(E value) {
        return value == null ? null : value.name();
    }

    @Override
    public E decode(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            return Enum.valueOf(this.type, stored);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
