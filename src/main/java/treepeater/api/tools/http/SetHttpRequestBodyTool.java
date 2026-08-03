package treepeater.api.tools.http;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.http.support.HttpTargetSupport;

public final class SetHttpRequestBodyTool extends AbstractHttpTargetTool {

    public static final String NAME = "set_http_request_body";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Replace full current body. One of body_utf8|body_base64 (aliases bodyUtf8|bodyBase64). "
                + "Object/array body_utf8 serializes to compact JSON. Arbitrary bytes: base64.";
    }

    @Override
    public String inputSchemaJson() {
        return HttpTargetSupport.setBodySchema();
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.WRITE;
    }
}
