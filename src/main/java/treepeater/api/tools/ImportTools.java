package treepeater.api.tools;

import java.net.URI;
import java.util.Base64;
import java.util.OptionalInt;

import com.fasterxml.jackson.databind.JsonNode;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import treepeater.ai.ToolActionLevel;
import treepeater.api.Json;
import treepeater.api.TreepeaterService;
import treepeater.api.TreepeaterTool;
import treepeater.api.TreepeaterToolRegistry;
import treepeater.importing.ImportOptions;
import treepeater.importing.ImportOptions.DirectNameMode;
import treepeater.importing.ImportOptions.DirectPlacement;
import treepeater.importing.ImportOptions.PathAwarePlacement;
import treepeater.settings.StatusRegistry;
import treepeater.settings.TreepeaterSettings;

/**
 * Imports requests supplied by the caller, mirroring the three "Send to Treepeater" context menu actions.
 *
 * <p>Requests must be provided explicitly as raw bytes plus a target; there is deliberately no tool that
 * reads Burp's proxy history, so the API cannot be used to harvest captured traffic.
 */
public final class ImportTools {

    public static final String IMPORT_HTTP_REQUEST = "import_http_request";
    public static final String IMPORT_HTTP_REQUEST_PATH_AWARE = "import_http_request_path_aware";
    public static final String IMPORT_HTTP_REQUEST_INTO_FOLDER = "import_http_request_into_folder";

    private static final String REQUEST_PROPERTIES =
            """
            "base_url":{"type":"string","description":"Target as a URL, e.g. https://api.example.com (host, port and TLS are derived from it)"},\
            "host":{"type":"string","description":"Alternative to base_url"},\
            "port":{"type":"integer","description":"Used with host; defaults to 443 when secure, else 80"},\
            "secure":{"type":"boolean","description":"Used with host; whether to use TLS (default true)"},\
            "request_utf8":{"type":"string","description":"Raw request text; lone LF line endings are converted to CRLF"},\
            "request_base64":{"type":"string","description":"Raw request bytes, base64; use for non-UTF-8 payloads"},\
            "response_utf8":{"type":"string","description":"Optional raw response text to store alongside the request"},\
            "response_base64":{"type":"string","description":"Optional raw response bytes, base64"},\
            "status_id":{"type":"string","description":"Status id from list_statuses; defaults to the project default"}\
            """;

    private static final String DIRECT_PROPERTIES =
            """
            "name_mode":{"type":"string","enum":["URL","PATH","ID","MANUAL"],"description":"How to name the leaf; defaults to the user's direct-import setting"},\
            "manual_name":{"type":"string","description":"Leaf name when name_mode is MANUAL"}\
            """;

    private static final String PATH_AWARE_PROPERTIES =
            """
            "leaf_mode":{"type":"string","enum":["DIRECT","METHOD_FOLDER"],"description":"DIRECT names the leaf after the last path segment; METHOD_FOLDER nests it under a [METHOD] folder"},\
            "base_leaf_name":{"type":"string","description":"Leaf name in METHOD_FOLDER mode (default \\"base\\")"},\
            "lenient_grouping_enabled":{"type":"boolean","description":"Reuse existing folders that carry extra leading segments"},\
            "lenient_grouping_max_skip":{"type":"integer","description":"Leading folder segments lenient grouping may skip (1-10)"},\
            "lenient_grouping_match_threshold_percent":{"type":"integer","description":"Minimum path overlap for lenient grouping, 0-100"},\
            "normalize_dynamic_segments_enabled":{"type":"boolean","description":"Rewrite dynamic segments into placeholders such as :id and :uuid"}\
            """;

    private static final String IMPORT_DIRECT_SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
                    + REQUEST_PROPERTIES
                    + ",\"folder_id\":{\"type\":\"integer\",\"description\":\"Destination folder id; 0 or omitted for the tree root\"},"
                    + DIRECT_PROPERTIES
                    + "},\"additionalProperties\":false}";

    private static final String IMPORT_PATH_AWARE_SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
                    + REQUEST_PROPERTIES
                    + ",\"folder_id\":{\"type\":\"integer\",\"description\":\"Folder to build the path under; 0 or omitted for the tree root\"},"
                    + PATH_AWARE_PROPERTIES
                    + "},\"additionalProperties\":false}";

