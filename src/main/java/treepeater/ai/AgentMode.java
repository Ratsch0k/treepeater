package treepeater.ai;

/**
 * How often the user is prompted before HTTP target tools run. Does not block actions; it only changes which tools
 * require approval (Ask: write/send; Helper: send only; Autonomous: none).
 */
public enum AgentMode {
    ASK("Ask"),
    HELPER("Helper"),
    AUTONOMOUS("Auto");

    private final String label;

    AgentMode(String label) {
        this.label = label;
    }

    /** Short label for combo boxes and UI. */
    public String label() {
        return this.label;
    }

    @Override
    public String toString() {
        return this.label;
    }
}
