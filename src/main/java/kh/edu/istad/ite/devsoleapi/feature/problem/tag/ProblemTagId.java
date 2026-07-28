package kh.edu.istad.ite.devsoleapi.feature.problem.tag;

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
public class ProblemTagId implements Serializable {

    @Column(name = "problem_id")
    private UUID problemId;

    @Column(name = "tag_id")
    private UUID tagId;
}
