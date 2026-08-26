package kh.edu.istad.ite.devsoleapi.feature.problem.dto;

import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;

import java.util.UUID;

/**
 * A suggestion shown to somebody drafting a problem: something already on the
 * platform that looks like what they are about to ask.
 *
 * <p>Not a {@link ProblemResponse}. That record carries viewer state — your
 * vote, your bookmark, whether you may edit — which is both meaningless in a
 * dropdown and expensive to fill while the user is still typing.
 *
 * @param solved        whether the problem reached {@code RESOLVED}, which is
 *                      the badge the panel exists to show. Derived from
 *                      {@code status} so the frontend does not have to know
 *                      which statuses count as solved.
 * @param solutionCount published solutions, which is the weaker signal that a
 *                      problem may still be worth reading when nobody has
 *                      accepted an answer yet
 */
public record RelatedProblemResponse(
        UUID id,
        String title,
        ProblemStatus status,
        boolean solved,
        long solutionCount,
        long viewCount
) {
}
