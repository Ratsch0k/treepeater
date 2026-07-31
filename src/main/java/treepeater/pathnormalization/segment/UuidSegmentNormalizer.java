package treepeater.pathnormalization.segment;

import java.util.regex.Pattern;

import treepeater.pathnormalization.RegexSegmentNormalizer;

public final class UuidSegmentNormalizer extends RegexSegmentNormalizer {

    private static final Pattern UUID = Pattern.compile(
            "^\\{?[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\}?$");

    public UuidSegmentNormalizer() {
        super(UUID, ":uuid");
    }
}
