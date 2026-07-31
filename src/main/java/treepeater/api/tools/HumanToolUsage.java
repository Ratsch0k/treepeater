package treepeater.api.tools;

/**
 * Transcript line for a tool: short {@code title} plus optional {@code detail} (what will change, key
 * arguments). {@code detail} is empty for read-only tools and for large payloads omitted from the card.
 */
public record HumanToolUsage(String title, String detail) {
    public HumanToolUsage {
        title = title != null ? title : "";
        detail = detail != null ? detail : "";
    }
}
