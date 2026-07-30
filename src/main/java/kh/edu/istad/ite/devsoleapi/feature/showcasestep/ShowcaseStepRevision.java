package kh.edu.istad.ite.devsoleapi.feature.showcasestep;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kh.edu.istad.ite.devsoleapi.common.entity.BasedEntity;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowcaseRevision;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(
        name = "showcase_step_revisions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_showcase_step_revisions_order",
                        columnNames = {"revision_id", "step_number"}
                ),
                @UniqueConstraint(
                        name = "uq_showcase_step_revisions_source",
                        columnNames = {"revision_id", "source_step_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ShowcaseStepRevision extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    private ShowcaseRevision revision;

    @Column(name = "source_step_id")
    private UUID sourceStepId;

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
