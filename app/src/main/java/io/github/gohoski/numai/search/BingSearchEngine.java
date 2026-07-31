package io.github.gohoski.numai.search;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.gohoski.numai.api.ApiClient;
import io.github.gohoski.numai.api.ApiError;
import io.github.gohoski.numai.api.ApiRequest;

/**
 * Created by Gleb on 30.07.2026.
 */
class BingSearchEngine implements SearchEngine {
    private ApiClient api;
    private String userAgent;

    BingSearchEngine() {
        api = new ApiClient(null);
        List<String> list = java.util.Arrays.asList("1.0", "1.1", "1.5", "1.6", "2.0", "2.1", "2.2", "2.3");
        userAgent = "Mozilla/5.0 (Linux; U; Android " + list.get(new java.util.Random().nextInt(list.size()))
                + "; en-us; generic) AppleWebKit/525.10+ (KHTML, like Gecko) Version/3.0.4 Mobile Safari/523.12.2";
    }

    @Override
    public List<SearchResult> search(String q) throws SearchException, ApiError, IOException {
        InputStream is = api.execute(new ApiRequest("http://www.bing.com", "/search", "GET").addParam("q", q).addParam("search","").addParam("form", "QBLH")
                .addHeader("User-Agent", userAgent)
                .addHeader("accept", "text/xml,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5")
                .addHeader("accept-language", "en-US")
                .addHeader("accept-charset", "utf-8, iso-8859-1, utf-16, *;q=0.7")).getBody();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"), 8192);
        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        String html = sb.toString();
        Log.i("Bing", "HTML downloaded");
        List<SearchResult> results = new ArrayList<SearchResult>();
        if (html.trim().length() == 0) {
            return results;
        }

        Node root = parseHtmlTree(html);
        List<Node> algoNodes = new ArrayList<Node>();
        findAlgoNodes(root, algoNodes);

        for (int i = 0; i < algoNodes.size(); i++) {
            Node algoNode = algoNodes.get(i);
            String title = extractTitle(algoNode);
            String url = extractUrl(algoNode);
            String snippet = extractSnippet(algoNode);

            results.add(new SearchResult(title, url, snippet));
        }

        return results;
    }

    public static List<SearchResult> parse(String html) {
        List<SearchResult> results = new ArrayList<SearchResult>();
        if (html == null || html.trim().length() == 0) {
            return results;
        }

        Node root = parseHtmlTree(html);
        List<Node> algoNodes = new ArrayList<Node>();
        findAlgoNodes(root, algoNodes);

        for (int i = 0; i < algoNodes.size(); i++) {
            Node algoNode = algoNodes.get(i);
            String title = extractTitle(algoNode);
            String url = extractUrl(algoNode);
            String snippet = extractSnippet(algoNode);

            results.add(new SearchResult(title, url, snippet));
        }

        return results;
    }

    private static void findAlgoNodes(Node node, List<Node> result) {
        if ("li".equals(node.tagName) && node.hasClass("b_algo")) {
            result.add(node);
            return;
        }
        for (int i = 0; i < node.children.size(); i++) {
            findAlgoNodes(node.children.get(i), result);
        }
    }

    private static String extractTitle(Node algoNode) {
        Node headerNode = findNodeByClass(algoNode, "b_algoheader");
        Node target = headerNode != null ? headerNode : algoNode;
        Node h2Node = findNodeByTag(target, "h2");

        if (h2Node != null) {
            StringBuilder sb = new StringBuilder();
            collectAllText(h2Node, sb);
            return cleanWhitespace(sb.toString());
        }
        return "";
    }

    private static String extractUrl(Node algoNode) {
        Node headerNode = findNodeByClass(algoNode, "b_algoheader");
        Node target = headerNode != null ? headerNode : algoNode;
        Node aNode = findNodeByTag(target, "a");

        if (aNode != null) {
            String href = aNode.getAttribute("href");
            if (href != null && href.length() > 0) {
                return href;
            }
        }
        return "";
    }

    private static String extractSnippet(Node algoNode) {
        StringBuilder sb = new StringBuilder();
        collectSnippetText(algoNode, sb);
        return cleanWhitespace(sb.toString());
    }

    private static void collectSnippetText(Node node, StringBuilder sb) {
        if (isExcludedForSnippet(node)) {
            return;
        }
        if ("#text".equals(node.tagName)) {
            if (node.text != null) {
                sb.append(node.text).append(" ");
            }
            return;
        }
        for (int i = 0; i < node.children.size(); i++) {
            collectSnippetText(node.children.get(i), sb);
        }
    }

