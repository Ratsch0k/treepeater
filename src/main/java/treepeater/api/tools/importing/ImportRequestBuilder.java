package treepeater.api.tools.importing;

import java.net.URI;
import java.util.Base64;

import com.fasterxml.jackson.databind.JsonNode;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import treepeater.api.Json;
import treepeater.importing.ImportOptions;
import treepeater.importing.ImportOptions.DirectNameMode;
import treepeater.importing.ImportOptions.DirectPlacement;
import treepeater.importing.ImportOptions.PathAwarePlacement;
import treepeater.settings.StatusRegistry;
import treepeater.settings.TreepeaterSettings;

/** Builds Montoya request/response pairs and import options from tool arguments. */
public final class ImportRequestBuilder {

    private ImportRequestBuilder() {}

    public static HttpRequestResponse buildRequestResponse(JsonNode args) {
        HttpService service = resolveService(args);
        ByteArray requestBytes = requiredBytes(args, "request_utf8", "request_base64");
        HttpRequest request = HttpRequest.httpRequest(service, requestBytes);
        ByteArray responseBytes = optionalBytes(args, "response_utf8", "response_base64");
        HttpResponse response = responseBytes != null ? HttpResponse.httpResponse(responseBytes) : null;
        return HttpRequestResponse.httpRequestResponse(request, response);
    }

    public static ImportOptions directOptions(JsonNode args) {
        TreepeaterSettings settings = TreepeaterSettings.getInstance();
        String rawMode = Json.text(args, "name_mode", "nameMode");
        DirectNameMode mode;
        if (rawMode == null) {
            mode = settings.getDirectImportNameMode();
        } else {
            try {
                mode = DirectNameMode.valueOf(rawMode.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("name_mode must be one of URL, PATH, ID, MANUAL");
            }
        }
        String manualName = Json.text(args, "manual_name", "manualName");
        if (mode == DirectNameMode.MANUAL && manualName == null) {
            throw new IllegalArgumentException("manual_name is required when name_mode is MANUAL");
        }
        return new ImportOptions(statusId(args), new DirectPlacement(mode, manualName));
    }

    public static ImportOptions pathAwareOptions(JsonNode args) {
        TreepeaterSettings settings = TreepeaterSettings.getInstance();
        String rawLeafMode = Json.text(args, "leaf_mode", "leafMode");
        String leafMode;
        if (rawLeafMode == null) {
            leafMode = settings.getImportLeafMode();
        } else if (TreepeaterSettings.IMPORT_LEAF_MODE_METHOD_FOLDER.equalsIgnoreCase(rawLeafMode)) {
            leafMode = TreepeaterSettings.IMPORT_LEAF_MODE_METHOD_FOLDER;
        } else if (TreepeaterSettings.IMPORT_LEAF_MODE_DIRECT.equalsIgnoreCase(rawLeafMode)) {
            leafMode = TreepeaterSettings.IMPORT_LEAF_MODE_DIRECT;
        } else {
            throw new IllegalArgumentException("leaf_mode must be DIRECT or METHOD_FOLDER");
        }

        String baseLeafName = Json.text(args, "base_leaf_name", "baseLeafName");
        boolean lenientEnabled =
                Json.bool(args, "lenient_grouping_enabled", "lenientGroupingEnabled")
                        .orElseGet(settings::isImportGroupingFolderReconciliationEnabled);
        int maxSkip =
                Json.integer(args, "lenient_grouping_max_skip", "lenientGroupingMaxSkip")
                        .orElseGet(settings::getImportGroupingFolderReconciliationMaxSkip);
        int threshold =
                Json.integer(
                                args,
                                "lenient_grouping_match_threshold_percent",
                                "lenientGroupingMatchThresholdPercent")
                        .orElseGet(settings::getImportGroupingFolderReconciliationMatchThresholdPercent);
        boolean normalize =
                Json.bool(args, "normalize_dynamic_segments_enabled", "normalizeDynamicSegmentsEnabled")
                        .orElseGet(settings::isImportNormalizeDynamicSegmentsEnabled);

        return new ImportOptions(
                statusId(args),
                new PathAwarePlacement(
                        leafMode,
                        baseLeafName != null ? baseLeafName : settings.getImportBaseLeafName(),
                        lenientEnabled,
                        Math.max(1, Math.min(10, maxSkip)),
                        Math.max(0, Math.min(100, threshold)),
                        normalize));
    }

    public static String importTargetDetail(JsonNode args) {
        String baseUrl = Json.text(args, "base_url", "baseUrl", "url");
        if (baseUrl != null && !baseUrl.isBlank()) {
            return treepeater.api.tools.ToolHumanUsage.quotedSnippet(baseUrl, 96);
        }
        String host = Json.text(args, "host");
        if (host != null && !host.isBlank()) {
            boolean secure = Json.bool(args, "secure", "https").orElse(Boolean.TRUE);
            int port = Json.integer(args, "port").orElse(secure ? 443 : 80);
            int defaultPort = secure ? 443 : 80;
            return port == defaultPort ? host : host + ":" + port;
        }
        return "";
    }

    private static String statusId(JsonNode args) {
        String requested = Json.text(args, "status_id", "statusId");
        return requested != null ? requested : StatusRegistry.getDefault().getId();
    }

    private static HttpService resolveService(JsonNode args) {
        String baseUrl = Json.text(args, "base_url", "baseUrl", "url");
        if (baseUrl != null) {
            URI uri;
            try {
                uri = new URI(baseUrl);
            } catch (Exception e) {
                throw new IllegalArgumentException("invalid base_url: " + e.getMessage());
            }
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("base_url must start with http:// or https://");
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("base_url must include a host");
            }
            boolean secure = "https".equalsIgnoreCase(scheme);
            int port = uri.getPort();
            if (port < 0) {
                port = secure ? 443 : 80;
            }
            return HttpService.httpService(host, port, secure);
        }

        String host = Json.text(args, "host");
        if (host == null) {
            throw new IllegalArgumentException("provide base_url, or host with optional port and secure");
        }
        boolean secure = Json.bool(args, "secure", "https").orElse(Boolean.TRUE);
        int port = Json.integer(args, "port").orElse(secure ? 443 : 80);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        return HttpService.httpService(host, port, secure);
    }

