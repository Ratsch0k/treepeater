package treepeater.api.tools.http;

import com.fasterxml.jackson.databind.JsonNode;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.AbstractEditorTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;
import treepeater.api.tools.http.support.HttpEditorService;

public final class PatchHttpRequestBodyLinesTool extends AbstractEditorTool {

    public static final String NAME = "patch_http_request_body_lines";

    private static final String SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
                    + HttpToolSchemas.REQUEST_NODE_ID_PROPERTY
                    + ",\"start_line\":{\"type\":\"integer\",\"minimum\":1},\"end_line\":{\"type\":\"integer\",\"minimum\":1},\"content\":{\"type\":\"string\",\"description\":\"New lines; \\\\R linebreaks\"}},\"required\":[\"start_line\",\"end_line\",\"content\"],\"additionalProperties\":false}";

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
        return SCHEMA;
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.WRITE;
    }

    @Override
    public String invoke(ToolInvocation inv) {
        return capResult(HttpEditorService.patchHttpRequestBodyLines(requireContext(inv), inv.args()));
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        JsonNode args = inv.args();
        String nodeSuf = HttpToolLabels.formatRequestNodeIdSuffix(args, labelCtx.uiSelectedRequestNodeId());
        int sl = HttpToolLabels.intArg(args, "start_line", "startLine");
        int el = HttpToolLabels.intArg(args, "end_line", "endLine");
        JsonNode contentNode = HttpToolLabels.firstArg(args, "content");
        String content = contentNode != null && !contentNode.isNull() ? contentNode.asText() : "";
        String preview = HttpToolLabels.singleLinePreview(content, 200);
        String det = "Lines " + sl + "–" + el + (preview.isEmpty() ? "" : " · new text: " + preview);
        return new HumanToolUsage("Patch request body line range" + nodeSuf, det);
    }
}
