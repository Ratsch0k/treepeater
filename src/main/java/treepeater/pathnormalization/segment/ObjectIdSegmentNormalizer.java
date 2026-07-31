package treepeater.pathnormalization.segment;

import java.util.regex.Pattern;

import treepeater.pathnormalization.RegexSegmentNormalizer;

public final class ObjectIdSegmentNormalizer extends RegexSegmentNormalizer {

    private static final Pattern OBJECT_ID = Pattern.compile("^[0-9a-f]{24}$");

    public ObjectIdSegmentNormalizer() {
        super(OBJECT_ID, ":objectId");
    }
}
