package searchengine.services.implementation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SiteFromConfig;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.exceptions.IndexingException;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.WordService;
import searchengine.services.interfaces.IndexingService;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingServiceImpl implements IndexingService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final WordService wordService;
    private final SitesList sitesList;

    private volatile boolean indexingInProgress = false;
    private final Map<Site, ForkJoinPool> activePools = new ConcurrentHashMap<>();
    private static final List<String> IGNORED_EXTENSIONS = Arrays.asList(".zip", ".pdf", ".jpg", ".png", ".docx", ".xlsx");
    private final Map<String, Lemma> lemmaCache = new ConcurrentHashMap<>();

    private static final int MAX_POOL_SIZE = Runtime.getRuntime().availableProcessors() / 2;
    private static final int REQUEST_DELAY_MS = 100;
    private static final int CONNECTION_TIMEOUT_MS = 5000;

    @Override
    @Transactional
    public IndexingResponse startIndexing() {
        log.info("Запрос на запуск индексации");
        IndexingResponse response = new IndexingResponse();
        if (indexingInProgress) {
            log.warn("Попытка запуска индексации, когда она уже выполняется");
            throw new IndexingException("Индексация уже запущена");
        }
        indexingInProgress = true;
        log.info("Очистка базы данных перед началом индексации");
        clearDatabase();
        log.info("Начало индексации сайтов: {}", sitesList.getSites().stream().map(SiteFromConfig::getUrl).toList());
        sitesList.getSites().forEach(this::indexSite);
        response.setResult(true);
        log.info("Индексация успешно запущена");
        return response;
    }

    @Override
    @Transactional
    public IndexingResponse stopIndexing() {
        log.info("Запрос на остановку индексации");
        IndexingResponse response = new IndexingResponse();
        if (!indexingInProgress) {
            log.warn("Попытка остановки индексации, когда она не выполняется");
            throw new IndexingException("Индексация не запущена");
        }
        indexingInProgress = false;
        log.info("Остановка {} активных пулов индексации", activePools.size());
        activePools.forEach((site, pool) -> {
            log.debug("Остановка пула для сайта: {}", site.getUrl());
            pool.shutdown();
            try {
                if (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                    log.warn("Пул не завершил работу в течение 1 секунды");
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("Ошибка при ожидании завершения пула для сайта {}", site.getUrl(), e);
                Thread.currentThread().interrupt();
            }
            if (siteRepository.findByUrl(site.getUrl()).getStatus() != Status.INDEXED) {
                log.info("Обновление статуса сайта {} на FAILED (индексация остановлена)", site.getUrl());
                updateSiteStatus(site, Status.FAILED, "Индексация остановлена пользователем");
            }
        });
        activePools.clear();
        response.setResult(true);
        log.info("Индексация успешно остановлена");
        return response;
    }

    @Override
    @Transactional
    public IndexingResponse indexSinglePage(String url) {
        log.info("Запрос на индексацию отдельной страницы: {}", url);
        IndexingResponse response = new IndexingResponse();
        try {
            URL urlFromString = new URL(url);
            String domain = urlFromString.getProtocol() + "://" + urlFromString.getHost();
            log.debug("Определен домен страницы: {}", domain);
            if (sitesList.getSites().stream().noneMatch(site -> site.getUrl().equals(domain))) {
                log.warn("Страница {} находится за пределами разрешенных сайтов", url);
                throw new IndexingException("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
            }
            Site siteEntity = siteRepository.findByUrl(domain);
            if (siteEntity == null) {
                log.info("Создание новой записи сайта для {}", domain);
                siteEntity = createNewSite(domain, sitesList.getSites().stream().filter(site -> site.getUrl().equals(domain)).findFirst().get().getName());
            }
            Page page = createPage(siteEntity, connectToPage(url));
            if (page != null) {
                log.debug("Очистка предыдущих данных для страницы {}", page.getPath());
                cleanLemmaAndIndex(page);
                pageRepository.save(page);
                log.debug("Индексация контента страницы {}", page.getPath());
                indexPageContent(page);
                log.info("Страница {} успешно проиндексирована", url);
            }
            response.setResult(true);
        } catch (MalformedURLException e) {
            log.error("Не корректный Url {}", url);
            throw new IndexingException("Не корректный Url " + url);
        } catch (IOException | InterruptedException e) {
            log.error("Ошибка при индексации страницы: {}", url, e);
            throw new IndexingException("Ошибка при индексации страницы: " + url);
        }
        return response;
    }

    private void clearDatabase() {
        indexRepository.deleteAll();
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        siteRepository.deleteAll();
        activePools.clear();
    }

    private synchronized void saveOrUpdateLemmasInBatch(Map<String, Integer> lemmas, Site siteEntity) {
        List<Lemma> lemmasToSave = new ArrayList<>();
        List<Lemma> lemmasToUpdate = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            String lemmaText = entry.getKey();
            Lemma lemma = lemmaCache.get(lemmaText);

            if (lemma == null) {
                lemma = lemmaRepository.findByLemmaAndSite(lemmaText, siteEntity).orElse(null);
                if (lemma != null) {
                    lemmaCache.put(lemmaText, lemma);
                }
            }

            if (lemma == null) {
                lemma = new Lemma();
                lemma.setLemma(lemmaText);
                lemma.setFrequency(1);
                lemma.setSite(siteEntity);
                lemmasToSave.add(lemma);
                lemmaCache.put(lemmaText, lemma);
            } else {
                lemma.setFrequency(lemma.getFrequency() + 1);
                lemmasToUpdate.add(lemma);
            }
        }

        if (!lemmasToSave.isEmpty()) {
            lemmaRepository.saveAll(lemmasToSave);
        }
        if (!lemmasToUpdate.isEmpty()) {
            lemmaRepository.saveAll(lemmasToUpdate);
        }
    }


    private void cleanLemmaAndIndex(Page page) {
        Page existingPage = pageRepository.findByPath(page.getPath());
        if (existingPage != null) {
            indexRepository.deleteByPage(existingPage);
            lemmaRepository.deleteAll(existingPage.getIndexes().stream().map(Index::getLemma).toList());
            pageRepository.delete(existingPage);
        }
    }

    private void indexSite(SiteFromConfig site) {
        String url = site.getUrl();
        Site siteEntity = siteRepository.findByUrl(url);
        if (siteEntity != null) {
            pageRepository.deleteAllBySite(siteEntity);
            updateSiteStatus(siteEntity, Status.INDEXING, null);
        } else {
            siteEntity = createNewSite(url, site.getName());
        }
        ForkJoinPool pool = new ForkJoinPool(MAX_POOL_SIZE);
        activePools.put(siteEntity, pool);

        Site finalSiteEntity = siteEntity;
        pool.execute(() -> indexSitePages(finalSiteEntity, url));
    }

    private void indexSitePages(Site siteEntity, String url) {
        try {
            ForkJoinPool.commonPool().invoke(new PageTask(siteEntity, url));
            updateSiteStatus(siteEntity, Status.INDEXED, null);
        } catch (Exception e) {
            if (!Thread.currentThread().isInterrupted()) {
                updateSiteStatus(siteEntity, Status.FAILED, e.toString());
                log.error("Ошибка при индексации сайта: {}", siteEntity.getUrl(), e);
            }
        } finally {
            activePools.remove(siteEntity);
            checkAndFinishIndexing();
        }
    }

    private void handleIndexingError(Site siteEntity, Exception e) {
        siteEntity.setStatus(Status.FAILED);
        siteEntity.setLastError("Ошибка при индексации: " + e.getMessage());
        siteEntity.setStatusTime(Instant.now());
        siteRepository.save(siteEntity);
        log.error("Ошибка при индексации сайта: {}", siteEntity.getUrl(), e);
    }

    private void updateSiteStatus(Site siteEntity, Status status, String error) {
        siteEntity.setStatus(status);
        siteEntity.setLastError(error);
        siteEntity.setStatusTime(Instant.now());
        siteRepository.save(siteEntity);
    }


    private synchronized void checkAndFinishIndexing() {
        if (activePools.isEmpty()) {
            indexingInProgress = false;
            log.info("Индексация завершена.");
        }
    }

    private Site createNewSite(String url, String name) {
        Site site = new Site();
        site.setUrl(url);
        site.setName(name);
        site.setStatus(Status.INDEXING);
        site.setStatusTime(Instant.now());
        return siteRepository.save(site);
    }

    private Page createPage(Site site, Document doc) {
        if (doc == null) {
            log.warn("Не удалось создать страницу, так как документ равен null");
            return null;
        }
        Page page = new Page();
        page.setSite(site);
        page.setPath(doc.baseUri().replaceAll("https?://[^/]+", ""));
        page.setCode(doc.connection().response().statusCode());
        page.setContent(doc.html());
        return page;
    }

    private boolean isIgnoredExtension(String url) {
        return IGNORED_EXTENSIONS.stream().anyMatch(url::endsWith);
    }

    private Document connectToPage(String url) throws IOException, InterruptedException {
        if (isIgnoredExtension(url)) {
            log.warn("Пропуск страницы с игнорируемым расширением: {}", url);
            return null;
        }
        Thread.sleep(REQUEST_DELAY_MS);
        return Jsoup.connect(url).timeout(CONNECTION_TIMEOUT_MS)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .referrer("http://www.google.com")
                .get();
    }

    private void indexPageContent(Page page) {
        log.debug("Начало индексации контента страницы: {}", page.getPath());
        String content = page.getContent();
        Map<String, Integer> lemmas = wordService.collectLemmas(content);
        log.debug("Найдено {} уникальных лемм на странице {}", lemmas.size(), page.getPath());
        List<Index> indexEntities = new ArrayList<>();

        // Пакетное сохранение лемм
        saveOrUpdateLemmasInBatch(lemmas, page.getSite());

        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            String lemmaText = entry.getKey();
            int count = entry.getValue();

            Lemma lemma = lemmaCache.get(lemmaText);
            if (lemma == null) {
                lemma = lemmaRepository.findByLemmaAndSite(lemmaText, page.getSite()).orElseThrow();
                lemmaCache.put(lemmaText, lemma);
            }

            Index index = new Index();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRank(count);
            indexEntities.add(index);
        }

        // Пакетное сохранение индексов
        indexRepository.saveAll(indexEntities);
        log.debug("Сохранено {} индексов для страницы {}", indexEntities.size(), page.getPath());
    }

    private class PageTask extends RecursiveAction {
        private final Site site;
        private final String url;
        private static final ConcurrentSkipListSet<String> visitedLinks = new ConcurrentSkipListSet<>();

        private PageTask(Site site, String url) {
            this.site = site;
            this.url = url;
        }

        @Override
        protected void compute() {
            if (!indexingInProgress || visitedLinks.contains(url)) {
                return;
            }
            visitedLinks.add(url);
            List<PageTask> tasks = new ArrayList<>();
            try {
                Document doc = connectToPage(url);
                Page page = createPage(site, doc);
                if (page != null && !pageRepository.existsByPath(page.getPath())) {
                    pageRepository.save(page);
                    site.setStatusTime(Instant.now());
                    indexPageContent(page);
                    Elements links = doc.select("a[href]");
                    links.forEach(link -> {
                        String absUrl = link.absUrl("href");
                        if (indexingInProgress && !visitedLinks.contains(absUrl) && isValidLink(absUrl) && absUrl.startsWith(site.getUrl())) {
                            tasks.add(new PageTask(site, absUrl));
                        }
                    });
                }
            } catch (IOException | InterruptedException e) {
                log.error("Ошибка при подключении к странице: {}", url);
                return;
            }
            invokeAll(tasks);
        }

        private boolean isValidLink(String url) {
            return !url.contains("#") && !url.contains("?");
        }
    }
}