package io.github.gohoski.numai.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by Gleb on 31.07.2026.
 * Lightweight, memory-efficient HTML parser.
 */
class HtmlParser {

    private static final Set<String> SELF_CLOSING = new HashSet<String>();
    static {
        String[] sc = new String[]{"area", "base", "br", "col", "embed", "hr", "img", "input",
                "link", "meta", "param", "source", "track", "wbr"};
        for (int k = 0; k < sc.length; k++) {
            SELF_CLOSING.add(sc[k]);
        }
    }

    static class Node {
        private static final List<Node> EMPTY_CHILDREN = new ArrayList<Node>(0);

        String tagName;
        Map<String, String> attributes;
        List<Node> children = EMPTY_CHILDREN;
        Node parent;
        public String text;

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

        void addChild(Node child) {
            if (children == EMPTY_CHILDREN) {
                children = new ArrayList<Node>(2);
            }
            children.add(child);
        }
    }

    static Node parseHtmlTree(String html) {
        Node root = new Node();
        root.tagName = "#root";
        Node current = root;

        int i = 0;
        int len = html.length();

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

                // Skip non-content tags early without building DOM nodes
                if ("script".equals(tagName) || "style".equals(tagName) || "noscript".equals(tagName) ||
                        "head".equals(tagName) || "svg".equals(tagName) || "iframe".equals(tagName)) {
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

                parseAttributes(tagBuf, tagName.length(), node);

                boolean isSelfClosing = tagBuf.endsWith("/") || SELF_CLOSING.contains(tagName);

                current.addChild(node);
                if (!isSelfClosing) {
                    current = node;
                }
                i = end + 1;
            } else {
                int nextTag = html.indexOf('<', i);
                if (nextTag == -1) nextTag = len;

                // Skip whitespace-only text nodes between tags
                boolean isBlank = true;
                for (int k = i; k < nextTag; k++) {
                    if (!Character.isWhitespace(html.charAt(k))) {
                        isBlank = false;
                        break;
                    }
                }

                if (!isBlank) {
                    String text = html.substring(i, nextTag);
                    Node textNode = new Node();
                    textNode.tagName = "#text";
                    textNode.text = unescapeHtml(text);
                    textNode.parent = current;
                    current.addChild(textNode);
                }
                i = nextTag;
            }
        }
        return root;
    }

    private static void parseAttributes(String attrStr, int startPos, Node node) {
        int i = startPos;
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
                        } catch (NumberFormatException ignored) {}
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
}