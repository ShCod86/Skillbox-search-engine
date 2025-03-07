package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.search.SearchResult;
import searchengine.model.IndexEntity;
import searchengine.model.Lemma;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.SiteRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SiteRepository siteRepository;
    private final WordService wordService;

    @Override
    @Transactional
    public SearchResponse search(String query, String siteUrl, int offset, int limit) {
        SearchResponse response = new SearchResponse();

        if (isQueryInvalid(query)) {
            return createErrorResponse(response, "Задан пустой поисковый запрос");
        }

        Set<String> uniqueLemmas = wordService.getLemmaSet(query);
        SiteEntity site = getSiteEntity(siteUrl);

        List<Lemma> filteredLemmas = getFilteredLemmas(site);
        List<String> validLemmas = filterValidLemmas(uniqueLemmas, filteredLemmas);

        if (validLemmas.isEmpty()) {
            return createEmptyResponse(response);
        }

        Map<PageEntity, Double> pageRelevanceMap = calculatePageRelevance(validLemmas, site);
        List<SearchResult> searchResults = createSearchResults(pageRelevanceMap, query);

        return paginateResults(response, searchResults, offset, limit);
    }

    private boolean isQueryInvalid(String query) {
        return query == null || query.isBlank();
    }

    private SiteEntity getSiteEntity(String siteUrl) {
        return (siteUrl != null) ? siteRepository.findByUrl(siteUrl) : null;
    }

    private List<Lemma> getFilteredLemmas(SiteEntity site) {
        return (site != null) ? lemmaRepository.findAllBySite(site) : lemmaRepository.findAll();
    }
    //TODO обработать NullPointerException
    private List<String> filterValidLemmas(Set<String> uniqueLemmas, List<Lemma> filteredLemmas) {
        Map<String, Integer> lemmaFrequencyMap = createLemmaFrequencyMap(filteredLemmas);
        return uniqueLemmas.stream()
                .filter(lemma -> lemmaFrequencyMap.getOrDefault(lemma, 0) <= (filteredLemmas.size() * 0.2))
                .sorted(Comparator.comparingInt(lemmaFrequencyMap::get))
                .toList();
    }

    private Map<String, Integer> createLemmaFrequencyMap(List<Lemma> filteredLemmas) {
        Map<String, Integer> frequencyMap = new HashMap<>();
        for (Lemma lemma : filteredLemmas) {
            frequencyMap.put(lemma.getLemma(), lemma.getFrequency());
        }
        return frequencyMap;
    }

    private SearchResponse createEmptyResponse(SearchResponse response) {
        response.setCount(0);
        response.setData(Collections.emptyList());
        response.setResult(true);
        return response;
    }

    private Map<PageEntity, Double> calculatePageRelevance(List<String> validLemmas, SiteEntity site) {
        Map<PageEntity, Double> pageRelevanceMap = new HashMap<>();

        List<SiteEntity> sitesToSearch = (site == null) ? siteRepository.findAll() : Collections.singletonList(site);

        for (SiteEntity siteToSearch : sitesToSearch) {
            for (String lemma : validLemmas) {
                Optional<Lemma> lemmaEntity = lemmaRepository.findByLemmaAndSite(lemma, siteToSearch);
                if (lemmaEntity.isPresent()) {
                    List<IndexEntity> indexEntities = indexRepository.findAllByLemma(lemmaEntity.get());
                    for (IndexEntity index : indexEntities) {
                        PageEntity page = index.getPage();
                        if (site == null || page.getSite().equals(site)) {
                            double currentRank = pageRelevanceMap.getOrDefault(page, 0.0);
                            pageRelevanceMap.put(page, currentRank + index.getRank());
                        }
                    }
                }
            }
        }


        return pageRelevanceMap;
    }

    private List<SearchResult> createSearchResults(Map<PageEntity, Double> pageRelevanceMap, String query) {
        double maxRelevance = pageRelevanceMap.values().stream().max(Double::compare).orElse(0.0);
        List<SearchResult> searchResults = new ArrayList<>();

        for (Map.Entry<PageEntity, Double> entry : pageRelevanceMap.entrySet()) {
            PageEntity page = entry.getKey();
            double absRelevance = entry.getValue();
            double relRelevance = maxRelevance > 0 ? absRelevance / maxRelevance : 0;

            String snippet = createSnippet(page.getContent(), query);
            SearchResult result = new SearchResult();
            result.setSite(page.getSite().getUrl());
            result.setSiteName(page.getSite().getName());
            result.setUri(page.getPath());
            result.setTitle(Jsoup.parse(page.getContent()).title());
            result.setSnippet(snippet);
            result.setRelevance(relRelevance);
            searchResults.add(result);
        }

        return searchResults;
    }

    private String createSnippet(String content, String query) {
        String[] words = query.split("\\s+");
        StringBuilder snippet = new StringBuilder();
        String text = Jsoup.parse(content).text();
        int snippetLength = 200; // Длина сниппета

        for (String word : words) {
            if (text.contains(word)) {
                int wordIndex = text.indexOf(word);

                // Найти начало предложения
                int sentenceStart = 0;
                for (int i = wordIndex; i >= 0; i--) {
                    if (text.charAt(i) == '.' || text.charAt(i) == '!' || text.charAt(i) == '?') {
                        sentenceStart = i + 1;
                        break;
                    }
                }

                int start = Math.max(Math.max(wordIndex - 60, sentenceStart), 0);
                int end = Math.min(start + snippetLength, text.length());

                // Выделить слово жирным
                String highlighted = text.substring(start, end).replace(word, "<b>" + word + "</b>");
                snippet.append(highlighted).append("...");
                break; // Сниппет только для одного совпадения
            }
        }
        return snippet.toString();
    }

    private SearchResponse paginateResults(SearchResponse response, List<SearchResult> searchResults, int offset, int limit) {
        int totalResults = searchResults.size();
        response.setCount(totalResults);

        List<SearchResult> paginatedResults = searchResults.stream()
                .skip(offset)
                .limit(limit)
                .collect(Collectors.toList());

        response.setData(paginatedResults);
        response.setResult(true);

        return response;
    }

    private SearchResponse createErrorResponse(SearchResponse response, String errorMessage) {
        response.setCount(0);
        response.setData(Collections.emptyList());
        response.setResult(false);
        response.setError(errorMessage);
        return response;
    }
}
