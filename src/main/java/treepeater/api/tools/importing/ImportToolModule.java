package treepeater.api.tools.importing;

import treepeater.api.TreepeaterToolRegistry;
import treepeater.api.tools.core.ToolModule;
import treepeater.api.tools.core.ToolRuntime;

public final class ImportToolModule implements ToolModule {

    @Override
    public void register(TreepeaterToolRegistry registry, ToolRuntime runtime) {
        registry.addSpec(new ImportHttpRequestTool(), runtime);
        registry.addSpec(new ImportHttpRequestPathAwareTool(), runtime);
        registry.addSpec(new ImportHttpRequestIntoFolderTool(), runtime);
    }
}
