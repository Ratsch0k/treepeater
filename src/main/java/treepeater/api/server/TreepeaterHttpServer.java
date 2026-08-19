package treepeater.api.server;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.thread.Invocable;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.io.Content.Sink;
import org.eclipse.jetty.io.Content.Source;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import treepeater.Treepeater;
import treepeater.api.TreepeaterService;
import treepeater.api.TreepeaterToolRegistry;
import treepeater.settings.TreepeaterSettings;

/**
 * Loopback-only HTTP front for the shared tool registry, exposing the REST API under {@code /api} and
 * the MCP endpoint under {@code /mcp}.
 *
 * <p>This is the only class aware of Jetty. It owns the connector, the shared gate (origin check and
 * bearer authentication) and the translation between Jetty's types and {@link ApiRequest} /
 * {@link ApiResponse}, so both handlers stay transport-independent.
 *
 * <p>Jetty rather than {@code com.sun.net.httpserver} because Burp runs on a trimmed jlink image that
 * omits the {@code jdk.httpserver} module, making that package missing at runtime even though it resolves
 * when compiling against a full JDK.
 */
public final class TreepeaterHttpServer {

    private static final String REST_PREFIX = "/api";
    private static final String MCP_PATH = "/mcp";

    /** Upper bound on a request body, so a rogue client cannot exhaust the extension's heap. */
    private static final int MAX_BODY_BYTES = 8 * 1024 * 1024;

    /**
     * Handlers block: every tool call waits on the Swing event dispatch thread, and some send HTTP
     * traffic. The pool therefore needs enough headroom that a slow tool cannot starve the acceptor and
     * selector threads Jetty also draws from it.
     */
    private static final int MAX_THREADS = 16;

    private static final int MIN_THREADS = 8;
    private static final int THREAD_IDLE_MS = 60000;
    private static final int CONNECTION_IDLE_MS = 30000;
    private static final long POOL_STOP_TIMEOUT_MS = 500;

    /**
     * Hosts accepted in an {@code Origin} header. A browser page on any other origin must not be able to
     * drive the local server, which is the DNS-rebinding attack that local MCP servers are required to
     * defend against.
     */
    private static final Set<String> ALLOWED_ORIGIN_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    private final RestHandler restHandler;
    private final McpHandler mcpHandler;

    private Server server;
    private ServerConnector connector;

    public TreepeaterHttpServer(TreepeaterToolRegistry registry, TreepeaterService service) {
        if (registry == null) {
            throw new IllegalArgumentException("registry required");
        }
        if (service == null) {
            throw new IllegalArgumentException("service required");
        }
        this.restHandler = new RestHandler(registry, service);
        this.mcpHandler = new McpHandler(registry);
    }

    /**
     * Starts on the port from settings and returns whether the server is listening. Already-running is a
     * no-op success. A bind failure is logged and reported as {@code false} rather than thrown, so a taken
     * port cannot stop the extension from loading.
     */
    public synchronized boolean start() {
        if (this.server != null) {
            return true;
        }
        int port = TreepeaterSettings.getInstance().getApiPort();
        Server created = null;
        try {
            QueuedThreadPool pool = new QueuedThreadPool(MAX_THREADS, MIN_THREADS, THREAD_IDLE_MS);
            pool.setName("treepeater-api");
            pool.setDaemon(true);
            // The pool otherwise waits five seconds for idle threads on every stop, which would stall
            // unloading and any change of port. The threads are daemons, so leaving them to exit is safe.
            pool.setStopTimeout(POOL_STOP_TIMEOUT_MS);

            created = new Server(pool);
            // Burp owns process shutdown, and the unloading handler already stops the server.
            created.setStopAtShutdown(false);
            // Stop at once rather than gracefully. A graceful stop waits for open connections to drain,
            // and an idle client holding a keep-alive connection would then stall unloading; no local API
            // call is worth waiting for.
            created.setStopTimeout(0);

            HttpConfiguration httpConfig = new HttpConfiguration();
            httpConfig.setSendServerVersion(false);
            httpConfig.setSendXPoweredBy(false);

            ServerConnector newConnector = new ServerConnector(created, new HttpConnectionFactory(httpConfig));
            newConnector.setHost("127.0.0.1");
            newConnector.setPort(port);
            newConnector.setIdleTimeout(CONNECTION_IDLE_MS);
            created.addConnector(newConnector);
            created.setHandler(new GateHandler());
            created.start();

            this.server = created;
            this.connector = newConnector;
            logOutput("Treepeater API listening on http://127.0.0.1:" + this.port() + " (/api, /mcp)");
            return true;
        } catch (Exception e) {
            stopQuietly(created);
            this.server = null;
            this.connector = null;
            logError("Treepeater API could not start on port " + port + ": " + describe(e));
            return false;
        }
    }

    public synchronized void stop() {
        stopQuietly(this.server);
        this.server = null;
        this.connector = null;
    }

    public synchronized boolean isRunning() {
        return this.server != null;
    }

    /** The bound port while running, otherwise the port currently configured in settings. */
    public synchronized int port() {
        if (this.connector != null) {
            int local = this.connector.getLocalPort();
            if (local > 0) {
                return local;
            }
        }
        return TreepeaterSettings.getInstance().getApiPort();
    }

