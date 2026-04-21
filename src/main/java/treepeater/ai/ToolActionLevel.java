package treepeater.ai;

/**
 * Sensitivity of built-in HTTP target tools: inspection only, mutating the live request, or sending traffic.
 */
public enum ToolActionLevel {
    READ_ONLY,
    WRITE,
    EXECUTE
}
