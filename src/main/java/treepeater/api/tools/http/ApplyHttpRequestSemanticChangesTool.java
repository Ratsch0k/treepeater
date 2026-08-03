package treepeater.api.tools.http;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.http.support.HttpTargetSupport;

public final class ApplyHttpRequestSemanticChangesTool extends AbstractHttpTargetTool {

    public static final String NAME = "apply_http_request_semantic_changes";

    /** Minimal valid payload example for errors and the agent system prompt. */
    public static final String EXAMPLE_ARGS =
            "{\"operations\":[{\"type\":\"header\",\"action\":\"set\",\"key\":\"X-Test\",\"value\":\"1\"}]}";

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
        return HttpTargetSupport.applySemanticChangesSchema();
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.WRITE;
    }
}
