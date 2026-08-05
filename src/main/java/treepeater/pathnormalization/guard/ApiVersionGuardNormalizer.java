package treepeater.pathnormalization.guard;

import java.util.Optional;
import java.util.regex.Pattern;

import treepeater.pathnormalization.PathSegmentUtils;
import treepeater.pathnormalization.SegmentNormalizer;

public final class ApiVersionGuardNormalizer implements SegmentNormalizer {

    private static final Pattern API_VERSION = Pattern.compile("^v\\d+(?:alpha|beta|rc|p\\d*)?$");

    @Override
    public Optional<String> tryNormalize(String segment) {
        if (segment == null || segment.isEmpty()) {
            return Optional.empty();
        }
        String value = PathSegmentUtils.stripMatrixParams(segment);
        if (API_VERSION.matcher(value).matches()) {
            return Optional.of(segment);
        }
        return Optional.empty();
    }
}
