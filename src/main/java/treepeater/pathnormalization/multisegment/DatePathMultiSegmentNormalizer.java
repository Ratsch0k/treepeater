package treepeater.pathnormalization.multisegment;

import java.util.List;
import java.util.regex.Pattern;

import treepeater.pathnormalization.MultiSegmentNormalizer;
import treepeater.pathnormalization.PathSegmentUtils;

public final class DatePathMultiSegmentNormalizer implements MultiSegmentNormalizer {

    private static final Pattern YEAR_SEGMENT = Pattern.compile("^(19\\d{2}|20\\d{2}|29\\d{2})$");
    private static final Pattern MONTH_SEGMENT = Pattern.compile("^(0[1-9]|1[0-2])$");
    private static final Pattern DAY_SEGMENT = Pattern.compile("^(0[1-9]|[12]\\d|3[01])$");

    @Override
    public void normalize(List<String> segments, String[] out) {
        for (int i = 0; i < segments.size(); i++) {
            String yearSegment = PathSegmentUtils.stripMatrixParams(segments.get(i));
            if (!YEAR_SEGMENT.matcher(yearSegment).matches()) {
                continue;
            }
            out[i] = ":year";
            if (i + 1 >= segments.size()) {
                continue;
            }
            String monthSegment = PathSegmentUtils.stripMatrixParams(segments.get(i + 1));
            if (!MONTH_SEGMENT.matcher(monthSegment).matches()) {
                continue;
            }
            out[i + 1] = ":month";
            if (i + 2 >= segments.size()) {
                continue;
            }
            String daySegment = PathSegmentUtils.stripMatrixParams(segments.get(i + 2));
            if (DAY_SEGMENT.matcher(daySegment).matches()) {
                out[i + 2] = ":day";
            }
        }
    }
}
