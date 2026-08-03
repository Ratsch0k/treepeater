package treepeater.api.tools.core;

import treepeater.api.TreepeaterService;

/** Base for tools that operate on the tree/import/status layer via {@link TreepeaterService}. */
public abstract class AbstractServiceTool implements TreepeaterToolSpec {

    protected final TreepeaterService requireService(ToolInvocation inv) {
        TreepeaterService service = inv.runtime().service();
        if (service == null) {
            throw new IllegalStateException("service unavailable");
        }
        return service;
    }
}
