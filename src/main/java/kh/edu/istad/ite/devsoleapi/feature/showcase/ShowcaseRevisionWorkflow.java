package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowCaseStepRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowcaseStep;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowcaseStepRevision;
import kh.edu.istad.ite.devsoleapi.feature.showcasestep.ShowcaseStepRevisionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShowcaseRevisionWorkflow {

    private final ShowcaseRevisionRepository showcaseRevisionRepository;
    private final ShowCaseStepRepository showcaseStepRepository;
    private final ShowcaseStepRevisionRepository
            showcaseStepRevisionRepository;

    @Transactional
    public ShowcaseRevision getOrCreate(
            ShowCases showcase,
            UUID submittedBy
    ) {
        return showcaseRevisionRepository
                .findByShowcase_Id(showcase.getId())
                .orElseGet(() -> createSnapshot(
                        showcase,
                        submittedBy
                ));
    }

    @Transactional
    public void submit(
            ShowcaseRevision revision,
            UUID submittedBy
    ) {
        revision.setReviewStatus(ReviewStatus.PENDING);
        revision.setSubmittedBy(submittedBy);
        revision.setReviewedBy(null);
        revision.setReviewedAt(null);
        revision.setRejectionReason(null);
        showcaseRevisionRepository.save(revision);
    }

    @Transactional(readOnly = true)
    public List<ShowcaseStepRevision> getCandidateSteps(
            UUID revisionId
    ) {
        return showcaseStepRevisionRepository
                .findByRevision_IdOrderByStepNumberAsc(revisionId);
    }

    @Transactional
    public void discard(ShowcaseRevision revision) {
        showcaseStepRevisionRepository
                .deleteByRevision_Id(revision.getId());
        showcaseRevisionRepository.delete(revision);
    }

    /**
     * Every image the published showcase points at: the cover plus each
     * step's screenshot and diagram.
     *
     * <p>Promoting or discarding a revision leaves one side's images
     * unreferenced. Callers collect both sides <em>before</em> mutating
     * either, then delete the difference; afterwards there is nothing left
     * to compare against.
     */
    @Transactional(readOnly = true)
    public List<String> imageUrlsOf(ShowCases showcase) {
        List<String> urls = new ArrayList<>();
        urls.add(showcase.getCoverImageUrl());
        showcaseStepRepository
                .findByShowcase_IdOrderByStepNumberAsc(showcase.getId())
                .forEach(step -> {
                    urls.add(step.getImageUrl());
                    urls.add(step.getDiagramUrl());
                });
        return urls.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> imageUrlsOf(ShowcaseRevision revision) {
        List<String> urls = new ArrayList<>();
        urls.add(revision.getCoverImageUrl());
        getCandidateSteps(revision.getId())
                .forEach(step -> {
                    urls.add(step.getImageUrl());
                    urls.add(step.getDiagramUrl());
                });
        return urls.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public void promoteSteps(
            ShowCases showcase,
            ShowcaseRevision revision
    ) {
        List<ShowcaseStep> publishedSteps = showcaseStepRepository
                .findByShowcase_IdOrderByStepNumberAsc(showcase.getId());
        List<ShowcaseStepRevision> candidateSteps =
                getCandidateSteps(revision.getId());

        Map<UUID, ShowcaseStep> publishedById = new HashMap<>();
        for (ShowcaseStep step : publishedSteps) {
            publishedById.put(step.getId(), step);
        }

        Set<UUID> retainedIds = new HashSet<>();
        for (ShowcaseStepRevision candidate : candidateSteps) {
            if (candidate.getSourceStepId() != null) {
                retainedIds.add(candidate.getSourceStepId());
            }
        }

        List<ShowcaseStep> removedSteps = publishedSteps.stream()
                .filter(step -> !retainedIds.contains(step.getId()))
                .toList();
        if (!removedSteps.isEmpty()) {
            showcaseStepRepository.deleteAll(removedSteps);
            showcaseStepRepository.flush();
        }

        List<ShowcaseStep> retainedSteps = publishedSteps.stream()
                .filter(step -> retainedIds.contains(step.getId()))
                .toList();
        moveRetainedStepsToTemporaryNumbers(
                retainedSteps,
                candidateSteps
        );

        List<ShowcaseStep> stepsToSave = new ArrayList<>();
        for (ShowcaseStepRevision candidate : candidateSteps) {
            ShowcaseStep target;

            if (candidate.getSourceStepId() == null) {
                target = new ShowcaseStep();
                target.setShowcase(showcase);
            } else {
                target = publishedById.get(candidate.getSourceStepId());
                if (target == null) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "A published showcase step referenced by "
                                    + "the revision no longer exists."
                    );
                }
            }

            copyCandidate(candidate, target);
            stepsToSave.add(target);
        }

        showcaseStepRepository.saveAllAndFlush(stepsToSave);
    }

    private ShowcaseRevision createSnapshot(
            ShowCases showcase,
            UUID submittedBy
    ) {
        ShowcaseRevision revision = new ShowcaseRevision();
        revision.setShowcase(showcase);
        revision.setCategory(showcase.getCategory());
        revision.setTitle(showcase.getTitle());
        revision.setOverview(showcase.getOverview());
        revision.setCoverImageUrl(showcase.getCoverImageUrl());
        revision.setLiveUrl(showcase.getLiveUrl());
        revision.setRepoUrl(showcase.getRepoUrl());
        revision.setVideoUrl(showcase.getVideoUrl());
        revision.setReviewStatus(ReviewStatus.PENDING);
        revision.setSubmittedBy(submittedBy);

        ShowcaseRevision saved =
                showcaseRevisionRepository.save(revision);

        List<ShowcaseStepRevision> stepSnapshots =
                showcaseStepRepository
                        .findByShowcase_IdOrderByStepNumberAsc(
                                showcase.getId()
                        )
                        .stream()
                        .map(step -> copyPublishedStep(saved, step))
                        .toList();

        showcaseStepRevisionRepository.saveAll(stepSnapshots);
        return saved;
    }

    private ShowcaseStepRevision copyPublishedStep(
            ShowcaseRevision revision,
            ShowcaseStep source
    ) {
        ShowcaseStepRevision target = new ShowcaseStepRevision();
        target.setRevision(revision);
        target.setSourceStepId(source.getId());
        target.setStepNumber(source.getStepNumber());
        target.setTitle(source.getTitle());
        target.setDescription(source.getDescription());
        target.setCodeSnippet(source.getCodeSnippet());
        target.setImageUrl(source.getImageUrl());
        target.setDiagramUrl(source.getDiagramUrl());
        return target;
    }

    private void copyCandidate(
            ShowcaseStepRevision source,
            ShowcaseStep target
    ) {
        target.setStepNumber(source.getStepNumber());
        target.setTitle(source.getTitle());
        target.setDescription(source.getDescription());
        target.setCodeSnippet(source.getCodeSnippet());
        target.setImageUrl(source.getImageUrl());
        target.setDiagramUrl(source.getDiagramUrl());
    }

    private void moveRetainedStepsToTemporaryNumbers(
            List<ShowcaseStep> retainedSteps,
            List<ShowcaseStepRevision> candidateSteps
    ) {
        if (retainedSteps.isEmpty()) {
            return;
        }

        int maximumCandidateStepNumber = candidateSteps.stream()
                .map(ShowcaseStepRevision::getStepNumber)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(0);
        int maximumPublishedStepNumber = retainedSteps.stream()
                .map(ShowcaseStep::getStepNumber)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(0);
        int maximumStepNumber = Math.max(
                maximumCandidateStepNumber,
                maximumPublishedStepNumber
        );

        if (maximumStepNumber
                > Integer.MAX_VALUE - retainedSteps.size()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Step numbers are too large to apply this revision."
            );
        }

        int temporaryStepNumber = maximumStepNumber + 1;
        for (ShowcaseStep retainedStep : retainedSteps) {
            retainedStep.setStepNumber(temporaryStepNumber++);
        }
        showcaseStepRepository.saveAllAndFlush(retainedSteps);
    }
}
