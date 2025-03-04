package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;

import java.util.List;

public interface PageRepository extends JpaRepository<PageEntity, Integer> {
    boolean existsByPath(String url);

    void deleteAllBySite(SiteEntity siteEntity);

    PageEntity findByPath(String path);

    List<PageEntity> findAllBySite(SiteEntity site);
}
