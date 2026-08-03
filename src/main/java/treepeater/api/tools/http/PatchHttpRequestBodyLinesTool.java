package treepeater.api.tools.http;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.http.support.HttpTargetSupport;

public final class PatchHttpRequestBodyLinesTool extends AbstractHttpTargetTool {

    public static final String NAME = "patch_http_request_body_lines";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Replace 1-based inclusive line range in current body (UTF-8; Java \\R). Binary: set_http_request_body.";
    }

    @Override
    public String inputSchemaJson() {
        return HttpTargetSupport.patchLinesSchema();
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.WRITE;
    }
}
