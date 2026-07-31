package io.github.gohoski.numai.search;

/**
 * Created by Gleb on 30.07.2026.
 */

public class SearchResult {
    private String title;
    private String url;
    private String snippet;

    public SearchResult(String title, String url, String snippet) {
        this.title = title != null ? title : "";
        this.url = url != null ? url : "";
        this.snippet = snippet != null ? snippet : "";
    }

    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getSnippet() { return snippet; }

    @Override
    public String toString() {
        return "SearchResult{" +
                "title='" + title + '\'' +
                ", url='" + url + '\'' +
                ", snippet='" + snippet + '\'' +
                '}';
    }
}