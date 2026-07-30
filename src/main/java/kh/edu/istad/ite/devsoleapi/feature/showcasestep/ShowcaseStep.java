package kh.edu.istad.ite.devsoleapi.feature.showcasestep;

import kh.edu.istad.ite.devsoleapi.common.entity.BasedEntity;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCases;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
        name = "showcase_steps",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_showcase_steps_order",
                columnNames = {"showcase_id", "step_number"}
        )
)
@Getter
@Setter
@NoArgsConstructor
public class ShowcaseStep extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "showcase_id", nullable = false)
    private ShowCases showcase;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "code_snippet", columnDefinition = "TEXT")
    private String codeSnippet;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "diagram_url", length = 500)
    private String diagramUrl;
}
