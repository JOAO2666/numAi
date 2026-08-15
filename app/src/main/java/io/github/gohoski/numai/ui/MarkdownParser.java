package io.github.gohoski.numai.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.text.style.BulletSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.ReplacementSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import io.github.gohoski.numai.R;

public class MarkdownParser {

    private static final int COLOR_CODE_BG = 0x22888888;
    private static final int COLOR_BUTTON_BG = 0xFF2C2C2C;
    private static final int COLOR_BUTTON_TEXT = 0xFF64B5F6;
    private static final int COLOR_QUOTE_TEXT = 0xFFAAAAAA;

    public static CharSequence parse(String text) {
        return parse(null, text, false);
    }

    public static CharSequence parse(Context context, String text) {
        return parse(context, text, false);
    }

    public static CharSequence parse(Context context, String text, boolean isGenerating) {
        if (text == null || text.length() == 0) {
            return "";
        }

        SpannableStringBuilder sb = new SpannableStringBuilder();
        int len = text.length();
        int lineStart = 0;
        boolean inCodeBlock = false;
        int codeBlockStart = -1;

        while (lineStart < len) {
            int lineEnd = text.indexOf('\n', lineStart);
            if (lineEnd == -1) {
                lineEnd = len;
            }

            int curEnd = lineEnd;
            if (curEnd > lineStart && text.charAt(curEnd - 1) == '\r') {
                curEnd--;
            }

            String line = text.substring(lineStart, curEnd);
            boolean isLastLine = (lineEnd == len);

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
                if (!isLastLine && !inCodeBlock) {
                    sb.append("\n");
                }
                lineStart = lineEnd + 1;
                continue;
            }

            if (inCodeBlock) {
                sb.append(line);
                if (!isLastLine) {
                    sb.append("\n");
                }
                lineStart = lineEnd + 1;
                continue;
            }

            if (!isLastLine && isTableLine(line)) {
                int nextLineStart = lineEnd + 1;
                int nextLineEnd = text.indexOf('\n', nextLineStart);
                if (nextLineEnd == -1) nextLineEnd = len;
                int nextCurEnd = nextLineEnd;
                if (nextCurEnd > nextLineStart && text.charAt(nextCurEnd - 1) == '\r') nextCurEnd--;
                String nextLine = text.substring(nextLineStart, nextCurEnd);

                if (isTableDelimiterLine(nextLine)) {
                    List<String> tableLines = new ArrayList<String>();
                    tableLines.add(line);
                    tableLines.add(nextLine);

                    int scanPos = nextLineEnd + 1;
                    while (scanPos < len) {
                        int scanEnd = text.indexOf('\n', scanPos);
                        if (scanEnd == -1) scanEnd = len;
                        int scanCurEnd = scanEnd;
                        if (scanCurEnd > scanPos && text.charAt(scanCurEnd - 1) == '\r') scanCurEnd--;
                        String scanLine = text.substring(scanPos, scanCurEnd);

                        if (isTableLine(scanLine) && !scanLine.trim().startsWith("```")) {
                            tableLines.add(scanLine);
                            scanPos = scanEnd + 1;
                        } else {
                            break;
                        }
                    }
                    boolean tableIsGenerating = isGenerating && (scanPos >= len);
                    renderTable(context, sb, tableLines, tableIsGenerating);
                    lineStart = scanPos;
                    if (lineStart < len) {
                        sb.append("\n");
                    }
                    continue;
                }
            } else if (isGenerating && isLastLine && isTableLine(line)) {
                renderLoadingTable(context, sb);
                lineStart = lineEnd + 1;
                continue;
            }

            int headerLevel = 0;
            if (line.startsWith("# ")) headerLevel = 1;
            else if (line.startsWith("## ")) headerLevel = 2;
            else if (line.startsWith("### ")) headerLevel = 3;
            else if (line.startsWith("#### ")) headerLevel = 4;
            else if (line.startsWith("##### ")) headerLevel = 5;
            else if (line.startsWith("###### ")) headerLevel = 6;

            int sbLineStart = sb.length();

            if (headerLevel > 0) {
                String headerText = line.substring(headerLevel + 1);
                parseInlineFormatting(sb, headerText);
                int sbLineEnd = sb.length();

                float scale = Math.max(1.05f, 1.4f - (headerLevel * 0.07f));
                sb.setSpan(new RelativeSizeSpan(scale), sbLineStart, sbLineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new StyleSpan(Typeface.BOLD), sbLineStart, sbLineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ")) {
                String bulletText = line.substring(2);
                parseInlineFormatting(sb, bulletText);
                int sbLineEnd = sb.length();
                sb.setSpan(new BulletSpan(10), sbLineStart, sbLineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (line.startsWith("> ")) {
                String quoteText = line.substring(2);
                parseInlineFormatting(sb, quoteText);
                int sbLineEnd = sb.length();
                sb.setSpan(new StyleSpan(Typeface.ITALIC), sbLineStart, sbLineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new ForegroundColorSpan(COLOR_QUOTE_TEXT), sbLineStart, sbLineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                parseInlineFormatting(sb, line);
            }

            if (!isLastLine) {
                sb.append("\n");
            }

            lineStart = lineEnd + 1;
        }

        if (inCodeBlock && codeBlockStart >= 0 && sb.length() > codeBlockStart) {
            applyCodeBlockStyle(sb, codeBlockStart, sb.length());
        }

        return sb;
    }

    private static boolean isTableLine(String line) {
        if (line == null) return false;
        String trimmed = line.trim();
        return trimmed.length() > 0 && trimmed.indexOf('|') != -1;
    }

    private static boolean isTableDelimiterLine(String line) {
        if (line == null) return false;
        String trimmed = line.trim();
        if (trimmed.length() == 0 || trimmed.indexOf('-') == -1) return false;
        for (int j = 0; j < trimmed.length(); j++) {
            char c = trimmed.charAt(j);
            if (c != '|' && c != '-' && c != ':' && c != ' ' && c != '\t') {
                return false;
            }
        }
        return true;
    }

    private static List<String> parseTableCells(String line) {
        List<String> cells = new ArrayList<String>();
        if (line == null) return cells;
        String trimmed = line.trim();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        String[] parts = trimmed.split("\\|", -1);
        for (int k = 0; k < parts.length; k++) {
            cells.add(parts[k].trim());
        }
        return cells;
    }

    private static void renderLoadingTable(Context context, SpannableStringBuilder sb) {
        sb.append("\n");
        int btnStart = sb.length();
        String loadingText = context != null ? context.getString(R.string.generating_table) : "Generating table...";
        sb.append(" ").append(loadingText).append(" ");
        int btnEnd = sb.length();
        sb.setSpan(new BackgroundColorSpan(COLOR_CODE_BG), btnStart, btnEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(0x88FFFFFF), btnStart, btnEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(Typeface.ITALIC), btnStart, btnEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("\n");
    }

    private static void renderTable(Context context, SpannableStringBuilder sb, List<String> tableLines, boolean tableIsGenerating) {
        if (tableLines.size() < 2) return;

        if (tableIsGenerating) {
            renderLoadingTable(context, sb);
            return;
        }

        sb.append("\n");

        int btnStart = sb.length();
        String btnText = context != null ? context.getString(R.string.view_table) : "View table";
        sb.append(btnText);
        int btnEnd = sb.length();

        float density = context != null ? context.getResources().getDisplayMetrics().density : 1.0f;
        int padH = (int) (20 * density);
        int padV = (int) (6 * density);
        float radius = 6 * density;

        sb.setSpan(new TableButtonSpan(padH, padV, COLOR_BUTTON_BG, COLOR_BUTTON_TEXT, radius), btnStart, btnEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new TableClickableSpan(tableLines), btnStart, btnEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("\n");
    }

    private static class TableButtonSpan extends ReplacementSpan {
        private final int padHorizontal;
        private final int padVertical;
        private final int bgColor;
        private final int textColor;
        private final float cornerRadius;

        TableButtonSpan(int padHorizontal, int padVertical, int bgColor, int textColor, float cornerRadius) {
            this.padHorizontal = padHorizontal;
            this.padVertical = padVertical;
            this.bgColor = bgColor;
            this.textColor = textColor;
            this.cornerRadius = cornerRadius;
        }

        @Override
        public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            float textWidth = paint.measureText(text, start, end);
            if (fm != null) {
                Paint.FontMetricsInt pfm = paint.getFontMetricsInt();
                fm.ascent = pfm.ascent - padVertical;
                fm.descent = pfm.descent + padVertical;
                fm.top = pfm.top - padVertical;
                fm.bottom = pfm.bottom + padVertical;
            }
            return (int) (textWidth + padHorizontal * 2);
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            float textWidth = paint.measureText(text, start, end);
            Paint.FontMetricsInt fm = paint.getFontMetricsInt();

            float rectTop = y + fm.ascent - padVertical;
            float rectRight = x + textWidth + padHorizontal * 2;
            float rectBottom = y + fm.descent + padVertical;

            int oldColor = paint.getColor();
            Paint.Style oldStyle = paint.getStyle();

            paint.setColor(bgColor);
            paint.setStyle(Paint.Style.FILL);
            RectF rect = new RectF(x, rectTop, rectRight, rectBottom);
            if (cornerRadius > 0) {
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);
            } else {
                canvas.drawRect(rect, paint);
            }

            paint.setColor(textColor);
            canvas.drawText(text, start, end, x + padHorizontal, y, paint);

            paint.setColor(oldColor);
            paint.setStyle(oldStyle);
        }
    }

    private static String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String formatCellMarkdown(String text) {
        if (text == null || text.length() == 0) return "";
        String s = escapeHtml(text);
        s = s.replaceAll("\\[(.*?)\\]\\((.*?)\\)", "<a href=\"$2\" style=\"color:#64b5f6;\">$1</a>");
        s = s.replaceAll("\\*\\*\\*(.*?)\\*\\*\\*", "<b><i>$1</i></b>");
        s = s.replaceAll("___(.*?)___", "<b><i>$1</i></b>");
        s = s.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");
        s = s.replaceAll("__(.*?)__", "<b>$1</b>");
        s = s.replaceAll("\\*(.*?)\\*", "<i>$1</i>");
        s = s.replaceAll("_(.*?)_", "<i>$1</i>");
        s = s.replaceAll("~~(.*?)~~", "<s>$1</s>");
        s = s.replaceAll("`(.*?)`", "<code>$1</code>");
        return s;
    }

    private static String buildHtmlTable(List<String> headers, List<List<String>> rows) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head>");
        html.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">");
        html.append("<meta name=\"viewport\" content=\"initial-scale=1.0, user-scalable=yes\">");
        html.append("<style>");
        html.append("html, body { background-color: #121212; color: #e0e0e0; font-family: sans-serif; padding: 8px; margin: 0; }");
        html.append("table { border-collapse: collapse; white-space: nowrap; margin: 0; }");
        html.append("th, td { border: 1px solid #444444; padding: 8px 12px; text-align: left; }");
        html.append("th { background-color: #2c2c2c; color: #64b5f6; font-weight: bold; }");
        html.append("tr:nth-child(even) { background-color: #1e1e1e; }");
        html.append("code { background-color: #2b2b2b; padding: 2px 4px; font-family: monospace; }");
        html.append("</style></head><body>");
        html.append("<table><thead><tr>");

        for (int i = 0; i < headers.size(); i++) {
            html.append("<th>").append(formatCellMarkdown(headers.get(i))).append("</th>");
        }
        html.append("</tr></thead><tbody>");

        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            html.append("<tr>");
            for (int c = 0; c < headers.size(); c++) {
                String val = (c < row.size()) ? row.get(c) : "";
                html.append("<td>").append(formatCellMarkdown(val)).append("</td>");
            }
            html.append("</tr>");
        }

        html.append("</tbody></table></body></html>");
        return html.toString();
    }

