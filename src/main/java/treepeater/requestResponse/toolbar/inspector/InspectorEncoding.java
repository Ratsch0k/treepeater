package treepeater.requestResponse.toolbar.inspector;

import java.nio.charset.StandardCharsets;

import burp.api.montoya.core.ByteArray;

import treepeater.Treepeater;

/** How selected text was interpreted for decode/encode round-trips in the Inspector. */
public enum InspectorEncoding {
    PLAIN("Plain text"),
    URL("URL-encoded"),
    HTML("HTML entities"),
    BASE64("Base64");

    private final String label;

    InspectorEncoding(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return this.label;
    }

    public static InspectorEncoding detect(String raw) {
        if (raw == null || raw.isEmpty()) {
            return PLAIN;
        }
        // Percent-escaped URL encoding is unambiguous, so check it first.
        if (containsPercentEscape(raw)) {
            return URL;
        }
        if (looksHtmlEncoded(raw)) {
            return HTML;
        }
        // Base64 before the plus-only URL case: Base64 also uses '+', so a valid Base64 string must not be mistaken
        // for URL-encoded form data just because it contains a '+'.
        if (looksBase64(raw)) {
            return BASE64;
        }
        if (looksUrlEncoded(raw)) {
            return URL;
        }
        return PLAIN;
    }

    public static String decode(String raw, InspectorEncoding encoding) {
        if (raw == null) {
            return "";
        }
        try {
            switch (encoding) {
                case URL:
                    return Treepeater.api.utilities().urlUtils().decode(raw);
                case HTML:
                    return Treepeater.api.utilities().htmlUtils().decode(raw);
                case BASE64:
                    return Treepeater.api.utilities().base64Utils().decode(raw.trim()).toString();
                case PLAIN:
                default:
                    return raw;
            }
        } catch (Exception ex) {
            return raw;
        }
    }

    public static String encode(String value, InspectorEncoding encoding) {
        if (value == null) {
            return "";
        }
        try {
            switch (encoding) {
                case URL:
                    // Burp's urlUtils().encode() follows java.net.URLEncoder, which encodes spaces as "+". Replace those
                    // with "%20". This is safe: a literal '+' in the input is encoded as "%2B", so every remaining "+"
                    // in the output represents a space.
                    return Treepeater.api.utilities().urlUtils().encode(value).replace("+", "%20");
                case HTML:
                    return Treepeater.api.utilities().htmlUtils().encode(value);
                case BASE64:
                    return Treepeater.api.utilities().base64Utils()
                            .encodeToString(ByteArray.byteArray(value.getBytes(StandardCharsets.UTF_8)));
                case PLAIN:
                default:
                    return value;
            }
        } catch (Exception ex) {
            return value;
        }
    }

    /** True if the string contains at least one valid %XX escape (and no malformed one). */
    private static boolean containsPercentEscape(String s) {
        boolean any = false;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '%') {
                if (i + 2 >= s.length() || !isHex(s.charAt(i + 1)) || !isHex(s.charAt(i + 2))) {
                    return false;
                }
                any = true;
            }
        }
        return any;
    }

    private static boolean looksUrlEncoded(String s) {
        // Fallback detection for URL-encoded form data that uses '+' for spaces but has no %XX escapes (those are
        // already handled earlier). A bare '+' only counts when there is no raw space and no whitespace/newline in the
        // string, since encoded data has its spaces encoded; this avoids treating plain text like "a + b" as encoded.
        boolean hasPlus = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '+') {
                hasPlus = true;
            } else if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                return false;
            }
        }
        return hasPlus;
    }

    private static boolean looksHtmlEncoded(String s) {
        return s.contains("&#") || s.contains("&amp;") || s.contains("&lt;") || s.contains("&gt;")
                || s.contains("&quot;") || s.contains("&#x") || s.contains("&apos;");
    }

    private static boolean looksBase64(String s) {
        String t = s.trim();
        if (t.length() < 8 || t.length() % 4 != 0) {
            return false;
        }
        int pad = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '=') {
                pad++;
                continue;
            }
            if (pad > 0) {
                return false; // padding only allowed at the very end
            }
            boolean ok = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '+' || c == '/';
            if (!ok) {
                return false;
            }
        }
        if (pad > 2) {
            return false;
        }
        // Guard against ordinary lowercase words that happen to be multiples of 4 characters.
        String decoded = Treepeater.api.utilities().base64Utils().decode(t).toString();
        if (decoded == null || decoded.isEmpty()) {
            return false;
        }
        return isMostlyPrintable(decoded);
    }

    private static boolean isMostlyPrintable(String s) {
        int printable = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\t' || c == '\n' || c == '\r' || (c >= 0x20 && c < 0x7f) || c >= 0xa0) {
                printable++;
            }
        }
        return printable >= (int) Math.ceil(s.length() * 0.9);
    }

    private static boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
