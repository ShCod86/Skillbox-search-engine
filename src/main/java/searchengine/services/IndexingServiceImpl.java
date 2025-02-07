package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.Page;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final SitesList sitesConfig;
    private volatile boolean isIndexingRunning = false;
    private final Map<SiteEntity, ForkJoinPool> activePools = new ConcurrentHashMap<>();

    @Override
    public synchronized IndexingResponse startIndexing() {
        if (isIndexingRunning) {
            return new IndexingResponse(false, "Индексация уже запущена");
        }
        isIndexingRunning = true;

        sitesConfig.getSites().forEach(siteConfig -> {
            String url = normalizeUrl(siteConfig.getUrl());
            String name = siteConfig.getName();

            prepareSiteForIndexing(url, name);
        });

        return new IndexingResponse(true, "");
    }

    @Override
    public synchronized IndexingResponse stopIndexing() {
        if (!isIndexingRunning) {
            return new IndexingResponse(false, "Индексация не запущена");
        }
        isIndexingRunning = false;

        activePools.forEach((site, pool) -> {
            pool.shutdownNow();
            updateSiteStatus(site, Status.FAILED, "Индексация остановлена пользователем");
        });
        activePools.clear();

        return new IndexingResponse(true, "");
    }

    @Transactional
    private void prepareSiteForIndexing(String url, String name) {
        SiteEntity existingSite = siteRepository.findByUrl(url);
        if (existingSite != null) {
            siteRepository.delete(existingSite);
        }

        SiteEntity newSite = createNewSite(url, name);
        ForkJoinPool pool = new ForkJoinPool();
        activePools.put(newSite, pool);

        pool.execute(() -> {
            try {
                pool.invoke(new PageParserTask(newSite, url, url));
                updateSiteStatus(newSite, Status.INDEXED, "");
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted()) {
                    updateSiteStatus(newSite, Status.FAILED, e.getMessage());
                }
            } finally {
                activePools.remove(newSite);
                checkAndFinishIndexing();
            }
        });
    }

    private SiteEntity createNewSite(String url, String name) {
        SiteEntity site = new SiteEntity();
        site.setUrl(url);
        site.setName(name);
        site.setStatus(Status.INDEXING);
        site.setLastError("");
        site.setStatusTime(Instant.now());
        return siteRepository.save(site);
    }

    private void updateSiteStatus(SiteEntity site, Status status, String error) {
        site.setStatus(status);
        site.setLastError(error);
        site.setStatusTime(Instant.now());
        siteRepository.save(site);
    }

    private synchronized void checkAndFinishIndexing() {
        if (activePools.isEmpty()) {
            isIndexingRunning = false;
        }
    }

    private String normalizeUrl(String url) {
        if (!url.endsWith("/")) {
            url += "/";
        }
        return url;
    }

    private class PageParserTask extends RecursiveAction {

        private final SiteEntity site;
        private final String baseUrl;
        private final String currentUrl;

        public PageParserTask(SiteEntity site, String baseUrl, String currentUrl) {
            this.site = site;
            this.baseUrl = baseUrl;
            this.currentUrl = currentUrl;
        }

        @Override
        protected void compute() {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            try {
                String relativePath = getRelativePath();
//                if (pageRepository.existsBySiteAndPath(site, relativePath)) {
                if (pageRepository.existsByPath(relativePath)) {
                    log.info("Page {} already exist", getRelativePath());
                    return;
                }

                delayBetweenRequests();

                Document doc = connectToPage();
                savePage(doc, relativePath);
                processLinks(doc);

            } catch (IOException e) {
                handlePageError(e);
            } catch (URISyntaxException e) {
                log.error("Invalid URL syntax: {}", currentUrl, e);
            }
        }

        private Document connectToPage() throws IOException {
            return Jsoup.connect(currentUrl).userAgent("HellionBot").referrer("http://www.google.com").ignoreHttpErrors(true).get();
        }

        private void delayBetweenRequests() {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Transactional
        public void savePage(Document doc, String relativePath) {
            try {
                Page page = new Page();
                page.setSite(site);
                page.setPath(relativePath);
                page.setCode(doc.connection().response().statusCode());
                page.setContent(doc.html());
                pageRepository.save(page);
                log.info("Page {} saved in repo", relativePath);

                updateSiteStatusTime();
            } catch (DataIntegrityViolationException e) {
                log.warn("Page already exists: {}", relativePath);
            }
        }

        public Page getPage(Document doc, String relativePath) {
            Page page = new Page();
            page.setSite(site);
            page.setPath(relativePath);
            page.setCode(doc.connection().response().statusCode());
            page.setContent(doc.html());
            return page;
//                updateSiteStatusTime();
        }

        private void updateSiteStatusTime() {
            site.setStatusTime(Instant.now());
            siteRepository.save(site);
        }

        private String getRelativePath() throws URISyntaxException {
            URI baseUri = new URI(baseUrl);
            URI currentUri = new URI(currentUrl);
            return baseUri.relativize(currentUri).getPath();
        }

        private void processLinks(Document doc) {
            List<PageParserTask> tasks = new ArrayList<>();
            doc.select("a[href]").forEach(link -> {
                String absUrl = link.absUrl("href");
                if (isValidLink(absUrl)) {
                    tasks.add(new PageParserTask(site, baseUrl, absUrl));
                }
            });
            invokeAll(tasks);
        }

        private boolean isValidLink(String url) {
            return url.startsWith(baseUrl) && !url.contains("#") && !url.contains("?");
        }

        private void handlePageError(IOException e) {
            if (!Thread.currentThread().isInterrupted()) {
                log.error("Error indexing page: {}", currentUrl, e);
//                updateSiteStatus(site, Status.FAILED, e.getMessage());
            }
        }
    }
}