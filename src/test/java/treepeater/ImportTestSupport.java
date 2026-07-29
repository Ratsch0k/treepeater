package treepeater;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import javax.swing.UIManager;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.persistence.Preferences;
import burp.api.montoya.ui.Theme;
import burp.api.montoya.ui.UserInterface;

import treepeater.settings.StatusRegistry;
import treepeater.settings.TreepeaterSettings;
import treepeater.tree.FolderTreeNode;
import treepeater.tree.RequestTreeNode;
import treepeater.tree.TreepeaterNode;

/** Shared setup and tree-navigation helpers for import-related unit tests. */
public abstract class ImportTestSupport {

    protected TreepeaterSettings settings;

    @BeforeAll
    static void installLookAndFeel() throws Exception {
        // The tree cell editor embeds a FlatLaf combo box; without an installed LAF its UI defaults
        // (e.g. cell padding) are null and computing the node's preferred size on insert NPEs.
        // FlatLaf is only on the runtime classpath here, so install it by class name.
        UIManager.setLookAndFeel("com.formdev.flatlaf.FlatLightLaf");
    }

    @BeforeEach
    protected void resetSettings() throws Exception {
        // Reset the settings singleton so we control it with a map-backed Preferences mock,
        // regardless of any other test class having initialized it first.
        Field instance = TreepeaterSettings.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);

        Map<String, String> store = new HashMap<>();
        Map<String, Integer> intStore = new HashMap<>();
        Preferences prefs = mock(Preferences.class);
        lenient().when(prefs.getString(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(i -> store.get(i.<String>getArgument(0)));
        lenient().doAnswer(i -> {
            store.put(i.getArgument(0), i.getArgument(1));
            return null;
        }).when(prefs).setString(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        lenient().doAnswer(i -> {
            store.remove(i.<String>getArgument(0));
            return null;
        }).when(prefs).deleteString(org.mockito.ArgumentMatchers.anyString());
        lenient().when(prefs.getInteger(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(i -> intStore.get(i.<String>getArgument(0)));
        lenient().doAnswer(i -> {
            intStore.put(i.getArgument(0), i.getArgument(1));
            return null;
        }).when(prefs).setInteger(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());

        TreepeaterSettings.init(prefs);
        this.settings = TreepeaterSettings.getInstance();

        // The JTree layout cache invokes the cell renderer on node insertion, which reads
        // Treepeater.getStatusRegistry() and resolves status colors via Treepeater.api's theme.
        // Provide both so headless inserts don't NPE.
        Field registry = Treepeater.class.getDeclaredField("statusRegistry");
        registry.setAccessible(true);
        registry.set(null, new StatusRegistry());

        Field api = Treepeater.class.getDeclaredField("api");
        api.setAccessible(true);
        MontoyaApi montoyaApi = mock(MontoyaApi.class);
        UserInterface userInterface = mock(UserInterface.class);
        Logging logging = mock(Logging.class);
        lenient().when(montoyaApi.userInterface()).thenReturn(userInterface);
        lenient().when(montoyaApi.logging()).thenReturn(logging);
        lenient().when(userInterface.currentTheme()).thenReturn(Theme.LIGHT);
        api.set(null, montoyaApi);
    }

    protected static HttpRequestResponse rr(String method, String path) {
        return rr(method, path, null);
    }

    protected static HttpRequestResponse rr(String method, String path, String url) {
        HttpRequest req = mock(HttpRequest.class);
        lenient().when(req.pathWithoutQuery()).thenReturn(path);
        lenient().when(req.path()).thenReturn(path);
        lenient().when(req.method()).thenReturn(method);
        if (url != null) {
            lenient().when(req.url()).thenReturn(url);
        }
        HttpResponse resp = mock(HttpResponse.class);
        HttpRequestResponse rr = mock(HttpRequestResponse.class);
        lenient().when(rr.request()).thenReturn(req);
        lenient().when(rr.response()).thenReturn(resp);
        return rr;
    }

    protected static FolderTreeNode root(TreepeaterModel model) {
        return (FolderTreeNode) model.getTree().getTreeModel().getRoot();
    }

    protected static FolderTreeNode folder(TreepeaterNode parent, String name) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            TreepeaterNode child = (TreepeaterNode) parent.getChildAt(i);
            if (child instanceof FolderTreeNode f && f.getName().equals(name)) {
                return f;
            }
        }
        return null;
    }

    protected static RequestTreeNode leaf(TreepeaterNode parent, String name) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            TreepeaterNode child = (TreepeaterNode) parent.getChildAt(i);
            if (child instanceof RequestTreeNode r && r.getName().equals(name)) {
                return r;
            }
        }
        return null;
    }
}
