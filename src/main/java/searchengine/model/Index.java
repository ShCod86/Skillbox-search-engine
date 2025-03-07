package searchengine.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;

@Entity
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Table(name = "`index`")
public class Index {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private int id;

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false, referencedColumnName = "id")
    private Page page;

    @ManyToOne
    @JoinColumn(name = "lemma_id", nullable = false, referencedColumnName = "id")
    private Lemma lemma;

    @Column(name = "`rank`", nullable = false)
    private float rank;

}