    private static boolean isExcludedForSnippet(Node node) {
        if (!node.isElement()) return false;

        String style = node.getAttribute("style");
        if (style != null) {
            String lowerStyle = style.toLowerCase();
            if (lowerStyle.contains("display:none") || lowerStyle.contains("visibility:hidden")) {
                return true;
            }
        }
        if ("true".equalsIgnoreCase(node.getAttribute("aria-hidden"))) {
            return true;
        }

        String cls = node.getAttribute("class");
        if (cls != null) {
            if (cls.contains("b_tpcn") ||
                    cls.contains("b_algoheader") ||
                    cls.contains("wiki_attr") ||
                    cls.contains("ansinfo") ||
                    cls.contains("b_hide") ||
                    cls.contains("b_wiki_see_more") ||
                    cls.contains("b_wikigbg_cmore") ||
                    cls.contains("expansionAccessibilityText") ||
                    cls.contains("ChevronDown12") ||
                    cls.contains("ChevronUp12") ||
                    cls.contains("b_mopexpref") ||
                    cls.contains("b_demoteText") ||
                    cls.contains("b_tranthis") ||
                    cls.contains("sw_up") ||
                    cls.contains("sw_down") ||
                    cls.contains("exp_img")) {
                return true;
            }
        }
        return false;
    }

