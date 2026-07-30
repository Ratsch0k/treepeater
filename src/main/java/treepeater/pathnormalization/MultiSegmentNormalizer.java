package treepeater.pathnormalization;

import java.util.List;

/**
 * Normalizes path segments that span multiple consecutive segments.
 */
public interface MultiSegmentNormalizer {

    /**
     * Writes placeholders into {@code out}; leave {@code out[i] == null} when unhandled.
     */
    void normalize(List<String> segments, String[] out);
}
