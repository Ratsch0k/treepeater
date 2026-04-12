package treepeater.ai;

import java.io.IOException;

/**
 * User-facing error strings independent of any specific LLM provider.
 */
public final class ChatErrors {
    private ChatErrors() {
    }

    public static String formatUserMessage(Throwable ex) {
        if (ex instanceof IOException) {
            return ex.getMessage() != null ? ex.getMessage() : "I/O error.";
        }
        Throwable c = ex.getCause();
        if (c != null && c.getMessage() != null) {
            return c.getMessage();
        }
        return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}
