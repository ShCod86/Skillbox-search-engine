package searchengine.services.implementation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class SearchServiceImpl implements SearchService {
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SiteRepository siteRepository;
    private final WordService wordService;

    @Override
    @Transactional
    public SearchResponse search(String query, String siteUrl, int offset, int limit) {
        log.info("Поисковый запрос: '{}', сайт: {}, offset: {}, limit: {}", query, siteUrl, offset, limit);
        SearchResponse response = new SearchResponse();

        if (isQueryInvalid(query)) {
            log.warn("Пустой поисковый запрос");
            throw new SearchException("Задан пустой поисковый запрос");
        }

        log.debug("Получение лемм из запроса");
        Set<String> uniqueLemmas = wordService.getLemmaSet(query);
        log.info("Найдено {} уникальных лемм в запросе: {}", uniqueLemmas.size(), uniqueLemmas);
        Site site = getSiteEntity(siteUrl);

        List<Lemma> filteredLemmas = getFilteredLemmas(site);
        log.info("Получено {} лемм из базы данных", filteredLemmas.size());
        List<String> validLemmas = filterValidLemmas(uniqueLemmas, filteredLemmas);
        log.info("После фильтрации осталось {} лемм: {}", validLemmas.size(), validLemmas);

        if (validLemmas.isEmpty()) {
            log.info("Нет подходящих лемм для поиска");
            return createEmptyResponse(response);
        }

        log.debug("Расчет релевантности страниц");
        Map<Page, Double> pageRelevanceMap = calculatePageRelevance(validLemmas, site);
        log.info("Найдено {} релевантных страниц", pageRelevanceMap.size());
        List<SearchResult> searchResults = createSearchResults(pageRelevanceMap, query);
        log.info("Поиск завершен, найдено {} результатов", searchResults.size());

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
        return uniqueLemmas.stream().filter(lemma -> lemmaFrequencyMap.containsKey(lemma)
                && lemmaFrequencyMap.getOrDefault(lemma, 0) <= (filteredLemmas.size() * 0.2)).sorted(Comparator.comparingInt(lemmaFrequencyMap::get)).toList();
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
        log.debug("Поиск будет выполнен по {} сайтам", sitesToSearch.size());

        for (Site siteToSearch : sitesToSearch) {
            for (String lemma : validLemmas) {
                lemmaRepository.findByLemmaAndSite(lemma, siteToSearch).ifPresent(lemmaEntity -> {
                    List<Index> indexEntities = indexRepository.findAllByLemma(lemmaEntity);
                    log.info("Для леммы '{}' найдено {} индексов", lemma, indexEntities.size());
                    for (Index index : indexEntities) {
                        Page page = index.getPage();
                        if ((site == null || page.getSite().equals(site))
                                && isLemmaVisibleInPage(page, lemma)
                        ) {
                            pageRelevanceMap.merge(page, (double) index.getRank(), Double::sum);
                        }
                    }
                });
            }
        }
        return pageRelevanceMap;
    }

    private boolean isLemmaVisibleInPage(Page page, String lemma) {
        try {
            String text = Jsoup.parse(page.getContent()).text().toLowerCase();
            boolean isVisible = text.contains(lemma.toLowerCase());

            if (!isVisible) {
                log.info("Лемма '{}' не найдена в тексте страницы {}", lemma, page.getPath());
            }
            return isVisible;
        } catch (Exception e) {
            log.error("Ошибка проверки видимости леммы: {}", e.getMessage());
            return false;
        }
    }

    private boolean containsLemma(String text, String lemma) {
        return text.toLowerCase().contains(lemma.toLowerCase());
    }

    private String createSnippet(String content, String query) {
        String text = Jsoup.parse(content).text();
        String title = Jsoup.parse(content).title();
        StringBuilder snippet = new StringBuilder();
        int snippetLength = 200;

        if (containsLemma(title, query)) {
            snippet.append("<b>Заголовок:</b> ").append(highlightWords(title, query));
        }

        String[] words = query.split("\\s+");
        for (String word : words) {
            if (text.toLowerCase().contains(word.toLowerCase())) {
                int wordIndex = text.toLowerCase().indexOf(word.toLowerCase());
                int start = Math.max(wordIndex - 60, 0);
                int end = Math.min(start + snippetLength, text.length());

                if (!snippet.isEmpty()) {
                    snippet.append("<br><br>");
                }
                log.info("Слово выделеное {}", highlightWords(text.substring(start, end), query));
                snippet.append(highlightWords(text.substring(start, end), query));
                break;
            }
        }

        return !snippet.isEmpty() ? snippet + "..." : "";
    }

    private String highlightWords(String text, String query) {
        String lowerText = text.toLowerCase();
        String lowerQuery = query.toLowerCase();

        for (String word : lowerQuery.trim().split("\\s+")) {
            word = word.trim();
            if (!word.isEmpty()) {
                int index = lowerText.indexOf(word);
                while (index != -1) {
                    String originalWord = text.substring(index, index + word.length());
                    text = text.substring(0, index) + "<b>" + originalWord + "</b>" + text.substring(index + word.length());
                    lowerText = text.toLowerCase();
                    index = lowerText.indexOf(word, index + 7); // +7 из-за добавления <b></b>
                }
            }
        }
        return text;
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

    private SearchResponse paginateResults(SearchResponse response, List<SearchResult> searchResults, int offset, int limit) {
        int totalResults = searchResults.size();
        response.setCount(totalResults);

        List<SearchResult> paginatedResults = searchResults.stream().skip(offset).limit(limit).toList();

        response.setData(paginatedResults);
        response.setResult(true);

        return response;
    }
}
