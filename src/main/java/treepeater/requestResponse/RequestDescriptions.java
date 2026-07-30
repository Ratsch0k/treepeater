package treepeater.requestResponse;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;

/** Safe accessors for request metadata used in import UI and naming. */
public final class RequestDescriptions {

    private RequestDescriptions() {
    }

    public static String method(HttpRequestResponse requestResponse) {
        if (requestResponse == null) {
            return "?";
        }
        HttpRequest request = requestResponse.request();
        if (request == null) {
            return "?";
        }
        try {
            String method = request.method();
            if (method != null && !method.isBlank()) {
                return method.trim().toUpperCase();
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return "?";
    }

    public static String method(HttpRequest request) {
        if (request == null) {
            return "?";
        }
        try {
            String method = request.method();
            if (method != null && !method.isBlank()) {
                return method.trim();
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return "?";
    }

    public static String url(HttpRequestResponse requestResponse) {
        if (requestResponse == null) {
            return "?";
        }
        HttpRequest request = requestResponse.request();
        return request != null ? url(request) : "?";
    }

    public static String url(HttpRequest request) {
        if (request == null) {
            return "?";
        }
        try {
            String url = request.url();
            if (url != null && !url.isBlank()) {
                return url.trim();
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        try {
            String path = request.pathWithoutQuery();
            if (path != null && !path.isBlank()) {
                return path.trim();
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        try {
            String path = request.path();
            if (path != null && !path.isBlank()) {
                return path.trim();
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return "?";
    }
}
