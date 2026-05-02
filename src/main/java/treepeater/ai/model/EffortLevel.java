package treepeater.ai.model;

/**
 * Generic, provider-neutral reasoning/output effort levels. Each {@link LlmProvider} implementation
 * clips/maps its supported subset to its SDK enum (e.g. Anthropic excludes {@link #MINIMAL},
 * OpenAI excludes {@link #MAX}).
 */
public enum EffortLevel {
    MINIMAL("Minimal"),
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    MAX("Max");

    private final String label;

    EffortLevel(String label) {
        this.label = label;
    }

    public String label() {
        return this.label;
    }
}
