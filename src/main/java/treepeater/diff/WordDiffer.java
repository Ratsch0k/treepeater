package treepeater.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

/**
 * Word-level side-by-side diff using java-diff-utils. Each side keeps the original line structure; only changed
 * words (and changed whitespace runs) are highlighted. Tokens are {@code \S+} words and {@code \s+} whitespace runs.
 */
public final class WordDiffer {

    private static final Pattern WORD_TOKEN = Pattern.compile("\\S+|\\s+");

    private WordDiffer() {}

    public record SideBySideDiff(String leftHtml, String rightHtml, int removedChars, int addedChars) {}

    public static SideBySideDiff diff(String left, String right) {
        String a = normalizeNewlines(left != null ? left : "");
        String b = normalizeNewlines(right != null ? right : "");

        List<String> tokensA = tokenize(a);
        List<String> tokensB = tokenize(b);
        Patch<String> patch = DiffUtils.diff(tokensA, tokensB, true);

        StringBuilder leftHtml = new StringBuilder();
        StringBuilder rightHtml = new StringBuilder();
        int removedChars = 0;
        int addedChars = 0;
        leftHtml.append("<html><body><pre style=\"").append(DiffTheme.preWrapperStyle()).append("\">");
        rightHtml.append("<html><body><pre style=\"").append(DiffTheme.preWrapperStyle()).append("\">");

        for (AbstractDelta<String> delta : patch.getDeltas()) {
            switch (delta.getType()) {
                case EQUAL -> {
                    String text = joinTokens(delta.getSource().getLines());
                    appendSpan(leftHtml, text, DiffTheme.normalSpanStyle());
                    appendSpan(rightHtml, text, DiffTheme.normalSpanStyle());
                }
                case DELETE -> {
                    String text = joinTokens(delta.getSource().getLines());
                    removedChars += text.length();
                    appendSpan(leftHtml, text, DiffTheme.removedSpanStyle());
                }
                case INSERT -> {
                    String text = joinTokens(delta.getTarget().getLines());
                    addedChars += text.length();
                    appendSpan(rightHtml, text, DiffTheme.addedSpanStyle());
                }
                case CHANGE -> {
                    String leftText = joinTokens(delta.getSource().getLines());
                    String rightText = joinTokens(delta.getTarget().getLines());
                    removedChars += leftText.length();
                    addedChars += rightText.length();
                    appendSpan(leftHtml, leftText, DiffTheme.removedSpanStyle());
                    appendSpan(rightHtml, rightText, DiffTheme.addedSpanStyle());
                }
            }
        }

        leftHtml.append("</pre></body></html>");
        rightHtml.append("</pre></body></html>");
        return new SideBySideDiff(leftHtml.toString(), rightHtml.toString(), removedChars, addedChars);
    }

    private static void appendSpan(StringBuilder html, String text, String style) {
        html.append("<span style=\"").append(style).append("\">").append(htmlEscape(text)).append("</span>");
    }

    private static String joinTokens(List<String> tokens) {
        StringBuilder sb = new StringBuilder();
        for (String token : tokens) {
            sb.append(token);
        }
        return sb.toString();
    }

    private static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher m = WORD_TOKEN.matcher(text);
        while (m.find()) {
            tokens.add(m.group());
        }
        return tokens;
    }

    static String htmlEscape(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\t' -> out.append("&#9;");
                case '\n' -> out.append("<br>");
                default -> out.append(c);
            }
        }
        return out.toString();
    }
}
