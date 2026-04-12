package treepeater.requestResponse;

import java.time.LocalDateTime;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

/**
 * Observes programmatic updates to the request/response shown in a {@link RequestResponsePanel}.
 * Each callback receives the full current snapshot (editors + active history receive time).
 */
public interface RequestResponseChangeListener {
    void onRequestChanged(HttpRequest request, HttpResponse response, LocalDateTime responseReceivedAt);

    void onResponseChanged(HttpRequest request, HttpResponse response, LocalDateTime responseReceivedAt);
}
