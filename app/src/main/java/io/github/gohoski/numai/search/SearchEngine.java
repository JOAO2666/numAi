package io.github.gohoski.numai.search;

/**
 * Created by Gleb on 30.07.2026.
 */

import java.io.IOException;
import java.util.List;

import io.github.gohoski.numai.api.ApiError;

public interface SearchEngine {
    List<SearchResult> search(String query) throws SearchException, ApiError, IOException;
}