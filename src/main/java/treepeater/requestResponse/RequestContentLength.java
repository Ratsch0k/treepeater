package treepeater.requestResponse;

import java.util.List;
import java.util.Locale;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;

/**
 * Keeps the {@code Content-Length} request header in sync with the raw message body bytes.
 */
final class RequestContentLength {
    private RequestContentLength() {
    }

    /**
     * Sets {@code Content-Length} to the length of {@link HttpRequest#body()}. If the request uses chunked
     * transfer coding, the message is returned unchanged (sending both chunked encoding and Content-Length is invalid).
     */
    static HttpRequest syncContentLengthToBody(HttpRequest request) {
        if (request == null) {
            return null;
        }
        if (hasChunkedTransferEncoding(request)) {
            return request;
        }
        int len = bodyLengthBytes(request);
        return request.withRemovedHeader("Content-Length").withHeader("Content-Length", Integer.toString(len));
    }

    private static boolean hasChunkedTransferEncoding(HttpRequest request) {
        for (HttpHeader h : headers(request)) {
            if ("Transfer-Encoding".equalsIgnoreCase(h.name())) {
                String v = h.value();
                if (v != null && v.toLowerCase(Locale.ROOT).contains("chunked")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<HttpHeader> headers(HttpRequest request) {
        try {
            List<HttpHeader> h = request.headers();
            return h != null ? h : List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static int bodyLengthBytes(HttpRequest request) {
        try {
            ByteArray b = request.body();
            if (b == null) {
                return 0;
            }
            byte[] raw = b.getBytes();
            return raw != null ? raw.length : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
