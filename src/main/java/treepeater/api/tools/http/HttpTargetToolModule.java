package treepeater.api.tools.http;

import treepeater.api.TreepeaterToolRegistry;
import treepeater.api.tools.core.ToolModule;
import treepeater.api.tools.core.ToolRuntime;

public final class HttpTargetToolModule implements ToolModule {

    @Override
    public void register(TreepeaterToolRegistry registry, ToolRuntime runtime) {
        registry.addSpec(new GetCurrentHttpTargetTool(), runtime);
        registry.addSpec(new SearchTabsTool(), runtime);
        registry.addSpec(new CopyTreepeaterNodeTool(), runtime);
        registry.addSpec(new BatchHttpTargetToolsTool(), runtime);
        registry.addSpec(new ReadHttpMessageTool(), runtime);
        registry.addSpec(new SearchHttpMessageTool(), runtime);
        registry.addSpec(new ReplaceInHttpRequestBodyTool(), runtime);
        registry.addSpec(new PatchHttpRequestBodyLinesTool(), runtime);
        registry.addSpec(new SetHttpRequestBodyTool(), runtime);
        registry.addSpec(new ApplyHttpRequestSemanticChangesTool(), runtime);
        registry.addSpec(new SendCurrentHttpRequestTool(), runtime);
    }
}
