package treepeater.pathnormalization.segment;

import java.util.regex.Pattern;

import treepeater.pathnormalization.RegexSegmentNormalizer;

public final class HexDigestSegmentNormalizer extends RegexSegmentNormalizer {

    private static final Pattern HEX_DIGEST = Pattern.compile(
            "^(?:[0-9a-fA-F]{32}|[0-9a-fA-F]{40}|[0-9a-fA-F]{56}|[0-9a-fA-F]{64}|[0-9a-fA-F]{96}|[0-9a-fA-F]{128})$");

    public HexDigestSegmentNormalizer() {
        super(HEX_DIGEST, ":hash");
    }
}
