package treepeater.api.tools.http;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.AbstractEditorTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;
import treepeater.api.tools.http.support.HttpEditorService;

public final class SetHttpRequestBodyTool extends AbstractEditorTool {

    public static final String NAME = "set_http_request_body";

    private static final String SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
                    + HttpToolSchemas.REQUEST_NODE_ID_PROPERTY
                    + ",\"body_utf8\":{\"type\":\"string\"},\"body_base64\":{\"type\":\"string\"}},\"additionalProperties\":false}";

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
        return SCHEMA;
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.WRITE;
    }

    @Override
    public String invoke(ToolInvocation inv) {
        return capResult(HttpEditorService.setHttpRequestBody(requireContext(inv), inv.args()));
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        String nodeSuf = HttpToolLabels.formatRequestNodeIdSuffix(inv.args(), labelCtx.uiSelectedRequestNodeId());
        return new HumanToolUsage("Setting full request body" + nodeSuf, "");
    }
}
