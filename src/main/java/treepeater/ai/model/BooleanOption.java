package treepeater.ai.model;

/**
 * {@link ModelOption} backed by a boolean toggle. Rendered as a {@link javax.swing.JCheckBoxMenuItem}.
 */
public final class BooleanOption implements ModelOption<Boolean> {
    private final String id;
    private final String menuLabel;

    public BooleanOption(String id, String menuLabel) {
        this.id = id;
        this.menuLabel = menuLabel;
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
    public Class<Boolean> type() {
        return Boolean.class;
    }

    @Override
    public String encode(Boolean value) {
        return value == null ? null : Boolean.toString(value);
    }

    @Override
    public Boolean decode(String stored) {
        if (stored == null) {
            return null;
        }
        return Boolean.parseBoolean(stored);
    }
}
