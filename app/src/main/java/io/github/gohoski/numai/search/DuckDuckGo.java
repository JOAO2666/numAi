package io.github.gohoski.numai.search;

import java.io.IOException;
import java.util.List;

import io.github.gohoski.numai.api.ApiClient;
import io.github.gohoski.numai.api.ApiError;

/**
 * Created by Gleb on 07.08.2026.
 */

class DuckDuckGo implements SearchEngine {
    private ApiClient api;
    DuckDuckGo() {
        api = new ApiClient(null);
    }

    @Override
    public List<SearchResult> search(String query) throws SearchException, ApiError, IOException {
        return null;
    }
}