    /** Stops then starts, for use when the port or enabled state changes in settings. */
    public synchronized void restart() {
        this.stop();
        this.start();
    }

    private static void stopQuietly(Server server) {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception ignored) {
                // already stopping or never started
            }
        }
    }

    /**
     * Root handler. Declared blocking because both the body read and every tool call block, so Jetty must
     * dispatch it to a thread that is allowed to wait.
     */
    private final class GateHandler extends Handler.Abstract {

        private GateHandler() {
            super(Invocable.InvocationType.BLOCKING);
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            ApiResponse result;
            try {
                result = TreepeaterHttpServer.this.dispatch(request);
            } catch (Exception e) {
                logError("Treepeater API request failed: " + describe(e));
                result = ApiResponse.error(500, "internal error");
            }
            send(response, callback, result);
            return true;
        }
    }

    /** Runs the shared gate, then routes to the surface that owns the path. */
    private ApiResponse dispatch(Request request) throws Exception {
        // The origin check comes first so a cross-site page is turned away even before preflight.
        if (!originAllowed(request)) {
            return ApiResponse.error(403, "forbidden origin");
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return ApiResponse.status(204);
        }
        if (!authorized(request)) {
            return ApiResponse.error(401, "unauthorized");
        }

        String path = normalizePath(request.getHttpURI().getPath());
        boolean mcp = path.equals(MCP_PATH) || path.startsWith(MCP_PATH + "/");
        boolean rest = path.equals(REST_PREFIX) || path.startsWith(REST_PREFIX + "/");
        if (!mcp && !rest) {
            return ApiResponse.error(404, "not found");
        }

        // Read the body only after the gate, so an unauthenticated caller cannot make us buffer anything.
        String declared = request.getHeaders().get(HttpHeader.CONTENT_LENGTH);
        if (declared != null && isOverLimit(declared)) {
            return ApiResponse.error(413, "request body too large");
        }
        String body = hasBody(request.getMethod()) ? Source.asString(request) : "";
        if (body.getBytes(StandardCharsets.UTF_8).length > MAX_BODY_BYTES) {
            return ApiResponse.error(413, "request body too large");
        }

        ApiRequest apiRequest =
                new ApiRequest(
                        request.getMethod(),
                        path,
                        request.getHttpURI().getQuery(),
                        headersOf(request),
                        body);
        return mcp ? this.mcpHandler.handle(apiRequest) : this.restHandler.handle(apiRequest);
    }

    private static void send(Response response, Callback callback, ApiResponse result) {
        response.setStatus(result.status());
        if (result.json() == null) {
            response.write(true, ByteBuffer.allocate(0), callback);
            return;
        }
        response.getHeaders().put(HttpHeader.CONTENT_TYPE, "application/json; charset=utf-8");
        Sink.write(response, true, result.json(), callback);
    }

    private static Map<String, String> headersOf(Request request) {
        Map<String, String> headers = new HashMap<>();
        for (HttpField field : request.getHeaders()) {
            // First occurrence wins; none of the headers the API reads may legitimately repeat.
            headers.putIfAbsent(field.getName().toLowerCase(Locale.ROOT), field.getValue());
        }
        return headers;
    }

    private static boolean hasBody(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    private static boolean isOverLimit(String contentLength) {
        try {
            return Long.parseLong(contentLength.trim()) > MAX_BODY_BYTES;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Path without a trailing slash, so {@code /api/health/} resolves like {@code /health}. */
    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String trimmed = path;
        while (trimmed.length() > 1 && trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /** Whether the request carries the configured bearer token. */
    private static boolean authorized(Request request) {
        String expected = TreepeaterSettings.getInstance().getOrCreateApiToken();
        String header = request.getHeaders().get(HttpHeader.AUTHORIZATION);
        String presented = null;
        if (header != null) {
            String trimmed = header.trim();
            if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
                presented = trimmed.substring(7).trim();
            }
        }
        return presented != null && expected != null && tokensMatch(expected, presented);
    }

    /** Constant-time comparison, so response timing cannot be used to recover the token byte by byte. */
    private static boolean tokensMatch(String expected, String presented) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Rejects cross-origin browser requests. Non-browser clients send no {@code Origin} at all and are
     * accepted; a header naming anything other than loopback means a web page is trying to reach the
     * server, which is how DNS rebinding attacks against local servers work.
     */
    private static boolean originAllowed(Request request) {
        String origin = request.getHeaders().get(HttpHeader.ORIGIN);
        if (origin == null || origin.isBlank()) {
            return true;
        }
        return ALLOWED_ORIGIN_HOSTS.contains(originHost(origin));
    }

    private static String originHost(String origin) {
        try {
            String host = URI.create(origin.trim()).getHost();
            return host != null ? host.toLowerCase(Locale.ROOT) : "";
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        return message != null && !message.isBlank()
                ? error.getClass().getSimpleName() + ": " + message
                : error.getClass().getSimpleName();
    }

    private static void logOutput(String message) {
        if (Treepeater.api != null) {
            Treepeater.api.logging().logToOutput(message);
        }
    }

    private static void logError(String message) {
        if (Treepeater.api != null) {
            Treepeater.api.logging().logToError(message);
        }
    }
}
