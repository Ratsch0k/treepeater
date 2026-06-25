package treepeater.requestResponse;

/**
 * Per-tab send configuration for {@link RequestResponsePanel}. Immutable; the options dialog returns a new
 * instance on OK.
 */
public record RequestResponseOptions(boolean updateContentLength) {
    public static final RequestResponseOptions DEFAULT = new RequestResponseOptions(true);
}
