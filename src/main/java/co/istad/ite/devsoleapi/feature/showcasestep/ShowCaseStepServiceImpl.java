package co.istad.ite.devsoleapi.feature.showcasestep;

import co.istad.ite.devsoleapi.config.security.AuthUtils;
import co.istad.ite.devsoleapi.feature.showcase.ShowCases;
import co.istad.ite.devsoleapi.feature.showcase.ShowCasesRepository;
import co.istad.ite.devsoleapi.feature.showcasestep.dto.CreateShowcaseStepRequest;
import co.istad.ite.devsoleapi.feature.showcasestep.dto.ShowcaseStepResponse;
import co.istad.ite.devsoleapi.feature.showcasestep.dto.UpdateShowcaseStepRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class ShowCaseStepServiceImpl implements ShowCaseStepService {
    private final ShowCaseStepRepository showcaseStepRepository;
    private final ShowCasesRepository showCasesRepository;
    private final ShowcaseStepMapper showcaseStepMapper;

    @Override
    public ShowcaseStepResponse create(UUID showcaseId, CreateShowcaseStepRequest request) {
        String userId = AuthUtils.extractUserId();

        ShowCases showcase = showCasesRepository
                .findByIdAndDeletedAtIsNull(showcaseId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Showcase not found."
                        ));

        if (!showcase.getAuthor().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only add steps to your own showcase."
            );
        }

        ShowcaseStep step = showcaseStepMapper.mapCreateShowcaseStepRequestToShowcaseStep(request);

        step.setShowcase(showcase);

        ShowcaseStep saved = showcaseStepRepository.save(step);

        return showcaseStepMapper.mapShowcaseStepToShowcaseStepResponse(saved);
    }

    @Override
    public List<ShowcaseStepResponse> getAll(UUID showcaseId) {
        ShowCases showcase = showCasesRepository
                .findByIdAndDeletedAtIsNull(showcaseId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Showcase not found."
                        ));

        return showcaseStepRepository
                .findByShowcase_IdOrderByStepNumberAsc(showcase.getId())
                .stream()
                .map(showcaseStepMapper::mapShowcaseStepToShowcaseStepResponse)
                .toList();
    }

    @Override
    public ShowcaseStepResponse getById(UUID showcaseId, UUID stepId) {
        ShowcaseStep step = showcaseStepRepository
                .findByIdAndShowcase_Id(stepId, showcaseId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Step not found."
                        ));

        return showcaseStepMapper.mapShowcaseStepToShowcaseStepResponse(step);
    }

    @Override
    public ShowcaseStepResponse update(UUID showcaseId, UUID stepId, UpdateShowcaseStepRequest request) {
        String userId = AuthUtils.extractUserId();

        ShowcaseStep step = showcaseStepRepository
                .findByIdAndShowcase_Id(stepId, showcaseId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Step not found."
                        ));

        if (!step.getShowcase().getAuthor().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only edit your own showcase step."
            );
        }

        if (request.stepNumber() != null) {
            step.setStepNumber(request.stepNumber());
        }

        if (request.title() != null) {
            step.setTitle(request.title());
        }

        if (request.description() != null) {
            step.setDescription(request.description());
        }

        if (request.codeSnippet() != null) {
            step.setCodeSnippet(request.codeSnippet());
        }

        if (request.imageUrl() != null) {
            step.setImageUrl(request.imageUrl());
        }

        if (request.diagramUrl() != null) {
            step.setDiagramUrl(request.diagramUrl());
        }

        ShowcaseStep updated = showcaseStepRepository.save(step);

        return showcaseStepMapper.mapShowcaseStepToShowcaseStepResponse(updated);

    }

    @Override
    public void delete(UUID showcaseId, UUID stepId) {
        String userId = AuthUtils.extractUserId();

        ShowcaseStep step = showcaseStepRepository
                .findByIdAndShowcase_Id(stepId, showcaseId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Step not found."
                        ));

        if (!step.getShowcase().getAuthor().getId().equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You can only delete your own showcase step."
            );
        }

        showcaseStepRepository.delete(step);
    }
}


