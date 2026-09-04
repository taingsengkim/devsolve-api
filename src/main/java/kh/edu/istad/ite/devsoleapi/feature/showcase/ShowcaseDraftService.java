package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.SaveShowcaseDraftRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseDraftResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Showcases somebody has started and not posted. Private to their author: never
 * listed, never indexed, never seen by a moderator until submitted.
 */
public interface ShowcaseDraftService {

    ShowcaseDraftResponse create(SaveShowcaseDraftRequest request);

    Page<ShowcaseDraftResponse> findMine(Pageable pageable);

    ShowcaseDraftResponse findById(UUID id);

    /** The autosave target. A whole-document replace, so repeating it is safe. */
    ShowcaseDraftResponse save(UUID id, SaveShowcaseDraftRequest request);

    /**
     * Stores a cover image against the draft, so picking one survives closing
     * the tab the way the typing already does.
     *
     * <p>The object is stored once and never moved. Submitting hands the same
     * URL to the showcase, which then owns it — the key namespace is only a
     * namespace, and copying the bytes to a prettier key would buy nothing but
     * a window in which both or neither exists.
     */
    ShowcaseDraftResponse uploadCoverImage(UUID id, MultipartFile file);

    ShowcaseDraftResponse removeCoverImage(UUID id);

    void delete(UUID id);

    /**
     * Posts the draft as a showcase and discards it. A refused submission
     * leaves the draft exactly as it was.
     */
    ShowCasesResponse submit(UUID id);
}
