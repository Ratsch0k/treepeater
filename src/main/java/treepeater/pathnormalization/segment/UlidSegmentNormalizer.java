package treepeater.pathnormalization.segment;

import java.util.regex.Pattern;

import treepeater.pathnormalization.PathSegmentUtils;
import treepeater.pathnormalization.RegexSegmentNormalizer;

public final class UlidSegmentNormalizer extends RegexSegmentNormalizer {

    private static final Pattern ULID = Pattern.compile("^[0-9A-HJKMNP-TV-Z]{26}$");

    public UlidSegmentNormalizer() {
        super(ULID, ":ulid", PathSegmentUtils::containsDigit);
    }
}
