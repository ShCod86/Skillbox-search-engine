package searchengine.model;

import io.hypersistence.utils.hibernate.type.basic.PostgreSQLEnumType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Getter
@Setter
@RequiredArgsConstructor
@Table(name = "site")
@TypeDef(name = "status_enum", typeClass = PostgreSQLEnumType.class)
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Type(type = "status_enum")
    private Status status;

    @Column(name = "status_time", nullable = false, columnDefinition = "DATETIME")
    private Instant statusTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "url", nullable = false, columnDefinition = "VARCHAR(255)", unique = true)
    private String url;

    @Column(name = "name", nullable = false, columnDefinition = "VARCHAR(255)")
    private String name;

    @OneToMany(mappedBy = "site")
    private Set<Page> pages = new HashSet<>();

    @OneToMany(mappedBy = "site")
    private Set<Lemma> lemmas = new HashSet<>();

}
