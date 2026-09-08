package treepeater.api.tools.http;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.AbstractEditorTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;
import treepeater.api.tools.http.support.HttpEditorService;

public final class ApplyHttpRequestSemanticChangesTool extends AbstractEditorTool {

    public static final String NAME = "apply_http_request_semantic_changes";

    /** Minimal valid payload example for errors and the agent system prompt. */
    public static final String EXAMPLE_ARGS =
            "{\"operations\":[{\"type\":\"header\",\"action\":\"set\",\"key\":\"X-Test\",\"value\":\"1\"}]}";

    public static final int MAX_OPERATIONS = 32;

    private static final String ITEMS_ALLOF =
            "["
                    + "{\"if\":{\"properties\":{\"type\":{\"const\":\"header\"},\"action\":{\"const\":\"set\"}},\"required\":[\"type\",\"action\"]},\"then\":{\"required\":[\"key\",\"value\"]}}"
                    + ",{\"if\":{\"properties\":{\"type\":{\"const\":\"header\"},\"action\":{\"const\":\"remove\"}},\"required\":[\"type\",\"action\"]},\"then\":{\"required\":[\"key\"]}}"
                    + ",{\"if\":{\"properties\":{\"type\":{\"const\":\"cookie\"},\"action\":{\"const\":\"set\"}},\"required\":[\"type\",\"action\"]},\"then\":{\"required\":[\"key\",\"value\"]}}"
                    + ",{\"if\":{\"properties\":{\"type\":{\"const\":\"cookie\"},\"action\":{\"const\":\"remove\"}},\"required\":[\"type\",\"action\"]},\"then\":{\"required\":[\"key\"]}}"
                    + ",{\"if\":{\"properties\":{\"type\":{\"const\":\"json\"},\"action\":{\"const\":\"set\"}},\"required\":[\"type\",\"action\"]},\"then\":{\"required\":[\"path\",\"value\"]}}"
                    + ",{\"if\":{\"properties\":{\"type\":{\"const\":\"json\"},\"action\":{\"const\":\"remove\"}},\"required\":[\"type\",\"action\"]},\"then\":{\"required\":[\"path\"]}}"
                    + ",{\"if\":{\"properties\":{\"type\":{\"const\":\"xml\"},\"action\":{\"const\":\"set\"}},\"required\":[\"type\",\"action\"]},\"then\":{\"required\":[\"path\",\"value\"]}}"
                    + ",{\"if\":{\"properties\":{\"type\":{\"const\":\"xml\"},\"action\":{\"const\":\"remove\"}},\"required\":[\"type\",\"action\"]},\"then\":{\"required\":[\"path\"]}}"
                    + ",{\"if\":{\"properties\":{\"type\":{\"const\":\"method\"},\"action\":{\"const\":\"set\"}},\"required\":[\"type\",\"action\"]},\"then\":{\"required\":[\"value\"],\"properties\":{\"value\":{\"type\":\"string\"}}}}"
                    + ",{\"if\":{\"properties\":{\"type\":{\"const\":\"url\"},\"action\":{\"const\":\"set\"}},\"required\":[\"type\",\"action\"]},\"then\":{\"required\":[\"value\"],\"properties\":{\"value\":{\"type\":\"string\"}}}}"
                    + "]";

    private static final String OPERATION_ITEM_SCHEMA =
            "{\"type\":\"object\",\"required\":[\"type\",\"action\"],"
                    + "\"properties\":{"
                    + "\"type\":{\"type\":\"string\",\"enum\":[\"header\",\"cookie\",\"json\",\"xml\",\"method\",\"url\"]},"
                    + "\"action\":{\"type\":\"string\",\"enum\":[\"set\",\"remove\"]},"
                    + "\"key\":{\"type\":\"string\",\"description\":\"Header/cookie; empty for method|url set\"},"
                    + "\"path\":{\"type\":\"string\",\"description\":\"JSON Pointer (json) or XPath (xml)\"},"
                    + "\"value\":{}"
                    + "},"
                    + "\"allOf\":"
                    + ITEMS_ALLOF
                    + ",\"additionalProperties\":false}";

    private static final String SCHEMA =
            "{\"type\":\"object\",\"required\":[\"operations\"],\"properties\":{"
                    + HttpToolSchemas.REQUEST_NODE_ID_PROPERTY
                    + ",\"operations\":{\"type\":\"array\",\"minItems\":1,\"maxItems\":"
                    + MAX_OPERATIONS
                    + ",\"items\":"
                    + OPERATION_ITEM_SCHEMA
                    + "}},\"additionalProperties\":false}";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return "Batch edit current request. Required operations[] (non-empty). Per op: type header|cookie|json|"
                + "xml|method|url + action set|remove; fields per JSON Schema. remove: omit value. "
                + "json set value:null = literal null. path: RFC6901 (json) or XPath (xml). method|url set: "
                + "empty key. Bad body yields error op_index; fix via read/set body. Example in system prompt.";
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
        return capResult(HttpEditorService.applyHttpRequestSemanticChanges(requireContext(inv), inv.args()));
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        String nodeSuf = HttpToolLabels.formatRequestNodeIdSuffix(inv.args(), labelCtx.uiSelectedRequestNodeId());
        return new HumanToolUsage(
                "Apply semantic request changes" + nodeSuf, HttpToolLabels.formatSemanticOperationsHumanDetail(inv.args()));
    }
}
