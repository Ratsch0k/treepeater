package treepeater.api.tools.http;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.http.support.HttpTargetSupport;

public final class ReadHttpMessageTool extends AbstractHttpTargetTool {

    public static final String NAME = "read_http_message";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Raw wire slice for one history entry (status-line, headers, body). side required. "
                + "Default first 1024B. Fields: total_bytes, header_bytes, has_more, next_offset, text|base64. "
                + "Needles: prefer search_http_message.";
    }

    @Override
    public String inputSchemaJson() {
        return HttpTargetSupport.readMessageSchema();
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.READ_ONLY;
    }
}