    private static ByteArray requiredBytes(JsonNode args, String textKey, String base64Key) {
        ByteArray bytes = optionalBytes(args, textKey, base64Key);
        if (bytes == null) {
            throw new IllegalArgumentException("provide " + textKey + " or " + base64Key);
        }
        return bytes;
    }

    private static ByteArray optionalBytes(JsonNode args, String textKey, String base64Key) {
        JsonNode text = Json.first(args, textKey, camel(textKey));
        JsonNode base64 = Json.first(args, base64Key, camel(base64Key));
        if (text != null && base64 != null) {
            throw new IllegalArgumentException("provide either " + textKey + " or " + base64Key + ", not both");
        }
        if (text != null) {
            if (!text.isTextual()) {
                throw new IllegalArgumentException(textKey + " must be a string");
            }
            return ByteArray.byteArray(normalizeLineEndings(text.asText()));
        }
        if (base64 != null) {
            if (!base64.isTextual()) {
                throw new IllegalArgumentException(base64Key + " must be a base64 string");
            }
            try {
                return ByteArray.byteArray(Base64.getDecoder().decode(base64.asText().trim()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("invalid " + base64Key);
            }
        }
        return null;
    }

    private static String normalizeLineEndings(String raw) {
        if (raw.indexOf('\r') >= 0) {
            return raw;
        }
        return raw.replace("\n", "\r\n");
    }

    private static String camel(String snake) {
        StringBuilder out = new StringBuilder(snake.length());
        boolean upper = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                upper = true;
                continue;
            }
            out.append(upper ? Character.toUpperCase(c) : c);
            upper = false;
        }
        return out.toString();
    }
}
