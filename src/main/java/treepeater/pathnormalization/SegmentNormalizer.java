package treepeater.pathnormalization;

import java.util.Optional;

/**
 * Normalizes a single URL path segment into a placeholder when a rule applies.
 */
public interface SegmentNormalizer {

    /**
     * @return empty if this rule does not apply; otherwise the normalized segment
     *         (which may equal the input for guard rules that stop further matching)
     */
    Optional<String> tryNormalize(String segment);
}
