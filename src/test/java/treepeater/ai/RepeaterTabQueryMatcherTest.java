package treepeater.ai;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class RepeaterTabQueryMatcherTest {

    @Test
    void matches_titleSubstring() {
        assertTrue(RepeaterTabQueryMatcher.matches("Login", "GET", "https://x.com/a", "My Login flow"));
        assertFalse(RepeaterTabQueryMatcher.matches("Logout", "GET", "https://x.com/a", "My Login flow"));
    }

    @Test
    void matches_urlSubstring_whenNoMethodToken() {
        assertTrue(
                RepeaterTabQueryMatcher.matches(
                        "https://domain.de/asdf", "GET", "https://domain.de/asdf/x", "1"));
        assertFalse(RepeaterTabQueryMatcher.matches("https://other", "GET", "https://domain.de/asdf/x", "1"));
    }

    @Test
    void matches_methodAndPath() {
        assertTrue(RepeaterTabQueryMatcher.matches("POST /test", "POST", "https://example.com/api/test", "n"));
        assertFalse(RepeaterTabQueryMatcher.matches("POST /test", "GET", "https://example.com/api/test", "n"));
        assertFalse(RepeaterTabQueryMatcher.matches("POST /test", "POST", "https://example.com/api/other", "n"));
    }

    @Test
    void matches_methodOnlyToken() {
        assertTrue(RepeaterTabQueryMatcher.matches("POST", "POST", "https://x/y", "t"));
        assertFalse(RepeaterTabQueryMatcher.matches("POST", "GET", "https://x/y", "t"));
    }

    @Test
    void emptyQuery_matchesAll() {
        assertTrue(RepeaterTabQueryMatcher.matches("", "GET", "https://x", "t"));
    }
}
