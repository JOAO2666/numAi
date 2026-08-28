package io.github.gohoski.numai.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.util.AttributeSet;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import android.view.ViewGroup;

/** Markdown renderer that delegates correctly delimited formulas to MathJax. */
public class MathMarkdownView extends WebView {
    private static final String MATHJAX_BASE_URL =
            "https://cdn.jsdelivr.net/npm/mathjax@3/es5/";
    private String lastMarkdown = null;
    private int lastHeight = -1;
    private boolean renderFailed;
    private RenderErrorListener renderErrorListener;

    public MathMarkdownView(Context context) {
        super(context);
        initialize();
    }

    public MathMarkdownView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public MathMarkdownView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize();
    }

    private void initialize() {
        setBackgroundColor(Color.TRANSPARENT);
        setVerticalScrollBarEnabled(false);
        setHorizontalScrollBarEnabled(false);
        setFocusable(false);
        setFocusableInTouchMode(false);

        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setSupportZoom(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        // MathJax is loaded from the configured CDN. Responses without math
        // never enter this view, so the native parser remains the offline
        // fallback for legacy devices or unavailable connectivity.
        if (Integer.parseInt(Build.VERSION.SDK) >= 8) settings.setBlockNetworkLoads(false);

        addJavascriptInterface(new HeightBridge(this), "NumAiMath");
        setWebViewClient(new WebViewClient());
    }

    public interface RenderErrorListener {
        void onRenderError();
    }

    public void setRenderErrorListener(RenderErrorListener listener) {
        renderErrorListener = listener;
    }

    public String getMarkdown() { return lastMarkdown; }

    private void notifyRenderError() {
        renderFailed = true;
        final RenderErrorListener listener = renderErrorListener;
        if (listener != null) {
            post(new Runnable() {
                @Override
                public void run() {
                    if (listener == renderErrorListener) listener.onRenderError();
                }
            });
        }
    }

    public static boolean canRender(String markdown) {
        if (markdown == null || markdown.length() == 0) return false;
        if (hasPaired(markdown, "$$", "$$", 2)) return true;
        if (hasPaired(markdown, "\\[", "\\]", 2)) return true;
        if (hasPaired(markdown, "\\(", "\\)", 2)) return true;

        int length = markdown.length();
        for (int i = 0; i < length; i++) {
            if (markdown.charAt(i) != '$' || isEscaped(markdown, i)) continue;
            if (i + 1 < length && markdown.charAt(i + 1) == '$') {
                i++;
                continue;
            }
            int end = findInlineDollarEnd(markdown, i + 1);
            if (end > i + 1) return true;
        }
        return false;
    }

    public void setMarkdown(String markdown) {
        String safeMarkdown = markdown == null ? "" : markdown;
        if (safeMarkdown.equals(lastMarkdown) && !renderFailed) return;
        lastMarkdown = safeMarkdown;
        lastHeight = -1;
        renderFailed = false;
        loadDataWithBaseURL(MATHJAX_BASE_URL, buildDocument(safeMarkdown),
                "text/html", "UTF-8", null);
    }

    public void updateHeight(final int height) {
        post(new Runnable() {
            @Override
            public void run() {
                int safeHeight = height;
                if (safeHeight < 1) safeHeight = 1;
                if (safeHeight > 100000) safeHeight = 100000;
                if (safeHeight == lastHeight) return;
                lastHeight = safeHeight;
                ViewGroup.LayoutParams params = getLayoutParams();
                if (params != null) {
                    params.height = safeHeight;
                    setLayoutParams(params);
                }
            }
        });
    }

    public static class HeightBridge {
        private final MathMarkdownView view;

        public HeightBridge(MathMarkdownView view) {
            this.view = view;
        }

        @JavascriptInterface
        public void setHeight(int height) {
            if (view != null) view.updateHeight(height);
        }

        @JavascriptInterface
        public void onRenderError() {
            if (view != null) view.notifyRenderError();
        }
    }

    static String buildDocument(String markdown) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head>");
        html.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">");
        html.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">");
        html.append("<script>window.MathJax={tex:{inlineMath:[[\"$\",\"$\"],[\"\\\\(\",\"\\\\)\"]],displayMath:[[\"$$\",\"$$\"],[\"\\\\[\",\"\\\\]\"]],processEscapes:true,packages:{'[+]':['noerrors','noundefined']}},options:{skipHtmlTags:['script','noscript','style','textarea','pre','code']}};</script>");
        html.append("<script async src=\"tex-chtml.js\"></script>");
        html.append("<style>");
        html.append("html,body{margin:0;padding:0;background:transparent;color:#f2f2f2;font-family:sans-serif;font-size:14px;line-height:1.38;word-wrap:break-word;}");
        html.append(".p{margin:0 0 7px 0}.h1{font-size:1.38em;font-weight:bold;margin:5px 0 8px 0}.h2{font-size:1.24em;font-weight:bold;margin:5px 0 7px 0}.h3{font-size:1.12em;font-weight:bold;margin:4px 0 6px 0}.li{margin:0 0 3px 12px;padding-left:4px}.quote{border-left:2px solid #2b88d9;padding-left:8px;color:#d0d0d0;margin:3px 0 7px 0}.code{font-family:monospace;background:#2b2b2b;padding:1px 3px}.block-code{white-space:pre-wrap;font-family:monospace;background:#242424;padding:7px;margin:0 0 7px 0;overflow-x:auto}.math-block{overflow-x:auto;overflow-y:hidden;padding:3px 0}.MathJax{color:#f2f2f2}.MathJax_Display,mjx-container[display=\"true\"]{overflow-x:auto;overflow-y:hidden;margin:10px 0;padding:2px 0}a{color:#64b5f6;text-decoration:none}pre{margin:0}");
        html.append("</style></head><body>");
        appendMarkdown(html, markdown);
        html.append("<script>");
        html.append("function reportHeight(){var h=Math.ceil(document.body.scrollHeight*(window.devicePixelRatio||1));if(window.NumAiMath){window.NumAiMath.setHeight(h);}};");
        html.append("var mathJaxAttempts=0;function renderMathJax(){if(window.MathJax&&window.MathJax.typesetPromise){window.MathJax.typesetPromise().then(function(){reportHeight();setTimeout(reportHeight,120);}).catch(function(){reportHeight();if(window.NumAiMath){window.NumAiMath.onRenderError();}});}else{reportHeight();if(++mathJaxAttempts<30){setTimeout(renderMathJax,120);}else if(window.NumAiMath){window.NumAiMath.onRenderError();}}};");
        html.append("if(window.addEventListener){window.addEventListener('load',renderMathJax,false);}else{window.onload=renderMathJax;}");
        html.append("</script></body></html>");
        return html.toString();
    }

    private static void appendMarkdown(StringBuilder html, String markdown) {
        boolean inCodeBlock = false;
        String displayMathEnd = null;
        StringBuilder displayMath = new StringBuilder();
        int start = 0;
        int length = markdown.length();
        while (start <= length) {
            int end = markdown.indexOf('\n', start);
            if (end == -1) end = length;
            String line = markdown.substring(start, end);
            if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);

            String trimmed = line.trim();
            if (line.trim().startsWith("```")) {
                if (inCodeBlock) html.append("</code></pre>");
                else html.append("<pre class=\"block-code\"><code>");
                inCodeBlock = !inCodeBlock;
            } else if (inCodeBlock) {
                html.append(escapeHtml(line));
                if (end < length) html.append("\n");
            } else if (displayMathEnd != null) {
                if (trimmed.equals(displayMathEnd)) {
                    appendDisplayMath(html, displayMath.toString());
                    displayMath.setLength(0);
                    displayMathEnd = null;
                } else {
                    if (displayMath.length() > 0) displayMath.append('\n');
                    displayMath.append(line);
                }
            } else if ("$$".equals(trimmed)) {
                displayMathEnd = "$$";
                displayMath.setLength(0);
            } else if ("\\[".equals(trimmed)) {
                displayMathEnd = "\\]";
                displayMath.setLength(0);
            } else {
                appendMarkdownLine(html, line);
            }

            if (end == length) break;
            start = end + 1;
        }
        if (inCodeBlock) html.append("</code></pre>");
        if (displayMathEnd != null) appendDisplayMath(html, displayMath.toString());
    }

    private static void appendDisplayMath(StringBuilder html, String tex) {
        html.append("<div class=\"p math-block\">\\[");
        html.append(escapeHtml(tex));
        html.append("\\]</div>");
    }

    private static void appendMarkdownLine(StringBuilder html, String line) {
        if (line.length() == 0) {
            html.append("<div class=\"p\">&nbsp;</div>");
        } else if (line.startsWith("### ")) {
            html.append("<div class=\"h3\">");
            appendInline(html, line.substring(4));
            html.append("</div>");
        } else if (line.startsWith("## ")) {
            html.append("<div class=\"h2\">");
            appendInline(html, line.substring(3));
            html.append("</div>");
        } else if (line.startsWith("# ")) {
            html.append("<div class=\"h1\">");
            appendInline(html, line.substring(2));
            html.append("</div>");
        } else if (line.startsWith("- ") || line.startsWith("* ")) {
            html.append("<div class=\"li\">&bull; ");
            appendInline(html, line.substring(2));
            html.append("</div>");
        } else if (line.startsWith("> ")) {
            html.append("<div class=\"quote\">");
            appendInline(html, line.substring(2));
            html.append("</div>");
        } else {
            html.append("<div class=\"p\">");
            appendInline(html, line);
            html.append("</div>");
        }
    }

    private static void appendInline(StringBuilder html, String text) {
        int cursor = 0;
        while (cursor < text.length()) {
            MathRange range = findNextMathRange(text, cursor);
            if (range == null) {
                html.append(formatText(text.substring(cursor)));
                break;
            }
            if (range.start > cursor) html.append(formatText(text.substring(cursor, range.start)));
            html.append(range.display ? "\\[" : "\\(");
            html.append(escapeHtml(range.tex));
            html.append(range.display ? "\\]" : "\\)");
            cursor = range.end;
        }
    }

    private static MathRange findNextMathRange(String text, int from) {
        int length = text.length();
        for (int i = from; i < length; i++) {
            if (text.startsWith("$$", i) && !isEscaped(text, i)) {
                int end = text.indexOf("$$", i + 2);
                if (end != -1) return new MathRange(i, end + 2, text.substring(i + 2, end), true);
            }
            if (text.startsWith("\\[", i) && !isEscaped(text, i)) {
                int end = text.indexOf("\\]", i + 2);
                if (end != -1) return new MathRange(i, end + 2, text.substring(i + 2, end), true);
            }
            if (text.startsWith("\\(", i) && !isEscaped(text, i)) {
                int end = text.indexOf("\\)", i + 2);
                if (end != -1) return new MathRange(i, end + 2, text.substring(i + 2, end), false);
            }
            if (text.charAt(i) == '$' && !isEscaped(text, i) && (i + 1 >= length || text.charAt(i + 1) != '$')) {
                int end = findInlineDollarEnd(text, i + 1);
                if (end > i + 1) return new MathRange(i, end + 1, text.substring(i + 1, end), false);
            }
        }
        return null;
    }

    private static boolean hasPaired(String text, String open, String close, int afterOpen) {
        int start = text.indexOf(open);
        return start != -1 && text.indexOf(close, start + afterOpen) != -1;
    }

    private static int findInlineDollarEnd(String text, int from) {
        if (from >= text.length() || Character.isWhitespace(text.charAt(from))) return -1;
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') return -1;
            if (c == '$' && !isEscaped(text, i) && i > from && !Character.isWhitespace(text.charAt(i - 1))) return i;
        }
        return -1;
    }

    private static boolean isEscaped(String text, int position) {
        int slashCount = 0;
        for (int i = position - 1; i >= 0 && text.charAt(i) == '\\'; i--) slashCount++;
        return (slashCount % 2) == 1;
    }

    private static String formatText(String text) {
        String formatted = escapeHtml(text);
        formatted = formatted.replaceAll("`([^`]+)`", "<span class=\"code\">$1</span>");
        formatted = formatted.replaceAll("\\*\\*([^*]+)\\*\\*", "<b>$1</b>");
        formatted = formatted.replaceAll("__([^_]+)__", "<b>$1</b>");
        formatted = formatted.replaceAll("\\*([^*]+)\\*", "<i>$1</i>");
        formatted = formatted.replaceAll("_([^_]+)_", "<i>$1</i>");
        formatted = formatted.replaceAll("~~([^~]+)~~", "<s>$1</s>");
        return formatted;
    }

    private static String escapeHtml(String text) {
        if (text == null || text.length() == 0) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static class MathRange {
        int start;
        int end;
        String tex;
        boolean display;

        MathRange(int start, int end, String tex, boolean display) {
            this.start = start;
            this.end = end;
            this.tex = tex;
            this.display = display;
        }
    }
}
