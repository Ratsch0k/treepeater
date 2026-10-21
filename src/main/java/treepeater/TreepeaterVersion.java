package treepeater;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Provides the version of the running Treepeater build.
 *
 * <p>The version is defined once in {@code build.gradle.kts}. At build time Gradle writes it into
 * {@code treepeater.properties}, which is bundled in the jar and read from the classpath here.
 */
public final class TreepeaterVersion {
    /** Returned when the version cannot be determined. */
    public static final String UNKNOWN = "unknown";

    private static final String RESOURCE = "/treepeater.properties";
    private static volatile String cached;

    private TreepeaterVersion() {}

    /**
     * Returns the current Treepeater version. The value is read once and cached.
     *
     * @return the version, or {@link #UNKNOWN} if it cannot be read
     */
    public static String get() {
        String version = cached;
        if (version == null) {
            version = read(RESOURCE);
            cached = version;
        }
        return version;
    }

    /**
     * Reads the {@code version} property from a classpath properties file.
     *
     * @param resource classpath location of the properties file
     * @return the trimmed version, or {@link #UNKNOWN} if the file is missing or unreadable, or the
     *     property is absent, blank, or still an unreplaced {@code ${...}} placeholder
     */
    static String read(String resource) {
        try (InputStream in = TreepeaterVersion.class.getResourceAsStream(resource)) {
            if (in == null) {
                return UNKNOWN;
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version");
            if (version == null || version.isBlank() || version.contains("${")) {
                return UNKNOWN;
            }
            return version.trim();
        } catch (IOException e) {
            return UNKNOWN;
        }
    }
}
