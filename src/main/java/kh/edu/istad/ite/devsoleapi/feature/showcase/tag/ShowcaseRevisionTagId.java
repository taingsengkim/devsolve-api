package kh.edu.istad.ite.devsoleapi.feature.showcase.tag;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ShowcaseRevisionTagId implements Serializable {

    @Column(name = "revision_id")
    private UUID revisionId;

    @Column(name = "tag_id")
    private UUID tagId;
}
