package searchengine.services.implementation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import searchengine.config.SiteFromConfig;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.interfaces.StatisticsService;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StatisticsServiceImpl implements StatisticsService {

    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    @Override
    public StatisticsResponse getStatistics() {
        log.info("Запрос статистики");
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);
        log.debug("Всего сайтов в конфигурации: {}", sites.getSites().size());

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<SiteFromConfig> sitesList = sites.getSites();

        for (SiteFromConfig siteFromConfig : sitesList) {
            log.debug("Обработка статистики для сайта: {}", siteFromConfig.getUrl());
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(siteFromConfig.getName());
            item.setUrl(siteFromConfig.getUrl());

            Site site = siteRepository.findByUrl(siteFromConfig.getUrl());
            if (site != null) {
                int pages = pageRepository.findAllBySite(site).size();
                int lemmas = lemmaRepository.findAllBySite(site).size();
                log.debug("Найдено для сайта {}: {} страниц, {} лемм", site.getUrl(), pages, lemmas);
                item.setStatus(site.getStatus().toString());
                item.setError(site.getLastError());
                item.setStatusTime(site.getStatusTime().toEpochMilli());
                item.setPages(pages);
                item.setLemmas(lemmas);
            } else {
                log.debug("Сайт {} не найден в базе данных", siteFromConfig.getUrl());
                item.setStatus("NOT_INDEXED");
                item.setError("");
                item.setStatusTime(System.currentTimeMillis());
                item.setPages(0);
                item.setLemmas(0);
            }
            detailed.add(item);
        }
        total.setPages(pageRepository.findAll().size());
        total.setLemmas(lemmaRepository.findAll().size());
        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        log.info("Статистика успешно собрана");
        return response;
    }
}
