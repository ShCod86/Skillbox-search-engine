package searchengine.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@RequiredArgsConstructor
@Table(name = "site")
public class SiteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "status_time", nullable = false, columnDefinition = "DATETIME")
    private Instant statusTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "url", nullable = false, columnDefinition = "VARCHAR(255)", unique = true)
    private String url;

    @Column(name = "name", nullable = false, columnDefinition = "VARCHAR(255)")
    private String name;

    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL)
    private Set<PageEntity> pages = new HashSet<>();

    @OneToMany(mappedBy = "site", cascade = CascadeType.ALL)
    private Set<Lemma> lemmas = new HashSet<>();

}
