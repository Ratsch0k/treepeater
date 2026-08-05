package treepeater.pathnormalization.segment;

import java.util.regex.Pattern;

import treepeater.pathnormalization.RegexSegmentNormalizer;

public final class NumericIdSegmentNormalizer extends RegexSegmentNormalizer {

    private static final Pattern NUMERIC = Pattern.compile("^\\d+$");

    public NumericIdSegmentNormalizer() {
        super(NUMERIC, ":id");
    }
}
