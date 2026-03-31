package treepeater.requestResponse;
public final class TargetSettings {
    private final String host;
    private final int port;
    private final boolean https;
    private final boolean sniEnabled;

    public TargetSettings(String host, int port, boolean https, boolean sniEnabled) {
        String normalizedHost = host == null ? "" : host.trim();
        this.host = normalizedHost;
        this.port = port;
        this.https = https;
        this.sniEnabled = https && sniEnabled;
    }

    public String host() {
        return this.host;
    }

    public int port() {
        return this.port;
    }

    public boolean https() {
        return this.https;
    }

    public boolean sniEnabled() {
        return this.sniEnabled;
    }
}
