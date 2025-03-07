package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.List;

public interface IndexRepository extends JpaRepository<Index, Integer> {

    void deleteByPage(Page existingPage);

    List<Index> findAllByLemma(Lemma lemma);

}
