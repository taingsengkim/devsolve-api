package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;

import java.util.UUID;

/**
 * One published problem that might be what the author is about to write, as it
 * travels from the retrieval step to the model and on into the response.
 *
 * <p>Plain values, detached from Hibernate on purpose. The model call that
 * reads these takes seconds, and holding a transaction — and the connection
 * under it — open across a network call to a third party is how a pool runs
 * dry under load that a database would not have noticed.
 */
public record DuplicateCandidate(
        UUID id,
        String title,
        String description,
        String errorMessage,
        ProblemStatus status,
        long solutionCount,
        long viewCount
) {

    public boolean solved() {
        return status == ProblemStatus.RESOLVED;
    }
}
