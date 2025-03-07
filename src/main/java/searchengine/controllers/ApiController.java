package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.interfaces.IndexingService;
import searchengine.services.interfaces.SearchService;
import searchengine.services.interfaces.StatisticsService;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;


    @GetMapping("/statistics")
    public StatisticsResponse statistics() {
        return statisticsService.getStatistics();
    }

    @GetMapping("/startIndexing")
    public IndexingResponse startIndexing() {
        return indexingService.startIndexing();
    }

    @GetMapping("/stopIndexing")
    public IndexingResponse stopIndexing() {
        return indexingService.stopIndexing();
    }

    @PostMapping("/indexPage")
    public IndexingResponse indexSinglePage(@RequestBody String url) {
        url = url.replace("url=", "");
        return indexingService.indexSinglePage(URLDecoder.decode(url, StandardCharsets.UTF_8));
    }

    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam(name = "query") String query,
            @RequestParam(name = "site", required = false, defaultValue = "") String siteUrl,
            @RequestParam(name = "offset", defaultValue = "0") int offset,
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return searchService.search(query, siteUrl, offset, limit);
    }


}
