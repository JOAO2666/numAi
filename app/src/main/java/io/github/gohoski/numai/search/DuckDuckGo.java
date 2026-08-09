package io.github.gohoski.numai.search;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import io.github.gohoski.numai.api.ApiClient;
import io.github.gohoski.numai.api.ApiError;
import io.github.gohoski.numai.api.ApiRequest;
import io.github.gohoski.numai.api.ApiResponse;

/**
 * Created by Glev on 07.08.2026.
 */
class DuckDuckGo implements SearchEngine {
    private final ApiClient api;

    DuckDuckGo() {
        this.api = new ApiClient(null);
    }

    @Override
    public List<SearchResult> search(String query) throws SearchException, ApiError, IOException {
        if (query == null || query.trim().length() == 0) {
            return new ArrayList<SearchResult>();
        }

        String postBody = "q=" + URLEncoder.encode(query, "UTF-8") + "&b=";

        ApiRequest request = new ApiRequest("https://html.duckduckgo.com", "/html/", "POST");
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0");
        request.addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        request.addHeader("Accept-Language", "ru-RU,ru;q=0.9,en-GB;q=0.8,en;q=0.7");
        request.addHeader("Content-Type", "application/x-www-form-urlencoded");
        request.addHeader("Referer", "https://html.duckduckgo.com/");
        request.addHeader("Origin", "https://html.duckduckgo.com");
        request.addHeader("Upgrade-Insecure-Requests", "1");
        request.addHeader("Sec-Fetch-Dest", "document");
        request.addHeader("Sec-Fetch-Mode", "navigate");
        request.addHeader("Sec-Fetch-Site", "same-origin");
        request.addHeader("Sec-Fetch-User", "?1");
        request.addHeader("Priority", "u=0, i");
        request.setBody(postBody);

        ApiResponse response = api.execute(request);
        String html = readResponseAsString(response);
        System.out.println(html);
        return parse(html);
    }

    private String readResponseAsString(ApiResponse response) throws IOException {
        if (response == null || response.getBody() == null) {
            return "";
        }
        InputStream is = response.getBody();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"), 8192);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } finally {
            try {
                is.close();
            } catch (IOException ignored) {}
        }
    }

    private List<SearchResult> parse(String html) {
        List<SearchResult> results = new ArrayList<SearchResult>();
        if (html == null || html.trim().length() == 0) {
            return results;
        }

        HtmlParser.Node root = HtmlParser.parseHtmlTree(html);
        List<HtmlParser.Node> bodyNodes = new ArrayList<HtmlParser.Node>();
        findResultBodyNodes(root, bodyNodes);

        for (int i = 0; i < bodyNodes.size(); i++) {
            HtmlParser.Node bodyNode = bodyNodes.get(i);

            // Extract Title and URL from <a class="result__a">
            HtmlParser.Node titleANode = findNodeByClass(bodyNode, "result__a");
            String title = "";
            String url = "";
            if (titleANode != null) {
                title = extractText(titleANode);
                url = titleANode.getAttribute("href");
            }

            // Fallback for title from <h2 class="result__title"> if titleANode missing
            if (title.length() == 0) {
                HtmlParser.Node h2Node = findNodeByClass(bodyNode, "result__title");
                if (h2Node != null) {
                    title = extractText(h2Node);
                }
            }

            // Extract Snippet from <a class="result__snippet">
            HtmlParser.Node snippetNode = findNodeByClass(bodyNode, "result__snippet");
            String snippet = snippetNode != null ? extractText(snippetNode) : "";

            if (url == null) {
                url = "";
            }

            if (title.length() > 0 || url.length() > 0) {
                results.add(new SearchResult(title, url, snippet));
            }
        }

        return results;
    }

    private void findResultBodyNodes(HtmlParser.Node node, List<HtmlParser.Node> result) {
        if ("div".equals(node.tagName) && node.hasClass("result__body")) {
            result.add(node);
            return;
        }
        for (int i = 0; i < node.children.size(); i++) {
            findResultBodyNodes(node.children.get(i), result);
        }
    }

    private HtmlParser.Node findNodeByClass(HtmlParser.Node node, String className) {
        if (node.hasClass(className)) {
            return node;
        }
        for (int i = 0; i < node.children.size(); i++) {
            HtmlParser.Node res = findNodeByClass(node.children.get(i), className);
            if (res != null) {
                return res;
            }
        }
        return null;
    }

    private String extractText(HtmlParser.Node node) {
        StringBuilder sb = new StringBuilder();
        collectAllText(node, sb);
        return cleanWhitespace(sb.toString());
    }

    private void collectAllText(HtmlParser.Node node, StringBuilder sb) {
        if ("#text".equals(node.tagName)) {
            if (node.text != null) {
                sb.append(node.text).append(" ");
            }
            return;
        }
        for (int i = 0; i < node.children.size(); i++) {
            collectAllText(node.children.get(i), sb);
        }
    }

    private String cleanWhitespace(String str) {
        if (str == null || str.length() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(str.length());
        boolean lastSpace = false;
        int len = str.length();
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!lastSpace) {
                    sb.append(' ');
                    lastSpace = true;
                }
            } else {
                sb.append(c);
                lastSpace = false;
            }
        }
        return sb.toString().trim();
    }
}