package treepeater.api.tools.http;

import com.fasterxml.jackson.databind.JsonNode;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.AbstractEditorTool;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;
import treepeater.api.tools.http.support.HttpEditorService;

public final class ReadHttpMessageTool extends AbstractEditorTool {

    public static final String NAME = "read_http_message";

    public static final int DEFAULT_CHUNK_BYTES = 4_096;
    public static final int MAX_CHUNK_BYTES = 65_536;

    private static final String SCHEMA =
            "{\"type\":\"object\",\"properties\":{"
                    + HttpToolSchemas.REQUEST_NODE_ID_PROPERTY
                    + ",\"side\":{\"type\":\"string\",\"enum\":[\"request\",\"response\"]},\"history_index\":{\"type\":\"integer\",\"minimum\":0,\"description\":\"0-based; omit=current\"},\"offset\":{\"type\":\"integer\",\"minimum\":0,\"default\":0},\"max_bytes\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":65536,\"default\":4096}},\"required\":[\"side\"],\"additionalProperties\":false}";

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
        return SCHEMA;
    }

    @Override
    public ToolActionLevel actionLevel() {
        return ToolActionLevel.READ_ONLY;
    }

    @Override
    public String invoke(ToolInvocation inv) {
        return capResult(HttpEditorService.readMessage(requireContext(inv), inv.args()));
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        JsonNode args = inv.args();
        String sideLabel = HttpToolLabels.sideLabel(args);
        String hist = HttpToolLabels.formatHistoryIndexArg(args, labelCtx.viewerHistoryIndex());
        String nodeSuf = HttpToolLabels.formatRequestNodeIdSuffix(args, labelCtx.uiSelectedRequestNodeId());
        String head = sideLabel.isEmpty() ? "Reading HTTP message" : "Reading " + sideLabel.toLowerCase();
        StringBuilder b = new StringBuilder(head);
        if (!hist.isEmpty()) {
            b.append(hist);
        }
        int offset = HttpToolLabels.offsetArg(args);
        int maxBytes =
                HttpToolLabels.clampedIntArg(
                        args, DEFAULT_CHUNK_BYTES, MAX_CHUNK_BYTES, "max_bytes", "maxBytes", "limit", "chunk_size", "chunkSize");
        b.append(" · offset ").append(offset).append(", max ").append(maxBytes).append(" B");
        b.append(nodeSuf);
        return new HumanToolUsage(b.toString(), "");
    }
}
