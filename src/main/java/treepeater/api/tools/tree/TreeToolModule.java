package treepeater.api.tools.tree;

import treepeater.api.TreepeaterToolRegistry;
import treepeater.api.tools.core.ToolModule;
import treepeater.api.tools.core.ToolRuntime;

public final class TreeToolModule implements ToolModule {

    @Override
    public void register(TreepeaterToolRegistry registry, ToolRuntime runtime) {
        registry.addSpec(new ListTreeTool(), runtime);
        registry.addSpec(new GetTreeNodeTool(), runtime);
        registry.addSpec(new GetNodeNotesTool(), runtime);
        registry.addSpec(new CreateFolderTool(), runtime);
        registry.addSpec(new RenameNodeTool(), runtime);
        registry.addSpec(new MoveNodeTool(), runtime);
        registry.addSpec(new SetNodeStatusTool(), runtime);
        registry.addSpec(new SetNodeNotesTool(), runtime);
        registry.addSpec(new DeleteNodeTool(), runtime);
    }
}
