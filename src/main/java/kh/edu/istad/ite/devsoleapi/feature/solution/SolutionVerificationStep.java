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
public class SolutionVerificationStep {

    @Column(name = "instruction", nullable = false, length = 1_000)
    private String instruction;

    @Column(name = "expected_result", nullable = false, length = 1_000)
    private String expectedResult;
}
