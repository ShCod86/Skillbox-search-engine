package searchengine.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@RequiredArgsConstructor
@Table(name = "lemma", indexes = {
        @Index(name = "idx_lemma_text", columnList = "lemma"),
        @Index(name = "idx_site", columnList = "site_id")
})
public class Lemma {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private int id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false, referencedColumnName = "id")
    private SiteEntity site;

    @Column(name = "lemma", nullable = false, columnDefinition = "VARCHAR(255)")
    private String lemma;

    @Column(name = "frequency", nullable = false)
    private volatile int frequency;

    @OneToMany(mappedBy = "lemma", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IndexEntity> indexes = new ArrayList<>();

}
