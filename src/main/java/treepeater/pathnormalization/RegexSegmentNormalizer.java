package treepeater.pathnormalization;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Base class for segment normalizers that match a regex against the matrix-stripped segment value.
 */
public abstract class RegexSegmentNormalizer implements SegmentNormalizer {

    private final Pattern pattern;
    private final String placeholder;
    private final Predicate<String> extraCheck;

    protected RegexSegmentNormalizer(Pattern pattern, String placeholder) {
        this(pattern, placeholder, value -> true);
    }

    protected RegexSegmentNormalizer(Pattern pattern, String placeholder, Predicate<String> extraCheck) {
        this.pattern = pattern;
        this.placeholder = placeholder;
        this.extraCheck = extraCheck;
    }

    @Override
    public Optional<String> tryNormalize(String segment) {
        if (segment == null || segment.isEmpty()) {
            return Optional.empty();
        }

        String value = PathSegmentUtils.stripMatrixParams(segment);
        if (value.isEmpty()) {
            return Optional.empty();
        }

        if (this.pattern.matcher(value).matches() && this.extraCheck.test(value)) {
            return Optional.of(this.placeholder);
        }
        return Optional.empty();
    }
}
