package treepeater.api.tools.http;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.http.support.HttpTargetSupport;

public final class ReplaceInHttpRequestBodyTool extends AbstractHttpTargetTool {

    public static final String NAME = "replace_in_http_request_body";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Literal find/replace in current request body (UTF-8). Non-UTF-8: set_http_request_body+base64. "
                + "Default max_replacements=1 needs single match; replace_all=all.";
    }

    @Override
    public String inputSchemaJson() {
        return HttpTargetSupport.replaceBodySchema();
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.WRITE;
    }
}
