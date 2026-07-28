package io.github.gohoski.numai.ui;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.BulletSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;

public class MarkdownParser {

    private static final int COLOR_CODE_BG = 0x22888888;

    public static CharSequence parse(String text) {
        if (text == null || text.length() == 0) {
            return "";
        }

        SpannableStringBuilder sb = new SpannableStringBuilder();
        String[] lines = text.split("\r?\n", -1);

        boolean inCodeBlock = false;
        int codeBlockStart = -1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (line.trim().startsWith("```")) {
                if (!inCodeBlock) {
                    inCodeBlock = true;
                    codeBlockStart = sb.length();
                } else {
                    inCodeBlock = false;
                    if (sb.length() > codeBlockStart) {
                        applyCodeBlockStyle(sb, codeBlockStart, sb.length());
                    }
                }
                if (i < lines.length - 1 && !inCodeBlock) {
                    sb.append("\n");
                }
                continue;
            }

            if (inCodeBlock) {
                sb.append(line);
                if (i < lines.length - 1) {
                    sb.append("\n");
                }
                continue;
            }

            int headerLevel = 0;
            if (line.startsWith("# ")) headerLevel = 1;
            else if (line.startsWith("## ")) headerLevel = 2;
            else if (line.startsWith("### ")) headerLevel = 3;
            else if (line.startsWith("#### ")) headerLevel = 4;

            int lineStart = sb.length();

            if (headerLevel > 0) {
                String headerText = line.substring(headerLevel + 1);
                parseInlineFormatting(sb, headerText);
                int lineEnd = sb.length();

                float scale = 1.4f - (headerLevel * 0.08f);
                sb.setSpan(new RelativeSizeSpan(scale), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new StyleSpan(Typeface.BOLD), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                String bulletText = line.substring(2);
                parseInlineFormatting(sb, bulletText);
                int lineEnd = sb.length();
                sb.setSpan(new BulletSpan(10), lineStart, lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                parseInlineFormatting(sb, line);
            }

            if (i < lines.length - 1) {
                sb.append("\n");
            }
        }

        if (inCodeBlock && codeBlockStart >= 0 && sb.length() > codeBlockStart) {
            applyCodeBlockStyle(sb, codeBlockStart, sb.length());
        }

        return sb;
    }

    private static void applyCodeBlockStyle(SpannableStringBuilder sb, int start, int end) {
        sb.setSpan(new TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new BackgroundColorSpan(COLOR_CODE_BG), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void parseInlineFormatting(SpannableStringBuilder sb, String text) {
        int len = text.length();
        int i = 0;

        while (i < len) {
            if (text.charAt(i) == '`') {
                int end = text.indexOf('`', i + 1);
                if (end != -1) {
                    int startSpan = sb.length();
                    sb.append(text.substring(i + 1, end));
                    int endSpan = sb.length();
                    sb.setSpan(new TypefaceSpan("monospace"), startSpan, endSpan, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new BackgroundColorSpan(COLOR_CODE_BG), startSpan, endSpan, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 1;
                    continue;
                }
            }

            if (text.charAt(i) == '[') {
                int titleEnd = text.indexOf(']', i + 1);
                if (titleEnd != -1 && titleEnd + 1 < len && text.charAt(titleEnd + 1) == '(') {
                    int urlEnd = text.indexOf(')', titleEnd + 2);
                    if (urlEnd != -1) {
                        String title = text.substring(i + 1, titleEnd);
                        String url = text.substring(titleEnd + 2, urlEnd);
                        int startSpan = sb.length();
                        sb.append(title);
                        int endSpan = sb.length();
                        sb.setSpan(new URLSpan(url), startSpan, endSpan, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        i = urlEnd + 1;
                        continue;
                    }
                }
            }

            if (i + 2 < len && text.startsWith("***", i)) {
                int end = text.indexOf("***", i + 3);
                if (end != -1) {
                    int startSpan = sb.length();
                    sb.append(text.substring(i + 3, end));
                    int endSpan = sb.length();
                    sb.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), startSpan, endSpan, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 3;
                    continue;
                }
            }

            if (i + 1 < len && text.startsWith("**", i)) {
                int end = text.indexOf("**", i + 2);
                if (end != -1) {
                    int startSpan = sb.length();
                    sb.append(text.substring(i + 2, end));
                    int endSpan = sb.length();
                    sb.setSpan(new StyleSpan(Typeface.BOLD), startSpan, endSpan, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 2;
                    continue;
                }
            }

            if (text.charAt(i) == '*' || text.charAt(i) == '_') {
                char mark = text.charAt(i);
                int end = text.indexOf(mark, i + 1);
                if (end != -1 && end > i + 1) {
                    int startSpan = sb.length();
                    sb.append(text.substring(i + 1, end));
                    int endSpan = sb.length();
                    sb.setSpan(new StyleSpan(Typeface.ITALIC), startSpan, endSpan, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 1;
                    continue;
                }
            }

            if (i + 1 < len && text.startsWith("~~", i)) {
                int end = text.indexOf("~~", i + 2);
                if (end != -1) {
                    int startSpan = sb.length();
                    sb.append(text.substring(i + 2, end));
                    int endSpan = sb.length();
                    sb.setSpan(new StrikethroughSpan(), startSpan, endSpan, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 2;
                    continue;
                }
            }

            sb.append(text.charAt(i));
            i++;
        }
    }
}