    private static void collectAllText(Node node, StringBuilder sb) {
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

    private static Node findNodeByClass(Node node, String className) {
        if (node.hasClass(className)) return node;
        for (int i = 0; i < node.children.size(); i++) {
            Node res = findNodeByClass(node.children.get(i), className);
            if (res != null) return res;
        }
        return null;
    }

    private static Node findNodeByTag(Node node, String tagName) {
        if (tagName.equalsIgnoreCase(node.tagName)) return node;
        for (int i = 0; i < node.children.size(); i++) {
            Node res = findNodeByTag(node.children.get(i), tagName);
            if (res != null) return res;
        }
        return null;
    }

    private static String cleanWhitespace(String str) {
        if (str == null || str.length() == 0) return "";
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

    private static class Node {
        String tagName;
        Map<String, String> attributes;
        List<Node> children = new ArrayList<Node>();
        Node parent;
        String text;

        boolean isElement() {
            return !"#text".equals(tagName) && !"#root".equals(tagName);
        }

        String getAttribute(String name) {
            if (attributes == null) return null;
            return attributes.get(name.toLowerCase());
        }

        boolean hasClass(String className) {
            String cls = getAttribute("class");
            return cls != null && cls.contains(className);
        }
    }

    // Case-insensitive string search without allocating a lower-cased copy of the entire HTML
    private static int indexOfIgnoreCase(String src, String target, int fromIndex) {
        if (src == null || target == null) return -1;
        int srcLen = src.length();
        int targetLen = target.length();
        if (fromIndex >= srcLen) return -1;
        if (fromIndex < 0) fromIndex = 0;
        if (targetLen == 0) return fromIndex;

        char firstLower = Character.toLowerCase(target.charAt(0));
        char firstUpper = Character.toUpperCase(target.charAt(0));

        for (int i = fromIndex; i <= srcLen - targetLen; i++) {
            char c = src.charAt(i);
            if (c == firstLower || c == firstUpper) {
                if (src.regionMatches(true, i, target, 0, targetLen)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static Node parseHtmlTree(String html) {
        Node root = new Node();
        root.tagName = "#root";
        Node current = root;

        int i = 0;
        int len = html.length();

        Set<String> selfClosing = new HashSet<String>();
        String[] sc = new String[]{"area", "base", "br", "col", "embed", "hr", "img", "input",
                "link", "meta", "param", "source", "track", "wbr"};
        for (int k = 0; k < sc.length; k++) {
            selfClosing.add(sc[k]);
        }

        while (i < len) {
            if (html.startsWith("<!--", i)) {
                int end = html.indexOf("-->", i + 4);
                if (end == -1) break;
                i = end + 3;
            } else if (html.startsWith("</", i)) {
                int end = html.indexOf('>', i + 2);
                if (end == -1) break;
                String closeTag = html.substring(i + 2, end).trim().toLowerCase();
                int spaceIdx = closeTag.indexOf(' ');
                if (spaceIdx != -1) closeTag = closeTag.substring(0, spaceIdx);

                Node p = current;
                while (p != null && p.parent != null && !p.tagName.equals(closeTag)) {
                    p = p.parent;
                }
                if (p != null && p.parent != null && p.tagName.equals(closeTag)) {
                    current = p.parent;
                }
                i = end + 1;
            } else if (html.charAt(i) == '<') {
                int end = html.indexOf('>', i);
                if (end == -1) break;

                String tagBuf = html.substring(i + 1, end).trim();
                if (tagBuf.length() == 0) {
                    i++;
                    continue;
                }

                int spaceIdx = -1;
                for (int k = 0; k < tagBuf.length(); k++) {
                    char c = tagBuf.charAt(k);
                    if (Character.isWhitespace(c) || c == '/') {
                        spaceIdx = k;
                        break;
                    }
                }
                String tagName = (spaceIdx == -1 ? tagBuf : tagBuf.substring(0, spaceIdx)).toLowerCase();

                if (tagName.startsWith("!") || tagName.startsWith("?")) {
                    i = end + 1;
                    continue;
                }

                if ("script".equals(tagName) || "style".equals(tagName)) {
                    String closeTag = "</" + tagName + ">";
                    int closeIdx = indexOfIgnoreCase(html, closeTag, end + 1);
                    if (closeIdx != -1) {
                        i = closeIdx + closeTag.length();
                    } else {
                        i = len;
                    }
                    continue;
                }

                Node node = new Node();
                node.tagName = tagName;
                node.parent = current;

                parseAttributes(tagBuf.substring(tagName.length()), node);

                boolean isSelfClosing = tagBuf.endsWith("/") || selfClosing.contains(tagName);

                current.children.add(node);
                if (!isSelfClosing) {
                    current = node;
                }
                i = end + 1;
            } else {
                int nextTag = html.indexOf('<', i);
                if (nextTag == -1) nextTag = len;
                String text = html.substring(i, nextTag);
                if (text.length() > 0) {
                    Node textNode = new Node();
                    textNode.tagName = "#text";
                    textNode.text = unescapeHtml(text);
                    textNode.parent = current;
                    current.children.add(textNode);
                }
                i = nextTag;
            }
        }
        return root;
    }

    private static void parseAttributes(String attrStr, Node node) {
        int i = 0;
        int len = attrStr.length();
        while (i < len) {
            while (i < len && Character.isWhitespace(attrStr.charAt(i))) i++;
            if (i >= len || attrStr.charAt(i) == '/') break;

            int startKey = i;
            while (i < len && attrStr.charAt(i) != '=' && !Character.isWhitespace(attrStr.charAt(i)) && attrStr.charAt(i) != '/') {
                i++;
            }
            String key = attrStr.substring(startKey, i).toLowerCase();
            while (i < len && Character.isWhitespace(attrStr.charAt(i))) i++;

            if (node.attributes == null) {
                node.attributes = new HashMap<String, String>(4);
            }

            if (i < len && attrStr.charAt(i) == '=') {
                i++;
                while (i < len && Character.isWhitespace(attrStr.charAt(i))) i++;
                if (i >= len) {
                    node.attributes.put(key, "");
                    break;
                }
                char quote = attrStr.charAt(i);
                if (quote == '"' || quote == '\'') {
                    i++;
                    int startVal = i;
                    while (i < len && attrStr.charAt(i) != quote) i++;
                    String val = attrStr.substring(startVal, i);
                    if (i < len) i++;
                    node.attributes.put(key, unescapeHtml(val));
                } else {
                    int startVal = i;
                    while (i < len && !Character.isWhitespace(attrStr.charAt(i)) && attrStr.charAt(i) != '>') i++;
                    String val = attrStr.substring(startVal, i);
                    node.attributes.put(key, unescapeHtml(val));
                }
            } else {
                node.attributes.put(key, "");
            }
        }
    }

    private static String unescapeHtml(String input) {
        if (input == null || input.indexOf('&') == -1) return input;
        StringBuilder sb = new StringBuilder(input.length());
        int i = 0;
        int len = input.length();
        while (i < len) {
            char c = input.charAt(i);
            if (c == '&') {
                int semi = input.indexOf(';', i);
                if (semi != -1 && semi - i < 10) {
                    String entity = input.substring(i + 1, semi);
                    if (entity.startsWith("#")) {
                        try {
                            int code;
                            if (entity.startsWith("#x") || entity.startsWith("#X")) {
                                code = Integer.parseInt(entity.substring(2), 16);
                            } else {
                                code = Integer.parseInt(entity.substring(1));
                            }
                            sb.append((char) code);
                            i = semi + 1;
                            continue;
                        } catch (NumberFormatException e) {
                            // ignore, fallback
                        }
                    } else {
                        if ("quot".equals(entity)) { sb.append('"'); i = semi + 1; continue; }
                        if ("amp".equals(entity))  { sb.append('&'); i = semi + 1; continue; }
                        if ("lt".equals(entity))   { sb.append('<'); i = semi + 1; continue; }
                        if ("gt".equals(entity))   { sb.append('>'); i = semi + 1; continue; }
                        if ("nbsp".equals(entity)) { sb.append(' '); i = semi + 1; continue; }
                        if ("apos".equals(entity)) { sb.append('\''); i = semi + 1; continue; }
                    }
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }
}