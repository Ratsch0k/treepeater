package treepeater.ai;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RepeaterTabQueryMatcherTest {

    @Test
    void matches_titleSubstring() {
        assertTrue(TreepeaterTabQueryMatcher.matches("Login", "GET", "https://x.com/a", "My Login flow"));
        assertFalse(TreepeaterTabQueryMatcher.matches("Logout", "GET", "https://x.com/a", "My Login flow"));
    }

    @Test
    void matches_urlSubstring_whenNoMethodToken() {
        assertTrue(
                TreepeaterTabQueryMatcher.matches(
                        "https://domain.de/asdf", "GET", "https://domain.de/asdf/x", "1"));
        assertFalse(TreepeaterTabQueryMatcher.matches("https://other", "GET", "https://domain.de/asdf/x", "1"));
    }

    @Test
    void matches_methodAndPath() {
        assertTrue(TreepeaterTabQueryMatcher.matches("POST /test", "POST", "https://example.com/api/test", "n"));
        assertFalse(TreepeaterTabQueryMatcher.matches("POST /test", "GET", "https://example.com/api/test", "n"));
        assertFalse(TreepeaterTabQueryMatcher.matches("POST /test", "POST", "https://example.com/api/other", "n"));
    }

    @Test
    void matches_methodOnlyToken() {
        assertTrue(TreepeaterTabQueryMatcher.matches("POST", "POST", "https://x/y", "t"));
        assertFalse(TreepeaterTabQueryMatcher.matches("POST", "GET", "https://x/y", "t"));
    }

    @Test
    void emptyQuery_matchesAll() {
        assertTrue(TreepeaterTabQueryMatcher.matches("", "GET", "https://x", "t"));
    }
}
