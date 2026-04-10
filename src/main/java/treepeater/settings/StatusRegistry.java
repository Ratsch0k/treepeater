package treepeater.settings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.swing.UIManager;

import treepeater.Treepeater;
import treepeater.requestResponse.Status;

/**
 * Manages the list of statuses available for Treepeater nodes.
 * 
 * Statuses are identified by a unique ID, and have a name, background and border/icon color, and an SVG icon.
 */
public class StatusRegistry {

    private final List<Status> statuses = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    private static final Status DEFAULT_STATUS = StatusRegistry.buildDefaultStatus();

    /**
     * Create a new status registry with the default status and the standard statuses.
     */
    public StatusRegistry() {
        this.statuses.add(StatusRegistry.DEFAULT_STATUS);
        this.statuses.addAll(StatusRegistry.buildStandardStatuses());
    }

    /**
     * Create a new status registry with the default status and the given statuses.
     * @param statuses The statuses to add to the registry.
     */
    public StatusRegistry(List<Status> statuses) {
        this.statuses.add(StatusRegistry.DEFAULT_STATUS);
        this.statuses.addAll(statuses);
    }

    public List<Status> getAll() {
        return Collections.unmodifiableList(statuses);
    }

    /**
     * Looks up a status by ID (case-insensitive), then falls back to a
     * case-insensitive match on the display name for backward compatibility
     * with persisted data that stored names like "Finding" / "Done".
     */
    public Status getById(String id) {
        if (id == null) return null;
        for (Status s : statuses) {
            if (s.getId().equalsIgnoreCase(id)) return s;
        }
        for (Status s : statuses) {
            if (s.getStatus().equalsIgnoreCase(id)) return s;
        }
        return null;
    }

    /** Return the default status. */
    public static Status getDefault() {
        return DEFAULT_STATUS;
    }


    public void add(Status status) {
        statuses.add(status);
        notifyListeners();
    }

    public void update(int index, Status status) {
        if (index == 0) return;

        statuses.set(index, status);
        notifyListeners();
    }

    public void remove(int index) {
        // Don't allow the default status to be removed
        if (index == 0) return;

        statuses.remove(index);
        notifyListeners();
    }

    public void moveUp(int index) {
        // Don't allow the default status to be moved
        if (index <= 1 || index >= statuses.size()) return;
        Collections.swap(statuses, index, index - 1);
        notifyListeners();
    }

    public void moveDown(int index) {
        // Don't allow the default status to be moved
        if (index <= 0 || index >= statuses.size() - 1) return;
        Collections.swap(statuses, index, index + 1);
        notifyListeners();
    }

    public void addChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        Treepeater.saveState();
        listeners.forEach(Runnable::run);
    }

    /** Generates a unique ID that does not collide with any existing status IDs. */
    public String generateId() {
        String id;
        do {
            id = UUID.randomUUID().toString();
        } while (getById(id) != null);
        return id;
    }

    private static List<Status> buildStandardStatuses() {
        List<Status> defaults = new ArrayList<>();
        defaults.add(new Status("TODO",       "TODO",       new Status.StatusColors(UIManager.getColor("Colors.ui.groups.2.background"), UIManager.getColor("Colors.ui.groups.2.accent"), UIManager.getColor("Colors.ui.groups.2.background"), UIManager.getColor("Colors.ui.groups.2.accent")), readSvgResource("/icons/hourglass.svg")));
        defaults.add(new Status("FINDING",    "Finding",    new Status.StatusColors(UIManager.getColor("Colors.ui.groups.1.background"), UIManager.getColor("Colors.ui.groups.1.accent"), UIManager.getColor("Colors.ui.groups.1.background"), UIManager.getColor("Colors.ui.groups.1.accent")), readSvgResource("/icons/warning.svg")));
        defaults.add(new Status("DONE",       "Done",       new Status.StatusColors(UIManager.getColor("Colors.ui.groups.3.background"), UIManager.getColor("Colors.ui.groups.3.accent"), UIManager.getColor("Colors.ui.groups.3.background"), UIManager.getColor("Colors.ui.groups.3.accent")), readSvgResource("/icons/check.svg")));
        return defaults;
    }

    private static Status buildDefaultStatus() {
        return new Status(
            "DEFAULT",
             "Default",
             new Status.StatusColors(UIManager.getColor("Colors.ui.background.3"), UIManager.getColor("Colors.ui.background.4"), UIManager.getColor("Colors.ui.background.3"), UIManager.getColor("Colors.ui.background.4")),
             StatusRegistry.readSvgResource("/icons/folder.svg"));
    }

    static String readSvgResource(String resourcePath) {
        String path = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
        try (InputStream is = StatusRegistry.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 16 16\"/>";
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 16 16\"/>";
        }
    }
}
