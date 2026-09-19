package treepeater.api.tools.core;

import treepeater.api.TreepeaterToolRegistry;

/** Registers a group of related tools into the registry. */
public interface ToolModule {

    void register(TreepeaterToolRegistry registry, ToolRuntime runtime);
}
