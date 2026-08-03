package treepeater.api.tools.http;

import treepeater.api.tools.HumanToolUsage;
import treepeater.api.tools.core.ToolInvocation;
import treepeater.api.tools.core.ToolLabelContext;
import treepeater.api.tools.core.TreepeaterToolSpec;
import treepeater.api.tools.http.support.HttpTargetSupport;

/** Base for HTTP/editor tools backed by {@link HttpTargetSupport}. */
abstract class AbstractHttpTargetTool implements TreepeaterToolSpec {

    @Override
    public String invoke(ToolInvocation inv) {
        return HttpTargetSupport.invokeTool(inv, name());
    }

    @Override
    public HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        HumanToolUsage usage =
                HttpTargetSupport.humanToolUsage(
                        name(),
                        inv.argumentsJson(),
                        labelCtx.viewerHistoryIndex(),
                        labelCtx.uiSelectedRequestNodeId());
        return usage != null ? usage : new HumanToolUsage(name(), "");
    }
}
