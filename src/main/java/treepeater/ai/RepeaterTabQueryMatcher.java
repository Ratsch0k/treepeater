package treepeater.ai;

import java.util.Locale;
import java.util.Set;

/**
 * Filter for {@link HttpTargetTools#SEARCH_TABS}: OR of live URL/method rules and tab title substring match.
 */
public final class RepeaterTabQueryMatcher {

    private static final Set<String> KNOWN_METHODS =
            Set.of(
                    "GET",
                    "POST",
                    "PUT",
                    "PATCH",
                    "DELETE",
                    "HEAD",
                    "OPTIONS",
                    "TRACE",
                    "CONNECT",
                    "PROPFIND",
                    "PROPPATCH",
                    "MKCOL",
                    "COPY",
                    "MOVE",
                    "LOCK",
                    "UNLOCK");

    private RepeaterTabQueryMatcher() {}

    /**
     * @param queryRaw non-null trimmed filter
     * @param method HTTP method from live request (may be empty)
     * @param url full URL from live request (may be empty)
     * @param title tab display title
     */
    public static boolean matches(String queryRaw, String method, String url, String title) {
        if (queryRaw == null || queryRaw.isEmpty()) {
            return true;
        }
        String q = queryRaw.trim();
        if (q.isEmpty()) {
            return true;
        }

        String titleSafe = title != null ? title : "";
        String qLower = q.toLowerCase(Locale.ROOT);
        if (!titleSafe.isEmpty() && titleSafe.toLowerCase(Locale.ROOT).contains(qLower)) {
            return true;
        }

        String methodU = method != null ? method.trim().toUpperCase(Locale.ROOT) : "";
        String urlSafe = url != null ? url : "";
        String urlLower = urlSafe.toLowerCase(Locale.ROOT);

        String[] parts = q.split("\\s+", 2);
        String firstUpper = parts[0].toUpperCase(Locale.ROOT);
        if (KNOWN_METHODS.contains(firstUpper)) {
            if (parts.length == 1) {
                return methodU.equals(firstUpper);
            }
            String rest = parts[1].trim();
            if (rest.isEmpty()) {
                return false;
            }
            if (!methodU.equals(firstUpper)) {
                return false;
            }
            return urlLower.contains(rest.toLowerCase(Locale.ROOT));
        }

        return urlLower.contains(qLower);
    }
}
