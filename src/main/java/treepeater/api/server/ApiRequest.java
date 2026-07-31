package treepeater.api.server;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * An inbound API call, reduced to the parts the handlers actually use.
 *
 * <p>Deliberately free of any HTTP library type so the handlers stay independent of the transport
 * Burp runs on a trimmed JRE without the {@code jdk.httpserver} module.
 */
public record ApiRequest(
        String method, String path, String rawQuery, Map<String, String> headers, String body) {

    public ApiRequest {
        method = method != null ? method.toUpperCase(Locale.ROOT) : "";
        path = path != null ? path : "";
        body = body != null ? body : "";
        headers = headers != null ? lowerCaseKeys(headers) : Map.of();
    }

    /** Header value by case-insensitive name, or {@code null} when absent. */
    public String header(String name) {
        return name != null ? this.headers.get(name.toLowerCase(Locale.ROOT)) : null;
    }

    public boolean isMethod(String candidate) {
        return candidate != null && candidate.equalsIgnoreCase(this.method);
    }

    private static Map<String, String> lowerCaseKeys(Map<String, String> source) {
        return source.entrySet().stream()
                .collect(
                        Collectors.toUnmodifiableMap(
                                entry -> entry.getKey().toLowerCase(Locale.ROOT),
                                Map.Entry::getValue,
                                (first, duplicate) -> first));
    }
}
