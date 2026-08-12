package io.github.gohoski.numai.search;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import io.github.gohoski.numai.api.ApiClient;
import io.github.gohoski.numai.api.ApiError;
import io.github.gohoski.numai.api.ApiRequest;
import io.github.gohoski.numai.api.ApiResponse;

public class WebFetcher {
    private static final int MAX_TEXT_LENGTH = 15000;
    private static final int MAX_HTML_BYTES = 512 * 1024;
    private ApiClient api;

    public WebFetcher() {
        api = new ApiClient(null);
    }

    public String fetch(String targetUrl) throws IOException, ApiError {
        String html = fetchHtml(targetUrl);
        if (html.trim().length() == 0) {
            return "Empty page content.";
        }
        HtmlParser.Node root = HtmlParser.parseHtmlTree(html);
        StringBuilder sb = new StringBuilder();
        renderNodeToMarkdown(root, sb, targetUrl);
        String result = cleanMarkdownWhitespace(sb.toString());
        if (result.length() > MAX_TEXT_LENGTH) {
            result = result.substring(0, MAX_TEXT_LENGTH) + "\n\n...[Content truncated]";
        }
        return result.length() > 0 ? result : "No readable text content extracted from page.";
    }

    private String fetchHtml(String targetUrl) throws IOException, ApiError {
        ApiRequest request = new ApiRequest(targetUrl, "", "GET")
                .addHeader("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_7_8 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Mobile/15E148 Safari/604.1")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8")
                .addHeader("Upgrade-Insecure-Requests", "1")
                .addHeader("Sec-Fetch-Site", "none")
                .addHeader("Sec-Fetch-Mode", "navigate")
                .addHeader("Sec-Fetch-User", "?1")
                .addHeader("Sec-Fetch-Dest", "document");

        ApiResponse response = api.execute(request);
        if (!response.isSuccessful()) {
            throw new IOException("HTTP Error Response: " + response.getStatusCode());
        }

        byte[] rawBytes;
        InputStream is = response.getBody();
        try {
            rawBytes = readAllBytes(is);
        } finally {
            try { is.close(); } catch (IOException ignored) {}
        }

        String charset = parseCharset(response.getHeader("Content-Type"));
        if (charset == null) {
            charset = detectMetaCharset(rawBytes);
        }
        if (charset == null) {
            charset = "UTF-8";
        }

        try {
            return new String(rawBytes, charset);
        } catch (Exception e) {
            return new String(rawBytes, "UTF-8");
        }
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(32768);
        byte[] buffer = new byte[8192];
        int len;
        int totalRead = 0;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
            totalRead += len;
            if (totalRead >= MAX_HTML_BYTES) {
                break;
            }
        }
        return baos.toByteArray();
    }

    private static String parseCharset(String contentType) {
        if (contentType == null) return null;
        String lower = contentType.toLowerCase();
        int idx = lower.indexOf("charset=");
        if (idx != -1) {
            String cs = contentType.substring(idx + 8).trim();
            int semi = cs.indexOf(';');
            if (semi != -1) cs = cs.substring(0, semi).trim();
            return cs.replace("\"", "").replace("'", "").trim();
        }
        return null;
    }

    private static String detectMetaCharset(byte[] bytes) {
        int limit = Math.min(bytes.length, 2048);
        String preview = new String(bytes, 0, limit);
        return parseCharset(preview);
    }

    private static void renderNodeToMarkdown(HtmlParser.Node node, StringBuilder sb, String baseUrl) {
        if (sb.length() >= MAX_TEXT_LENGTH || isIgnoredNode(node)) {
            return;
        }

        if ("#text".equals(node.tagName)) {
            if (node.text != null && node.text.trim().length() > 0) {
                sb.append(node.text.trim()).append(" ");
            }
            return;
        }

        String tag = node.tagName != null ? node.tagName.toLowerCase() : "";

        if ("a".equals(tag)) {
            String href = node.getAttribute("href");
            String resolvedHref = resolveUrl(baseUrl, href);
            boolean validLink = resolvedHref.length() > 0 && !resolvedHref.startsWith("javascript:");

            int startLen = sb.length();
            if (validLink) {
                sb.append(" [");
            } else {
                sb.append(" ");
            }

            int textStartLen = sb.length();
            int childCount = node.children.size();
            for (int i = 0; i < childCount; i++) {
                if (sb.length() >= MAX_TEXT_LENGTH) break;
                renderNodeToMarkdown(node.children.get(i), sb, baseUrl);
            }

            if (sb.length() == textStartLen) {
                sb.setLength(startLen);
            } else if (validLink) {
                sb.append("](").append(resolvedHref).append(") ");
            } else {
                sb.append(" ");
            }
            return;
        }

        boolean isBlock = isBlockTag(tag);
        if (isBlock) sb.append("\n");

        if (tag.length() == 2 && tag.charAt(0) == 'h' && Character.isDigit(tag.charAt(1))) {
            int level = tag.charAt(1) - '0';
            for (int k = 0; k < level; k++) sb.append("#");
            sb.append(" ");
        } else if ("li".equals(tag)) {
            sb.append("* ");
        } else if ("blockquote".equals(tag)) {
            sb.append("> ");
        }

        int childCount = node.children.size();
        for (int i = 0; i < childCount; i++) {
            if (sb.length() >= MAX_TEXT_LENGTH) break;
            renderNodeToMarkdown(node.children.get(i), sb, baseUrl);
        }

        if (isBlock || "br".equals(tag)) {
            sb.append("\n");
        }
    }

    private static boolean isIgnoredNode(HtmlParser.Node node) {
        if (!node.isElement()) return false;
        String tag = node.tagName != null ? node.tagName.toLowerCase() : "";

        if ("script".equals(tag) || "style".equals(tag) || "noscript".equals(tag) ||
                "head".equals(tag) || "iframe".equals(tag) || "nav".equals(tag) ||
                "footer".equals(tag) || "svg".equals(tag) || "form".equals(tag) ||
                "canvas".equals(tag) || "button".equals(tag) || "select".equals(tag)) {
            return true;
        }

        String style = node.getAttribute("style");
        if (style != null) {
            String lowerStyle = style.toLowerCase();
            if (lowerStyle.contains("display:none") || lowerStyle.contains("visibility:hidden")) {
                return true;
            }
        }
        return "true".equalsIgnoreCase(node.getAttribute("aria-hidden"));
    }

    private static boolean isBlockTag(String tag) {
        return "p".equals(tag) || "div".equals(tag) || "article".equals(tag) ||
                "section".equals(tag) || "h1".equals(tag) || "h2".equals(tag) ||
                "h3".equals(tag) || "h4".equals(tag) || "h5".equals(tag) ||
                "h6".equals(tag) || "li".equals(tag) || "tr".equals(tag) ||
                "blockquote".equals(tag) || "header".equals(tag) || "main".equals(tag);
    }

    private static String resolveUrl(String baseUrl, String relativeUrl) {
        if (relativeUrl == null || relativeUrl.trim().length() == 0) return "";
        relativeUrl = relativeUrl.trim();
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl;
        }
        if (relativeUrl.startsWith("//")) {
            return "https:" + relativeUrl;
        }
        if (relativeUrl.startsWith("javascript:") || relativeUrl.startsWith("mailto:") || relativeUrl.startsWith("#")) {
            return "";
        }
        try {
            URL base = new URL(baseUrl);
            URL resolved = new URL(base, relativeUrl);
            return resolved.toString();
        } catch (Exception e) {
            return relativeUrl;
        }
    }

    private static String cleanMarkdownWhitespace(String input) {
        if (input == null || input.length() == 0) return "";
        int len = input.length();
        StringBuilder sb = new StringBuilder(Math.min(len, MAX_TEXT_LENGTH + 100));
        int emptyLineCount = 0;
        int i = 0;

        while (i < len) {
            int lineEnd = input.indexOf('\n', i);
            if (lineEnd == -1) lineEnd = len;

            int start = i;
            int end = lineEnd;
            while (start < end && Character.isWhitespace(input.charAt(start))) start++;
            while (end > start && Character.isWhitespace(input.charAt(end - 1))) end--;

            if (start == end) {
                emptyLineCount++;
                if (emptyLineCount <= 1) {
                    sb.append('\n');
                }
            } else {
                emptyLineCount = 0;
                sb.append(input, start, end).append('\n');
            }

            i = lineEnd + 1;
        }
        return sb.toString().trim();
    }
}