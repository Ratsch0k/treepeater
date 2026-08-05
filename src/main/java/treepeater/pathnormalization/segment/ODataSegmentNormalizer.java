package treepeater.pathnormalization.segment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import treepeater.pathnormalization.SegmentNormalizer;

public final class ODataSegmentNormalizer implements SegmentNormalizer {

    private static final Pattern ODATA_ENTITY_KEY =
            Pattern.compile("^([A-Za-z_][A-Za-z0-9_.]*)\\((.+)\\)$");
    private static final Pattern NAMED_KEY_PART = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)=(.*)$");

    @Override
    public Optional<String> tryNormalize(String segment) {
        if (segment == null || segment.isEmpty()) {
            return Optional.empty();
        }

        Matcher matcher = ODATA_ENTITY_KEY.matcher(segment);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        String entitySet = matcher.group(1);
        String predicate = matcher.group(2);
        return Optional.of(entitySet + "(" + normalizeODataKey(predicate) + ")");
    }

    private static String normalizeODataKey(String predicate) {
        List<String> parts = splitODataKeyParts(predicate);
        List<String> normalizedParts = new ArrayList<>(parts.size());
        for (String part : parts) {
            normalizedParts.add(normalizeODataKeyPart(part.trim()));
        }
        return String.join(",", normalizedParts);
    }

    private static String normalizeODataKeyPart(String part) {
        Matcher named = NAMED_KEY_PART.matcher(part);
        if (named.matches()) {
            String name = named.group(1);
            return name + "=:" + name.toLowerCase();
        }
        return ":id";
    }

    private static List<String> splitODataKeyParts(String predicate) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < predicate.length(); i++) {
            char c = predicate.charAt(i);
            if (c == '\'') {
                inQuotes = !inQuotes;
                current.append(c);
            } else if (c == ',' && !inQuotes) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (!current.isEmpty()) {
            parts.add(current.toString());
        }
        return parts;
    }
}
