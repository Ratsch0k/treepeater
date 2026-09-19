package treepeater.api.tools.core;

import treepeater.ai.ToolActionLevel;
import treepeater.api.tools.HumanToolUsage;

/** Metadata and behavior for one callable Treepeater operation. */
public interface TreepeaterToolSpec {

    String name();

    String description();

    String inputSchemaJson();

    ToolActionLevel actionLevel();

    /** Runs the tool and returns a JSON result string. */
    String invoke(ToolInvocation inv) throws Exception;

    /** Chat approval card label; override when the default is insufficient. */
    default HumanToolUsage humanLabel(ToolInvocation inv, ToolLabelContext labelCtx) {
        return new HumanToolUsage(name(), "");
    }
}
