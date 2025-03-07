package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;

public interface PageRepository extends JpaRepository<Page, Integer> {
    boolean existsByPath(String url);

    void deleteAllBySite(Site siteEntity);

    Page findByPath(String path);

    List<Page> findAllBySite(Site site);
}
