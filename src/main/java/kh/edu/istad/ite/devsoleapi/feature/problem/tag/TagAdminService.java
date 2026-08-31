package kh.edu.istad.ite.devsoleapi.feature.problem.tag;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.dto.TagDeletionResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.tag.ShowcaseRevisionTagRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.tag.ShowcaseTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Removing a tag from the shared catalogue.
 *
 * <p>Deletion unlinks before it deletes, so it scrubs the tag off published
 * problems and showcases rather than failing on a foreign key. Because that
 * edits content an admin never looked at, an in-use tag is refused unless the
 * caller passes {@code force} — the count in the 409 is what they need to
 * decide.
 */
@Service
@RequiredArgsConstructor
public class TagAdminService {

    private final TagRepository tagRepository;
    private final ProblemTagRepository problemTagRepository;
    private final ShowcaseTagRepository showcaseTagRepository;
    private final ShowcaseRevisionTagRepository showcaseRevisionTagRepository;

    /**
     * Every cache holding a rendered tag. Missing one here leaves the deleted
     * tag visible on a feed for the length of its TTL.
     */
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.PROBLEM_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.PROBLEM_LISTING, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.SHOWCASE_DETAIL, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.SHOWCASE_LISTING, allEntries = true),
            @CacheEvict(
                    cacheNames = CacheNames.SHOWCASE_LISTING_RANKED,
                    allEntries = true
            )
    })
    @Transactional
    public TagDeletionResponse delete(UUID tagId, boolean force) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Tag not found"
                ));

        long problems = problemTagRepository.countByTagId(tagId);
        long showcases = showcaseTagRepository.countByTagId(tagId);
        long revisions = showcaseRevisionTagRepository.countByTagId(tagId);
        long links = problems + showcases + revisions;

        if (links > 0 && !force) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tag '" + tag.getSlug() + "' is still on " + problems
                            + " problem(s), " + showcases + " showcase(s) and "
                            + revisions + " pending revision(s). Retry with"
                            + " force=true to unlink and delete it."
            );
        }

        problemTagRepository.deleteAllByTagId(tagId);
        showcaseTagRepository.deleteAllByTagId(tagId);
        showcaseRevisionTagRepository.deleteAllByTagId(tagId);
        tagRepository.delete(tag);

        return new TagDeletionResponse(
                tagId,
                tag.getName(),
                tag.getSlug(),
                problems,
                showcases,
                revisions
        );
    }
}
