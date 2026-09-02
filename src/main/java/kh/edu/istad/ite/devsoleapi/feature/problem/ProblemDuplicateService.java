package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.feature.problem.dto.DuplicateCheckRequest;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.DuplicateCheckResponse;

/**
 * "Has somebody already asked this?", answered properly.
 *
 * <p>Separate from {@link ProblemService#findRelated} rather than replacing it,
 * because the two are answering at different prices. {@code findRelated} runs
 * between keystrokes, costs one indexed query and is allowed to be roughly
 * right. This one costs a call to a model, runs when the author asks for it or
 * is about to submit, and is expected to be right — including the answer that
 * nothing matches, which the keyword version cannot give with any confidence.
 */
public interface ProblemDuplicateService {

    DuplicateCheckResponse check(DuplicateCheckRequest request);
}
