package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Page;
import searchengine.model.SiteEntity;

public interface PageRepository extends JpaRepository<Page, Integer> {
    void deleteBySite(SiteEntity site);

    boolean existsBySiteAndPath(SiteEntity site, String relativePath);

    void deleteAllBySite(SiteEntity existingSite);

    boolean existsByPath(String relativePath);
}