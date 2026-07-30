package treepeater.pathnormalization.segment;

import java.util.Optional;
import java.util.regex.Pattern;

import treepeater.pathnormalization.PathSegmentUtils;
import treepeater.pathnormalization.SegmentNormalizer;

public final class IsoDateSegmentNormalizer implements SegmentNormalizer {

    private static final Pattern ISO_DATE_DASHED = Pattern.compile(
            "^(19\\d{2}|20\\d{2}|29\\d{2})-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])$");
    private static final Pattern ISO_DATE_COMPACT = Pattern.compile(
            "^(19\\d{2}|20\\d{2}|29\\d{2})(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])$");
    private static final Pattern ISO_DATE_YEAR_MONTH = Pattern.compile(
            "^(19\\d{2}|20\\d{2}|29\\d{2})-(0[1-9]|1[0-2])$");

    @Override
    public Optional<String> tryNormalize(String segment) {
        if (segment == null || segment.isEmpty()) {
            return Optional.empty();
        }

        String value = PathSegmentUtils.stripMatrixParams(segment);
        if (value.isEmpty()) {
            return Optional.empty();
        }

        if (ISO_DATE_DASHED.matcher(value).matches()
                || ISO_DATE_COMPACT.matcher(value).matches()
                || ISO_DATE_YEAR_MONTH.matcher(value).matches()) {
            return Optional.of(":date");
        }
        return Optional.empty();
    }
}
