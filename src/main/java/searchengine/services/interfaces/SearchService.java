package searchengine.services.interfaces;

import searchengine.dto.search.SearchResponse;

public interface SearchService {
    SearchResponse search(String query, String siteUrl, int offset, int limit);
}