    private static class TableClickableSpan extends ClickableSpan {
        private final List<String> tableLines;

        TableClickableSpan(List<String> tableLines) {
            this.tableLines = tableLines;
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(false);
        }

        @Override
        public void onClick(View widget) {
            try {
                if (tableLines == null || tableLines.size() < 2) return;
                List<String> headers = parseTableCells(tableLines.get(0));
                List<List<String>> rows = new ArrayList<List<String>>();
                for (int i = 2; i < tableLines.size(); i++) {
                    rows.add(parseTableCells(tableLines.get(i)));
                }
                String htmlTable = buildHtmlTable(headers, rows);

                Context context = widget.getContext();
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(R.string.view_table);

                WebView webView = new WebView(context);
                WebSettings settings = webView.getSettings();
                settings.setUseWideViewPort(true);
                settings.setSupportZoom(true);

                // Use reflection for API 3+ zoom controls to prevent VerifyError on Android 1.0/1.1
                try {
                    Method m = settings.getClass().getMethod("setBuiltInZoomControls", boolean.class);
                    m.invoke(settings, true);
                } catch (Throwable ignored) {}

                webView.setHorizontalScrollBarEnabled(true);
                webView.setVerticalScrollBarEnabled(true);

                webView.loadDataWithBaseURL(null, htmlTable, "text/html", "UTF-8", null);

                builder.setView(webView);
                builder.setPositiveButton(android.R.string.ok, null);
                builder.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void applyCodeBlockStyle(SpannableStringBuilder sb, int start, int end) {
        sb.setSpan(new TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new BackgroundColorSpan(COLOR_CODE_BG), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static boolean hasMarkdownSymbols(String text) {
        if (text == null || text.length() == 0) return false;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '`' || c == '[' || c == '*' || c == '_' || c == '~') {
                return true;
            }
            if (c == 'h' && (text.startsWith("http://", i) || text.startsWith("https://", i))) {
                return true;
            }
        }
        return false;
    }

    private static int findUrlEnd(String text, int start) {
        int len = text.length();
        int i = start;
        int parenDepth = 0;
        while (i < len) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '<' || c == '"' || c == '\'') {
                break;
            }
            if (c == '(') {
                parenDepth++;
            } else if (c == ')') {
                if (parenDepth == 0) break;
                parenDepth--;
            } else if (c == ']') {
                break;
            }
            i++;
        }
        while (i > start) {
            char c = text.charAt(i - 1);
            if (c == '.' || c == ',' || c == ';' || c == ':' || c == '!' || c == '?') {
                i--;
            } else {
                break;
            }
        }
        return i;
    }

