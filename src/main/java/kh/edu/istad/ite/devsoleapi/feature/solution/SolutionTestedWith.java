package kh.edu.istad.ite.devsoleapi.feature.solution;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SolutionTestedWith {

    @Column(name = "technology", nullable = false, length = 100)
    private String technology;

    @Column(name = "version", length = 50)
    private String version;
}
