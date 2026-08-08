package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowCaseStepRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowcaseStep;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowcaseStepRevision;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowcaseStepRevisionRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.tag.ShowcaseTagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShowcaseRevisionWorkflowTest {

    @Mock
    private ShowcaseRevisionRepository showcaseRevisionRepository;

    @Mock
    private ShowCaseStepRepository showcaseStepRepository;

    @Mock
    private ShowcaseStepRevisionRepository
            showcaseStepRevisionRepository;

    @Mock
    private ShowcaseTagService showcaseTagService;

    private ShowcaseRevisionWorkflow workflow;

    @BeforeEach
    void setUp() {
        workflow = new ShowcaseRevisionWorkflow(
                showcaseRevisionRepository,
                showcaseStepRepository,
                showcaseStepRevisionRepository,
                showcaseTagService
        );
    }

    @Test
    void getOrCreateCopiesPublishedMetadataAndSteps() {
        UUID showcaseId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        UUID stepId = UUID.randomUUID();

        ShowCases showcase = new ShowCases();
        showcase.setId(showcaseId);
        showcase.setTitle("Published showcase");
        showcase.setOverview("Published overview");

        ShowcaseStep publishedStep = new ShowcaseStep();
        publishedStep.setId(stepId);
        publishedStep.setShowcase(showcase);
        publishedStep.setStepNumber(1);
        publishedStep.setTitle("Published step");
        publishedStep.setDescription("Published instructions");

        when(showcaseRevisionRepository.findByShowcase_Id(showcaseId))
                .thenReturn(Optional.empty());
        when(showcaseRevisionRepository.save(
                any(ShowcaseRevision.class)
        )).thenAnswer(invocation -> {
            ShowcaseRevision revision = invocation.getArgument(0);
            revision.setId(revisionId);
            return revision;
        });
        when(showcaseStepRepository
                .findByShowcase_IdOrderByStepNumberAsc(showcaseId))
                .thenReturn(List.of(publishedStep));

        ShowcaseRevision revision = workflow.getOrCreate(
                showcase,
                ownerId
        );

        assertEquals(revisionId, revision.getId());
        assertEquals("Published showcase", revision.getTitle());
        assertEquals(ownerId, revision.getSubmittedBy());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ShowcaseStepRevision>> stepsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(showcaseStepRevisionRepository)
                .saveAll(stepsCaptor.capture());

        ShowcaseStepRevision snapshot =
                stepsCaptor.getValue().getFirst();
        assertSame(revision, snapshot.getRevision());
        assertEquals(stepId, snapshot.getSourceStepId());
        assertEquals(1, snapshot.getStepNumber());
        assertEquals("Published step", snapshot.getTitle());
    }

    @Test
    void promoteStepsUpdatesAddsAndDeletesPublishedSteps() {
        UUID showcaseId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        UUID retainedStepId = UUID.randomUUID();

        ShowCases showcase = new ShowCases();
        showcase.setId(showcaseId);

        ShowcaseRevision revision = new ShowcaseRevision();
        revision.setId(revisionId);
        revision.setShowcase(showcase);

        ShowcaseStep retained = new ShowcaseStep();
        retained.setId(retainedStepId);
        retained.setShowcase(showcase);
        retained.setStepNumber(1);
        retained.setTitle("Old title");

        ShowcaseStep removed = new ShowcaseStep();
        removed.setId(UUID.randomUUID());
        removed.setShowcase(showcase);
        removed.setStepNumber(2);
        removed.setTitle("Remove me");

        ShowcaseStepRevision updatedCandidate =
                new ShowcaseStepRevision();
        updatedCandidate.setRevision(revision);
        updatedCandidate.setSourceStepId(retainedStepId);
        updatedCandidate.setStepNumber(2);
        updatedCandidate.setTitle("Updated title");

        ShowcaseStepRevision addedCandidate =
                new ShowcaseStepRevision();
        addedCandidate.setRevision(revision);
        addedCandidate.setStepNumber(1);
        addedCandidate.setTitle("New step");

        when(showcaseStepRepository
                .findByShowcase_IdOrderByStepNumberAsc(showcaseId))
                .thenReturn(List.of(retained, removed));
        when(showcaseStepRevisionRepository
                .findByRevision_IdOrderByStepNumberAsc(revisionId))
                .thenReturn(List.of(
                        addedCandidate,
                        updatedCandidate
                ));

        workflow.promoteSteps(showcase, revision);

        verify(showcaseStepRepository)
                .deleteAll(List.of(removed));
        verify(showcaseStepRepository).flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ShowcaseStep>> savedStepsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(showcaseStepRepository, times(2))
                .saveAllAndFlush(savedStepsCaptor.capture());

        List<ShowcaseStep> promotedSteps =
                savedStepsCaptor.getAllValues().getLast();
        assertEquals(2, promotedSteps.size());
        assertEquals("Updated title", retained.getTitle());
        assertEquals(2, retained.getStepNumber());

        ShowcaseStep added = promotedSteps.stream()
                .filter(step -> step.getId() == null)
                .findFirst()
                .orElseThrow();
        assertSame(showcase, added.getShowcase());
        assertEquals(1, added.getStepNumber());
        assertEquals("New step", added.getTitle());
    }
}
