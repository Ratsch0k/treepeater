package treepeater.pathnormalization;

public final class PathSegmentUtils {

    private PathSegmentUtils() {
    }

    public static String stripMatrixParams(String segment) {
        int semi = segment.indexOf(';');
        return semi >= 0 ? segment.substring(0, semi) : segment;
    }

    public static boolean containsDigit(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