    private static final String IMPORT_INTO_FOLDER_SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
                    + REQUEST_PROPERTIES
                    + ",\"folder_id\":{\"type\":\"integer\",\"description\":\"Destination folder id, from list_tree or create_folder\"},"
                    + "\"placement\":{\"type\":\"string\",\"enum\":[\"direct\",\"path_aware\"],\"description\":\"How to place the request under the folder (default direct)\"},"
                    + DIRECT_PROPERTIES
                    + ","
                    + PATH_AWARE_PROPERTIES
                    + "},\"required\":[\"folder_id\"],\"additionalProperties\":false}";

    private ImportTools() {}

    public static void register(TreepeaterToolRegistry registry, TreepeaterService service) {
        registry.add(
                new TreepeaterTool(
                        IMPORT_HTTP_REQUEST,
                        "Imports a request you supply as a single leaf, like \"Send to Treepeater (direct)\". "
                                + "Provide base_url plus request_utf8. Returns the new request_node_id.",
                        IMPORT_DIRECT_SCHEMA,
                        ToolActionLevel.WRITE,
                        args -> TreeTools.withArgs(args, parsed -> {
                            int folderId = Json.integer(parsed, "folder_id", "folderId").orElse(0);
                            return service.importRequest(
                                    folderId, buildRequestResponse(parsed), directOptions(parsed));
                        })));

        registry.add(
                new TreepeaterTool(
                        IMPORT_HTTP_REQUEST_PATH_AWARE,
                        "Imports a request you supply using the path-aware importer, like \"Send to Treepeater "
                                + "(path-aware)\": folders are built from the URL path, reusing existing ones. "
                                + "All placement options default to the user's settings. Returns request_node_id "
                                + "and the folder_path that was used.",
                        IMPORT_PATH_AWARE_SCHEMA,
                        ToolActionLevel.WRITE,
                        args -> TreeTools.withArgs(args, parsed -> {
                            int folderId = Json.integer(parsed, "folder_id", "folderId").orElse(0);
                            return service.importRequest(
                                    folderId, buildRequestResponse(parsed), pathAwareOptions(parsed));
                        })));

        registry.add(
                new TreepeaterTool(
                        IMPORT_HTTP_REQUEST_INTO_FOLDER,
                        "Imports a request you supply under an explicit folder_id, the headless equivalent of "
                                + "\"Send to Treepeater (manual)\". Set placement to direct or path_aware.",
                        IMPORT_INTO_FOLDER_SCHEMA,
                        ToolActionLevel.WRITE,
                        args -> TreeTools.withArgs(args, parsed -> {
                            OptionalInt folderId = Json.integer(parsed, "folder_id", "folderId");
                            if (folderId.isEmpty()) {
                                return Json.error("folder_id required");
                            }
                            String placement = Json.text(parsed, "placement");
                            ImportOptions options =
                                    "path_aware".equalsIgnoreCase(placement) || "pathAware".equals(placement)
                                            ? pathAwareOptions(parsed)
                                            : directOptions(parsed);
                            return service.importRequest(
                                    folderId.getAsInt(), buildRequestResponse(parsed), options);
                        })));
    }

    private static String statusId(JsonNode args) {
        String requested = Json.text(args, "status_id", "statusId");
        return requested != null ? requested : StatusRegistry.getDefault().getId();
    }

    private static ImportOptions directOptions(JsonNode args) {
        TreepeaterSettings settings = TreepeaterSettings.getInstance();
        String rawMode = Json.text(args, "name_mode", "nameMode");
        DirectNameMode mode;
        if (rawMode == null) {
            mode = settings.getDirectImportNameMode();
        } else {
            try {
                mode = DirectNameMode.valueOf(rawMode.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "name_mode must be one of URL, PATH, ID, MANUAL");
            }
        }
        String manualName = Json.text(args, "manual_name", "manualName");
        if (mode == DirectNameMode.MANUAL && manualName == null) {
            throw new IllegalArgumentException("manual_name is required when name_mode is MANUAL");
        }
        return new ImportOptions(statusId(args), new DirectPlacement(mode, manualName));
    }

    private static ImportOptions pathAwareOptions(JsonNode args) {
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

    /** Builds a Montoya request/response pair from the caller-supplied target and raw bytes. */
    static HttpRequestResponse buildRequestResponse(JsonNode args) {
        HttpService service = resolveService(args);
        ByteArray requestBytes = requiredBytes(args, "request_utf8", "request_base64");
        HttpRequest request = HttpRequest.httpRequest(service, requestBytes);
        ByteArray responseBytes = optionalBytes(args, "response_utf8", "response_base64");
        HttpResponse response = responseBytes != null ? HttpResponse.httpResponse(responseBytes) : null;
        return HttpRequestResponse.httpRequestResponse(request, response);
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

    /** HTTP requires CRLF, but JSON authors routinely send lone LFs. */
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
