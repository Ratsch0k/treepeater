package treepeater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.repeater.Repeater;
import treepeater.persistence.TreepeaterPersistence;

class RepeaterNoticeTest extends ImportTestSupport {

    private Repeater repeater;
    private Logging logging;
    private TreepeaterPersistence persistence;
    private String current;

    @BeforeEach
    void setUpNotice() throws Exception {
        MontoyaFactoryStub.install();
        this.current = TreepeaterVersion.get();

        this.repeater = mock(Repeater.class);
        lenient().when(Treepeater.api.repeater()).thenReturn(this.repeater);
        this.logging = Treepeater.api.logging();

        this.persistence = mock(TreepeaterPersistence.class);
        Field field = Treepeater.class.getDeclaredField("persistence");
        field.setAccessible(true);
        field.set(null, this.persistence);
    }

    @AfterEach
    void tearDownNotice() {
        MontoyaFactoryStub.uninstall();
    }

    @Test
    void requestContainsHostVersionAndProjectLink() {
        HttpRequest request = RepeaterNotice.buildRequest("1.6.0");

        String raw = new String(request.toByteArray().getBytes(), StandardCharsets.UTF_8);
        assertEquals(RepeaterNotice.HOST, request.httpService().host());
        assertTrue(raw.contains("Host: treepeater.invalid"));
        assertTrue(raw.contains("X-Treepeater-Version: 1.6.0"));
        assertTrue(raw.contains("version 1.6.0"));
        assertTrue(raw.contains("https://github.com/Ratsch0k/treepeater"));
    }

    @Test
    void createNoticeAlwaysSendsAndSavesVersion() {
        when(this.persistence.getUsedTreepeaterVersion()).thenReturn("1.6.0");

        RepeaterNotice.createNoticeInRepeaterTab("1.6.0");

        verify(this.repeater).sendToRepeater(any(HttpRequest.class), eq("Treepeater 1.6.0 used"));
        verify(this.persistence).setUsedTreepeaterVersion("1.6.0");
    }

    @Test
    void ensureNoticeDoesNothingForEmptyTree() {
        RepeaterNotice.ensureNotice(new TreepeaterModel());

        verify(this.repeater, never()).sendToRepeater(any(HttpRequest.class), anyString());
        verify(this.persistence, never()).setUsedTreepeaterVersion(anyString());
    }

    @Test
    void ensureNoticeSendsOnceWhenTreeHasRequests() {
        TreepeaterModel model = new TreepeaterModel();
        model.insertNode(rr("GET", "/api/users"));

        RepeaterNotice.ensureNotice(model);
        when(this.persistence.getUsedTreepeaterVersion()).thenReturn(this.current);
        RepeaterNotice.ensureNotice(model);

        verify(this.repeater, times(1)).sendToRepeater(any(HttpRequest.class), anyString());
        verify(this.persistence).setUsedTreepeaterVersion(this.current);
    }

    @Test
    void ensureNoticeSendsNewNoticeAfterUpgrade() {
        TreepeaterModel model = new TreepeaterModel();
        model.insertNode(rr("GET", "/api/users"));
        when(this.persistence.getUsedTreepeaterVersion()).thenReturn("0.0.1");

        RepeaterNotice.ensureNotice(model);

        verify(this.repeater).sendToRepeater(
                any(HttpRequest.class), eq("Treepeater " + this.current + " used"));
        verify(this.persistence).setUsedTreepeaterVersion(this.current);
    }

    @Test
    void ensureNoticeSwallowsAndLogsErrors() {
        doThrow(new IllegalStateException("boom"))
                .when(this.repeater).sendToRepeater(any(HttpRequest.class), anyString());
        TreepeaterModel model = new TreepeaterModel();
        model.insertNode(rr("GET", "/api/users"));

        RepeaterNotice.ensureNotice(model);

        verify(this.logging).logToOutput(org.mockito.ArgumentMatchers.contains("boom"));
        verify(this.persistence, never()).setUsedTreepeaterVersion(anyString());
    }

    @Test
    void versionFallsBackToUnknownWhenResourceIsMissing() {
        assertEquals(TreepeaterVersion.UNKNOWN, TreepeaterVersion.read("/does-not-exist.properties"));
    }
}
