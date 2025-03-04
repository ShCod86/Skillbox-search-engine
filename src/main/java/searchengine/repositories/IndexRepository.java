package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.IndexEntity;
import searchengine.model.Lemma;
import searchengine.model.PageEntity;

import java.util.List;

public interface IndexRepository extends JpaRepository<IndexEntity, Integer> {

    void deleteByPage(PageEntity existingPage);

    List<IndexEntity> findAllByLemma(Lemma lemma);

}
