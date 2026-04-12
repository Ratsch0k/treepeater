package treepeater.requestResponse;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.requests.HttpRequest;

/**
 * Mutable host/port/scheme/SNI state for the request panel, kept in sync with the edited
 * {@link HttpRequest} and the edit-target dialog.
 */
final class RequestPanelHttpTarget {
    private String host;
    private int port;
    private boolean https;
    private boolean sniEnabled;

    void initFromRequest(HttpRequest request) {
        if (request == null) {
            return;
        }
        HttpService service;
        try {
            service = request.httpService();
        } catch (Exception ignored) {
            return;
        }
        if (service == null) {
            return;
        }

        this.host = service.host();
        this.port = service.port();
        this.https = service.secure();
        if (this.port <= 0) {
            this.port = this.https ? 443 : 80;
        }
        if (!this.https) {
            this.sniEnabled = false;
        } else if (this.host != null && !this.host.isBlank()) {
            this.sniEnabled = true;
        }
    }

    HttpRequest applyToRequest(HttpRequest request) {
        if (request == null) {
            return null;
        }
        String h = (this.host == null) ? "" : this.host.trim();
        if (h.isEmpty()) {
            return request;
        }
        try {
            HttpService service = HttpService.httpService(h, this.port, this.https);
            return request.withService(service);
        } catch (IllegalArgumentException ex) {
            return request;
        }
    }

    /**
     * Long form used for history list labels and send snapshot (scheme://host:port …).
     */
    String statusLineLabel() {
        String h = (this.host == null) ? "" : this.host.trim();
        if (h.isEmpty()) {
            return "";
        }
        String scheme = this.https ? "https" : "http";
        return scheme + "://" + h + ":" + this.port
                + (this.https ? (this.sniEnabled ? " (SNI)" : " (no SNI)") : "");
    }

    void applyFromDialog(TargetSettings settings) {
        this.host = settings.host();
        this.port = settings.port();
        this.https = settings.https();
        this.sniEnabled = settings.sniEnabled();
    }

    String getHost() {
        return this.host;
    }

    int getPort() {
        return this.port;
    }

    boolean isHttps() {
        return this.https;
    }

    boolean isSniEnabled() {
        return this.sniEnabled;
    }
}
