package kh.edu.istad.ite.devsoleapi.feature.problem.tag;

import kh.edu.istad.ite.devsoleapi.feature.problem.tag.dto.TagDeletionResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.tag.ShowcaseRevisionTagRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.tag.ShowcaseTagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagAdminServiceTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private ProblemTagRepository problemTagRepository;

    @Mock
    private ShowcaseTagRepository showcaseTagRepository;

    @Mock
    private ShowcaseRevisionTagRepository showcaseRevisionTagRepository;

    private TagAdminService service;
    private Tag tag;

    @BeforeEach
    void setUp() {
        service = new TagAdminService(
                tagRepository,
                problemTagRepository,
                showcaseTagRepository,
                showcaseRevisionTagRepository
        );
        tag = Tag.builder()
                .id(UUID.randomUUID())
                .name("Spam Tag")
                .slug("spam-tag")
                .usageCount(3)
                .build();
    }

    @Test
    void anUnusedTagDeletesWithoutForce() {
        stubTagFound();
        stubLinkCounts(0, 0, 0);

        TagDeletionResponse response = service.delete(tag.getId(), false);

        assertEquals("spam-tag", response.slug());
        assertEquals(0, response.unlinkedProblems());
        verify(tagRepository).delete(tag);
    }

    @Test
    void aTagStillInUseIsRefusedAndNothingIsUnlinked() {
        stubTagFound();
        stubLinkCounts(2, 1, 0);

        ResponseStatusException thrown = assertThrows(
                ResponseStatusException.class,
                () -> service.delete(tag.getId(), false)
        );

        assertEquals(HttpStatus.CONFLICT, thrown.getStatusCode());
        verify(tagRepository, never()).delete(tag);
        verify(problemTagRepository, never()).deleteAllByTagId(tag.getId());
        verify(showcaseTagRepository, never()).deleteAllByTagId(tag.getId());
    }

    /** The counts are the whole point of the 409 — they drive the retry. */
    @Test
    void theRefusalNamesWhatIsStillUsingTheTag() {
        stubTagFound();
        stubLinkCounts(2, 1, 4);

        ResponseStatusException thrown = assertThrows(
                ResponseStatusException.class,
                () -> service.delete(tag.getId(), false)
        );

        String reason = thrown.getReason();
        assertTrue(reason.contains("spam-tag"), reason);
        assertTrue(reason.contains("2 problem(s)"), reason);
        assertTrue(reason.contains("1 showcase(s)"), reason);
        assertTrue(reason.contains("4 pending revision(s)"), reason);
    }

    @Test
    void forceUnlinksEverywhereBeforeDeleting() {
        stubTagFound();
        stubLinkCounts(2, 1, 4);

        TagDeletionResponse response = service.delete(tag.getId(), true);

        assertEquals(2, response.unlinkedProblems());
        assertEquals(1, response.unlinkedShowcases());
        assertEquals(4, response.unlinkedRevisions());
        verify(problemTagRepository).deleteAllByTagId(tag.getId());
        verify(showcaseTagRepository).deleteAllByTagId(tag.getId());
        verify(showcaseRevisionTagRepository).deleteAllByTagId(tag.getId());
        verify(tagRepository).delete(tag);
    }

    /**
     * Revisions count too: a pending revision holds a foreign key to the tag,
     * so skipping them would fail the delete on a tag that looks unused.
     */
    @Test
    void aTagOnlyOnAPendingRevisionStillCountsAsInUse() {
        stubTagFound();
        stubLinkCounts(0, 0, 1);

        assertEquals(
                HttpStatus.CONFLICT,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.delete(tag.getId(), false)
                ).getStatusCode()
        );
    }

    @Test
    void anUnknownTagIs404NotASilentSuccess() {
        UUID missing = UUID.randomUUID();
        when(tagRepository.findById(missing)).thenReturn(Optional.empty());

        assertEquals(
                HttpStatus.NOT_FOUND,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.delete(missing, true)
                ).getStatusCode()
        );
    }

    private void stubTagFound() {
        when(tagRepository.findById(tag.getId())).thenReturn(Optional.of(tag));
    }

    private void stubLinkCounts(
            long problems,
            long showcases,
            long revisions
    ) {
        when(problemTagRepository.countByTagId(tag.getId()))
                .thenReturn(problems);
        when(showcaseTagRepository.countByTagId(tag.getId()))
                .thenReturn(showcases);
        when(showcaseRevisionTagRepository.countByTagId(tag.getId()))
                .thenReturn(revisions);
    }
}
