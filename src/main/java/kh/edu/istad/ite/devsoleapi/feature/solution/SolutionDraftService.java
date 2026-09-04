package kh.edu.istad.ite.devsoleapi.feature.solution;

import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SaveSolutionDraftRequest;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionDraftResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Answers somebody has started and not posted. Private to their author: never
 * counted on a problem, never shown to the asker, never in review.
 */
public interface SolutionDraftService {

    SolutionDraftResponse create(UUID problemId, SaveSolutionDraftRequest request);

    /**
     * @param problemId narrows to drafts answering one problem. Omitted,
     *                  returns every draft the caller owns.
     */
    Page<SolutionDraftResponse> findMine(UUID problemId, Pageable pageable);

    SolutionDraftResponse findById(UUID id);

    /** The autosave target. A whole-document replace, so repeating it is safe. */
    SolutionDraftResponse save(UUID id, SaveSolutionDraftRequest request);

    void delete(UUID id);

    /**
     * Posts the draft as a solution and discards it. A refused submission
     * leaves the draft exactly as it was.
     */
    SolutionResponse submit(UUID id);
}
