package treepeater.api.tools.http;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.http.support.HttpTargetSupport;

public final class SearchHttpMessageTool extends AbstractHttpTargetTool {

    public static final String NAME = "search_http_message";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Regex on raw bytes (offsets align with read_http_message). max_matches 10 dflt /100 max; "
                + "context_bytes 64 dflt /512 max. scope headers|body|all. Java Pattern (?i)(?m)(?s). "
                + "Latin-1 indexing; pattern <=1024 chars; scan <=1MB (scan_limited_bytes when clipped).";
    }

    @Override
    public String inputSchemaJson() {
        return HttpTargetSupport.searchMessageSchema();
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.READ_ONLY;
    }
}
