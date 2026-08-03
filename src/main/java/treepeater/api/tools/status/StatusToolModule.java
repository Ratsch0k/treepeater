package treepeater.api.tools.status;

import treepeater.api.TreepeaterToolRegistry;
import treepeater.api.tools.core.ToolModule;
import treepeater.api.tools.core.ToolRuntime;
import treepeater.api.tools.core.TreepeaterToolAdapter;

public final class StatusToolModule implements ToolModule {

    @Override
    public void register(TreepeaterToolRegistry registry, ToolRuntime runtime) {
        registry.addSpec(new ListStatusesTool(), runtime);
    }
}
