package treepeater.api.server;

import treepeater.api.Json;

/**
 * The result of an API call: a status and an optional JSON body. Like {@link ApiRequest}, it carries no
 * HTTP library type, so handlers can be exercised without a socket.
 *
 * @param json the response body, or {@code null} for a status-only reply
 */
public record ApiResponse(int status, String json) {

    public static ApiResponse ok(String json) {
        return new ApiResponse(200, json);
    }

    /** 200 when the tool or service succeeded, 400 when it returned an error envelope. */
    public static ApiResponse fromResult(String result) {
        return new ApiResponse(Json.isError(result) ? 400 : 200, result);
    }

    public static ApiResponse error(int status, String message) {
        return new ApiResponse(status, Json.error(message));
    }

    public static ApiResponse status(int status) {
        return new ApiResponse(status, null);
    }
}