    private static void parseInlineFormatting(SpannableStringBuilder sb, String text) {
        if (text == null || text.length() == 0) {
            return;
        }
        if (!hasMarkdownSymbols(text)) {
            sb.append(text);
            return;
        }

        int len = text.length();
        int i = 0;

        while (i < len) {
            // Inline code `code` (raw, no nested styling)
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

            // Markdown links [title](url) with nested formatting inside title
            if (text.charAt(i) == '[') {
                int titleEnd = text.indexOf(']', i + 1);
                if (titleEnd != -1 && titleEnd + 1 < len && text.charAt(titleEnd + 1) == '(') {
                    int urlEnd = text.indexOf(')', titleEnd + 2);
                    if (urlEnd != -1) {
                        String title = text.substring(i + 1, titleEnd);
                        String url = text.substring(titleEnd + 2, urlEnd);
                        int startSpan = sb.length();
                        parseInlineFormatting(sb, title);
                        int endSpan = sb.length();
                        sb.setSpan(new URLSpan(url), startSpan, endSpan, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        i = urlEnd + 1;
                        continue;
                    }
                }
            }

            // Raw auto-links http:// or https://
            if (text.charAt(i) == 'h' && (text.startsWith("http://", i) || text.startsWith("https://", i))) {
                int urlEnd = findUrlEnd(text, i);
                if (urlEnd > i) {
                    String url = text.substring(i, urlEnd);
                    int startSpan = sb.length();
                    sb.append(url);
                    int endSpan = sb.length();
                    sb.setSpan(new URLSpan(url), startSpan, endSpan, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = urlEnd;
                    continue;
                }
            }

            // Bold + Italic ***text*** or ___text___
            if (i + 2 < len && (text.startsWith("***", i) || text.startsWith("___", i))) {
                String delim = text.substring(i, i + 3);
                int end = text.indexOf(delim, i + 3);
                if (end != -1 && end > i + 3) {
                    int startSpan = sb.length();
                    parseInlineFormatting(sb, text.substring(i + 3, end));
                    int endSpan = sb.length();
                    sb.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), startSpan, endSpan, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 3;
                    continue;
                }
            }

            // Bold **text** or __text__
            if (i + 1 < len && (text.startsWith("**", i) || text.startsWith("__", i))) {
                String delim = text.substring(i, i + 2);
                int end = text.indexOf(delim, i + 2);
                if (end != -1 && end > i + 2) {
                    int startSpan = sb.length();
                    parseInlineFormatting(sb, text.substring(i + 2, end));
                    int endSpan = sb.length();
                    sb.setSpan(new StyleSpan(Typeface.BOLD), startSpan, endSpan, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 2;
                    continue;
                }
            }

            // Italic *text* or _text_
            if (text.charAt(i) == '*' || text.charAt(i) == '_') {
                char mark = text.charAt(i);
                int end = text.indexOf(mark, i + 1);
                if (end != -1 && end > i + 1) {
                    // Prevent false positives on math expressions like "2 * 3 = 6"
                    if (text.charAt(i + 1) != ' ' && text.charAt(end - 1) != ' ') {
                        int startSpan = sb.length();
                        parseInlineFormatting(sb, text.substring(i + 1, end));
                        int endSpan = sb.length();
                        sb.setSpan(new StyleSpan(Typeface.ITALIC), startSpan, endSpan, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        i = end + 1;
                        continue;
                    }
                }
            }

            // Strikethrough ~~text~~
            if (i + 1 < len && text.startsWith("~~", i)) {
                int end = text.indexOf("~~", i + 2);
                if (end != -1 && end > i + 2) {
                    int startSpan = sb.length();
                    parseInlineFormatting(sb, text.substring(i + 2, end));
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