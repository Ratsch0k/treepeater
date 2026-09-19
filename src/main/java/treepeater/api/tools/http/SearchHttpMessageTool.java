package treepeater.api.tools.http;

import com.fasterxml.jackson.databind.JsonNode;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.AbstractEditorTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;
import treepeater.api.tools.http.support.HttpEditorService;

public final class SearchHttpMessageTool extends AbstractEditorTool {

    public static final String NAME = "search_http_message";

    public static final int DEFAULT_MAX_MATCHES = 10;
    public static final int MAX_MAX_MATCHES = 100;
    public static final int DEFAULT_CONTEXT_BYTES = 64;
    public static final int MAX_CONTEXT_BYTES = 512;
    public static final int MAX_PATTERN_CHARS = 1_024;
    public static final int MAX_SCAN_BYTES = 1_048_576;

    private static final String SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
                    + HttpToolSchemas.REQUEST_NODE_ID_PROPERTY
                    + ",\"side\":{\"type\":\"string\",\"enum\":[\"request\",\"response\"]},\"pattern\":{\"type\":\"string\",\"description\":\"Java Pattern; (?i)(?m)(?s)\"},\"history_index\":{\"type\":\"integer\",\"minimum\":0,\"description\":\"0-based; omit=current\"},\"scope\":{\"type\":\"string\",\"enum\":[\"headers\",\"body\",\"all\"],\"default\":\"all\"},\"max_matches\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":100,\"default\":10},\"context_bytes\":{\"type\":\"integer\",\"minimum\":0,\"maximum\":512,\"default\":64}},\"required\":[\"side\",\"pattern\"],\"additionalProperties\":false}";

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
        return SCHEMA;
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.READ_ONLY;
    }

    @Override
    public String invoke(ToolInvocation inv) {
        return capResult(HttpEditorService.searchMessage(requireContext(inv), inv.args()));
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        JsonNode args = inv.args();
        String sideLabel = HttpToolLabels.sideLabel(args);
        String hist = HttpToolLabels.formatHistoryIndexArg(args, labelCtx.viewerHistoryIndex());
        String nodeSuf = HttpToolLabels.formatRequestNodeIdSuffix(args, labelCtx.uiSelectedRequestNodeId());
        String head = sideLabel.isEmpty() ? "Searching HTTP message" : "Searching " + sideLabel.toLowerCase();
        StringBuilder b = new StringBuilder(head);
        String sc = HttpToolLabels.searchScope(args);
        if (!"all".equals(sc)) {
            b.append(" (scope=").append(sc).append(")");
        }
        if (!hist.isEmpty()) {
            b.append(hist);
        }
        String pat = HttpToolLabels.textArg(args, "pattern", "regex", "re");
        String det = pat.isEmpty() ? "" : HttpToolLabels.quotedSnippet(pat, 96);
        b.append(nodeSuf);
        return new HumanToolUsage(b.toString(), det);
    }
}
