package kh.edu.istad.ite.devsoleapi.feature.showcasestep;

import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCases;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "showcase_steps")
@Getter
@Setter
@NoArgsConstructor
public class ShowcaseStep {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "showcase_id", nullable = false)
    private ShowCases showcase;

    private Integer stepNumber;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String codeSnippet;

    private String imageUrl;

    private String diagramUrl;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
