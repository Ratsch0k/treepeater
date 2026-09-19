package treepeater.api.tools.importing;

/** Shared JSON Schema fragments for import tools. */
final class ImportSchemas {

    static final String REQUEST_PROPERTIES =
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

    static final String DIRECT_PROPERTIES =
            """
            "name_mode":{"type":"string","enum":["URL","PATH","ID","MANUAL"],"description":"How to name the leaf; defaults to the user's direct-import setting"},\
            "manual_name":{"type":"string","description":"Leaf name when name_mode is MANUAL"}\
            """;

    static final String PATH_AWARE_PROPERTIES =
            """
            "leaf_mode":{"type":"string","enum":["DIRECT","METHOD_FOLDER"],"description":"DIRECT names the leaf after the last path segment; METHOD_FOLDER nests it under a [METHOD] folder"},\
            "base_leaf_name":{"type":"string","description":"Leaf name in METHOD_FOLDER mode (default \\"base\\")"},\
            "lenient_grouping_enabled":{"type":"boolean","description":"Reuse existing folders that carry extra leading segments"},\
            "lenient_grouping_max_skip":{"type":"integer","description":"Leading folder segments lenient grouping may skip (1-10)"},\
            "lenient_grouping_match_threshold_percent":{"type":"integer","description":"Minimum path overlap for lenient grouping, 0-100"},\
            "normalize_dynamic_segments_enabled":{"type":"boolean","description":"Rewrite dynamic segments into placeholders such as :id and :uuid"}\
            """;

    private ImportSchemas() {}
}
