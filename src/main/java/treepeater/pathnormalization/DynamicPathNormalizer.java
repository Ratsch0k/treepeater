package treepeater.pathnormalization;

import java.util.ArrayList;
import java.util.List;

import treepeater.pathnormalization.guard.ApiVersionGuardNormalizer;
import treepeater.pathnormalization.guard.PlaceholderGuardNormalizer;
import treepeater.pathnormalization.multisegment.DatePathMultiSegmentNormalizer;
import treepeater.pathnormalization.segment.HexDigestSegmentNormalizer;
import treepeater.pathnormalization.segment.IsoDateSegmentNormalizer;
import treepeater.pathnormalization.segment.IsoDateTimeSegmentNormalizer;
import treepeater.pathnormalization.segment.NumericIdSegmentNormalizer;
import treepeater.pathnormalization.segment.ObjectIdSegmentNormalizer;
import treepeater.pathnormalization.segment.ODataSegmentNormalizer;
import treepeater.pathnormalization.segment.UlidSegmentNormalizer;
import treepeater.pathnormalization.segment.UuidSegmentNormalizer;

/**
 * Rewrites dynamic URL path segments into type-named placeholders for path-aware import.
 */
public final class DynamicPathNormalizer {

    private static final List<MultiSegmentNormalizer> MULTI_SEGMENT_NORMALIZERS = List.of(
            new DatePathMultiSegmentNormalizer());

    private static final List<SegmentNormalizer> SEGMENT_NORMALIZERS = List.of(
            new PlaceholderGuardNormalizer(),
            new ApiVersionGuardNormalizer(),
            new ODataSegmentNormalizer(),
            new IsoDateTimeSegmentNormalizer(),
            new IsoDateSegmentNormalizer(),
            new UuidSegmentNormalizer(),
            new ObjectIdSegmentNormalizer(),
            new HexDigestSegmentNormalizer(),
            new UlidSegmentNormalizer(),
            new NumericIdSegmentNormalizer());

    private DynamicPathNormalizer() {
    }

    /**
     * Returns a new list with dynamic segments replaced by placeholders.
     */
    public static List<String> normalize(List<String> segments) {
        if (segments == null || segments.isEmpty()) {
            return segments == null ? List.of() : List.copyOf(segments);
        }

        String[] marked = new String[segments.size()];
        for (MultiSegmentNormalizer normalizer : MULTI_SEGMENT_NORMALIZERS) {
            normalizer.normalize(segments, marked);
        }

        List<String> normalized = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            if (marked[i] != null) {
                normalized.add(marked[i]);
            } else {
                normalized.add(normalizeSegment(segments.get(i)));
            }
        }
        return normalized;
    }

    /**
     * Classifies a single segment; returns the input unchanged when nothing matches.
     */
    public static String normalizeSegment(String segment) {
        if (segment == null || segment.isEmpty()) {
            return segment;
        }

        for (SegmentNormalizer normalizer : SEGMENT_NORMALIZERS) {
            var result = normalizer.tryNormalize(segment);
            if (result.isPresent()) {
                return result.get();
            }
        }
        return segment;
    }
}
