package searchengine.services.implementation;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.search.SearchResult;
import searchengine.exceptions.SearchException;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.WordService;
import searchengine.services.interfaces.SearchService;

import java.util.*;

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
            throw new SearchException("Задан пустой поисковый запрос");
        }

        Set<String> uniqueLemmas = wordService.getLemmaSet(query);
        Site site = getSiteEntity(siteUrl);

        List<Lemma> filteredLemmas = getFilteredLemmas(site);
        List<String> validLemmas = filterValidLemmas(uniqueLemmas, filteredLemmas);

        if (validLemmas.isEmpty()) {
            return createEmptyResponse(response);
        }

        Map<Page, Double> pageRelevanceMap = calculatePageRelevance(validLemmas, site);
        List<SearchResult> searchResults = createSearchResults(pageRelevanceMap, query);

        return paginateResults(response, searchResults, offset, limit);
    }

    private boolean isQueryInvalid(String query) {
        return query == null || query.isBlank();
    }

    private Site getSiteEntity(String siteUrl) {
        return (siteUrl != null) ? siteRepository.findByUrl(siteUrl) : null;
    }

    private List<Lemma> getFilteredLemmas(Site site) {
        return (site != null) ? lemmaRepository.findAllBySite(site) : lemmaRepository.findAll();
    }


    private List<String> filterValidLemmas(Set<String> uniqueLemmas, List<Lemma> filteredLemmas) {
        Map<String, Integer> lemmaFrequencyMap = createLemmaFrequencyMap(filteredLemmas);
        return uniqueLemmas.stream()
                .filter(lemma -> lemmaFrequencyMap.containsKey(lemma) && lemmaFrequencyMap.getOrDefault(lemma, 0) <= (filteredLemmas.size() * 0.2))
                .sorted(Comparator.comparingInt(lemmaFrequencyMap::get))
                .toList();
    }

    private Map<String, Integer> createLemmaFrequencyMap(List<Lemma> filteredLemmas) {
        Map<String, Integer> frequencyMap = new HashMap<>();
        for (Lemma lemma : filteredLemmas) {
            if (lemma.getLemma() != null) {
                frequencyMap.put(lemma.getLemma(), lemma.getFrequency());
            }
        }
        return frequencyMap;
    }

    private SearchResponse createEmptyResponse(SearchResponse response) {
        response.setCount(0);
        response.setData(Collections.emptyList());
        response.setResult(true);
        return response;
    }


    private Map<Page, Double> calculatePageRelevance(List<String> validLemmas, Site site) {
        Map<Page, Double> pageRelevanceMap = new HashMap<>();
        List<Site> sitesToSearch = (site == null) ? siteRepository.findAll() : Collections.singletonList(site);

        for (Site siteToSearch : sitesToSearch) {
            for (String lemma : validLemmas) {
                lemmaRepository.findByLemmaAndSite(lemma, siteToSearch).ifPresent(lemmaEntity -> {
                    List<Index> indexEntities = indexRepository.findAllByLemma(lemmaEntity);
                    for (Index index : indexEntities) {
                        Page page = index.getPage();
                        if (site == null || page.getSite().equals(site)) {
                            pageRelevanceMap.merge(page, (double) index.getRank(), Double::sum);
                        }
                    }
                });
            }
        }
        return pageRelevanceMap;
    }

    private List<SearchResult> createSearchResults(Map<Page, Double> pageRelevanceMap, String query) {
        double maxRelevance = pageRelevanceMap.values().stream().max(Double::compare).orElse(0.0);
        List<SearchResult> searchResults = new ArrayList<>();

        for (Map.Entry<Page, Double> entry : pageRelevanceMap.entrySet()) {
            Page page = entry.getKey();
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
                break;
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
                .toList();

        response.setData(paginatedResults);
        response.setResult(true);

        return response;
    }
}
