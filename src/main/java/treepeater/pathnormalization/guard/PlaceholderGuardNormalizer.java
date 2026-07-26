package treepeater.pathnormalization.guard;

import java.util.Optional;
import java.util.regex.Pattern;

import treepeater.pathnormalization.PathSegmentUtils;
import treepeater.pathnormalization.SegmentNormalizer;

public final class PlaceholderGuardNormalizer implements SegmentNormalizer {

    private static final Pattern PLACEHOLDER = Pattern.compile("^:[A-Za-z][A-Za-z0-9]*$");

    @Override
    public Optional<String> tryNormalize(String segment) {
        if (segment == null || segment.isEmpty()) {
            return Optional.empty();
        }
        String value = PathSegmentUtils.stripMatrixParams(segment);
        if (PLACEHOLDER.matcher(value).matches()) {
            return Optional.of(segment);
        }
        return Optional.empty();
    }
}
