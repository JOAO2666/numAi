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
import android.webkit.WebView;

import java.util.ArrayList;
import java.util.List;

import io.github.gohoski.numai.R;

public class MarkdownParser {

    private static final int COLOR_CODE_BG = 0x22888888;
    private static final int COLOR_BUTTON_BG = 0xFF2C2C2C;
    private static final int COLOR_BUTTON_TEXT = 0xFF64B5F6;

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

            if (i + 1 < lines.length && isTableLine(line) && isTableDelimiterLine(lines[i + 1])) {
                List<String> tableLines = new ArrayList<String>();
                tableLines.add(line);
                tableLines.add(lines[i + 1]);
                int j = i + 2;
                while (j < lines.length && isTableLine(lines[j]) && !lines[j].trim().startsWith("```")) {
                    tableLines.add(lines[j]);
                    j++;
                }
                boolean tableIsGenerating = isGenerating && (j >= lines.length);
                renderTable(context, sb, tableLines, tableIsGenerating);
                i = j - 1;
                if (i < lines.length - 1) {
                    sb.append("\n");
                }
                continue;
            } else if (isGenerating && i == lines.length - 1 && isTableLine(line)) {
                renderLoadingTable(context, sb);
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

    private static boolean isTableLine(String line) {
        if (line == null) return false;
        String trimmed = line.trim();
        return trimmed.length() > 0 && trimmed.contains("|");
    }

    private static boolean isTableDelimiterLine(String line) {
        if (line == null) return false;
        String trimmed = line.trim();
        if (trimmed.length() == 0 || !trimmed.contains("-")) return false;
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
        String[] parts = trimmed.split("\\|");
        for (int k = 0; k < parts.length; k++) {
            cells.add(parts[k].trim());
        }
        return cells;
    }

    private static void renderLoadingTable(Context context, SpannableStringBuilder sb) {
        sb.append("\n");
        int btnStart = sb.length();
        String loadingText = context.getString(R.string.generating_table);
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

        List<String> headers = parseTableCells(tableLines.get(0));
        List<List<String>> rows = new ArrayList<List<String>>();
        for (int i = 2; i < tableLines.size(); i++) {
            rows.add(parseTableCells(tableLines.get(i)));
        }
        if (headers.isEmpty()) return;

        String htmlTable = buildHtmlTable(headers, rows);
        sb.append("\n");

        int btnStart = sb.length();
        sb.append(context.getString(R.string.view_table));
        int btnEnd = sb.length();

        float density = context.getResources().getDisplayMetrics().density;
        int padH = (int) (20 * density);
        int padV = (int) (6 * density);
        float radius = 6 * density;

        sb.setSpan(new TableButtonSpan(padH, padV, COLOR_BUTTON_BG, COLOR_BUTTON_TEXT, radius), btnStart, btnEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new TableClickableSpan(htmlTable), btnStart, btnEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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

//            float rectLeft = x;
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
        s = s.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");
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
        private final String htmlTable;

        TableClickableSpan(String htmlTable) {
            this.htmlTable = htmlTable;
        }

        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(false);
        }

        @Override
        public void onClick(View widget) {
            try {
                Context context = widget.getContext();
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(R.string.view_table);

                WebView webView = new WebView(context);
                webView.getSettings().setUseWideViewPort(true);
                webView.getSettings().setBuiltInZoomControls(true);
                webView.getSettings().setSupportZoom(true);
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