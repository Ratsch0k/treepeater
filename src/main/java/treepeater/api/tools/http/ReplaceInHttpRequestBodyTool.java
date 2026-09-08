package treepeater.api.tools.http;

import com.fasterxml.jackson.databind.JsonNode;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.AbstractEditorTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;
import treepeater.api.tools.http.support.HttpEditorService;

public final class ReplaceInHttpRequestBodyTool extends AbstractEditorTool {

    public static final String NAME = "replace_in_http_request_body";

    public static final int MAX_REPLACEMENTS = 100_000;

    private static final String SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
                    + HttpToolSchemas.REQUEST_NODE_ID_PROPERTY
                    + ",\"old_text\":{\"type\":\"string\"},\"new_text\":{\"type\":\"string\"},\"max_replacements\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":%d,\"default\":1},\"replace_all\":{\"type\":\"boolean\",\"default\":false}},\"required\":[\"old_text\",\"new_text\"],\"additionalProperties\":false}"
                    .formatted(MAX_REPLACEMENTS);

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
        return SCHEMA;
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.WRITE;
    }

    @Override
    public String invoke(ToolInvocation inv) {
        return capResult(HttpEditorService.replaceInHttpRequestBody(requireContext(inv), inv.args()));
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        JsonNode args = inv.args();
        String nodeSuf = HttpToolLabels.formatRequestNodeIdSuffix(args, labelCtx.uiSelectedRequestNodeId());
        String oldT = HttpToolLabels.textArg(args, "old_text", "oldText");
        String newT = "";
        JsonNode newNode = HttpToolLabels.firstArg(args, "new_text", "newText");
        if (newNode != null && !newNode.isNull()) {
            newT = newNode.asText();
        }
        boolean replaceAll = args != null && args.has("replace_all") && args.get("replace_all").asBoolean(false);
        int maxRep = 1;
        if (!replaceAll) {
            JsonNode m = HttpToolLabels.firstArg(args, "max_replacements", "maxReplacements");
            if (m != null) {
                maxRep = HttpToolLabels.intArg(args, "max_replacements", "maxReplacements");
            }
        }
        StringBuilder d = new StringBuilder();
        d.append("Find ")
                .append(HttpToolLabels.quotedSnippet(oldT, 72))
                .append(" → replace with ")
                .append(HttpToolLabels.quotedSnippet(newT, 72));
        if (replaceAll) {
            d.append(" · all occurrences");
        } else if (maxRep > 1) {
            d.append(" · up to ").append(maxRep).append(" time(s)");
        }
        return new HumanToolUsage("Replace text in request body" + nodeSuf, d.toString());
    }
}
