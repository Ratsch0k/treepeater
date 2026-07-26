package treepeater.pathnormalization.segment;

import java.util.regex.Pattern;

import treepeater.pathnormalization.RegexSegmentNormalizer;

public final class IsoDateTimeSegmentNormalizer extends RegexSegmentNormalizer {

    private static final Pattern ISO_DATETIME = Pattern.compile(
            "^(19\\d{2}|20\\d{2}|29\\d{2})-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])"
                    + "T\\d{2}:\\d{2}(:\\d{2}(?:\\.\\d+)?)?(?:Z|[+-]\\d{2}:\\d{2})$");

    public IsoDateTimeSegmentNormalizer() {
        super(ISO_DATETIME, ":timestamp");
    }
}
