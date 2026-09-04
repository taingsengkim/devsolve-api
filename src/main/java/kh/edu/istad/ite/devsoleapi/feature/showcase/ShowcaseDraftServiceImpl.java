package kh.edu.istad.ite.devsoleapi.feature.showcase;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.pagination.PageableValidator;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.CreateShowCasesRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.SaveShowcaseDraftRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseDraftResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShowcaseDraftServiceImpl implements ShowcaseDraftService {

    private static final String USER_ROLE = "USER";

    /**
     * A cap rather than a limit anyone should reach. Drafts are free to create
     * and never expire, so without one an autosave bug on the client turns into
     * unbounded rows.
     */
    private static final int MAX_DRAFTS = 20;

    private static final Set<String> DRAFT_SORT_PROPERTIES = Set.of(
            "id",
            "createdAt",
            "updatedAt",
            "title"
    );

    private final ShowcaseDraftRepository showcaseDraftRepository;
    private final UserProfileRepository userProfileRepository;
    private final ShowCasesService showCasesService;
    private final Validator validator;

    @Override
    @Transactional
    public ShowcaseDraftResponse create(SaveShowcaseDraftRequest request) {
        requireRole(USER_ROLE);
        UUID authorId = currentUserId();

        long existing = showcaseDraftRepository.countByAuthor_Id(authorId);
        if (existing >= MAX_DRAFTS) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You already have " + MAX_DRAFTS + " showcase drafts. "
                            + "Post or delete one before starting another."
            );
        }

        ShowcaseDraft draft = ShowcaseDraft.builder()
                .author(findUserProfile(authorId))
                .build();
        apply(draft, request);
        return toResponse(showcaseDraftRepository.save(draft));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ShowcaseDraftResponse> findMine(Pageable pageable) {
        requireRole(USER_ROLE);
        Pageable validated = PageableValidator.requireAllowedSort(
                pageable,
                DRAFT_SORT_PROPERTIES
        );
        return showcaseDraftRepository
                .findByAuthor_Id(currentUserId(), validated)
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ShowcaseDraftResponse findById(UUID id) {
        return toResponse(findOwnDraft(id));
    }

    @Override
    @Transactional
    public ShowcaseDraftResponse save(
            UUID id,
            SaveShowcaseDraftRequest request
    ) {
        ShowcaseDraft draft = findOwnDraft(id);
        apply(draft, request);
        return toResponse(showcaseDraftRepository.save(draft));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        showcaseDraftRepository.delete(findOwnDraft(id));
    }

    @Override
    @Transactional
    public ShowCasesResponse submit(UUID id) {
        ShowcaseDraft draft = findOwnDraft(id);
        CreateShowCasesRequest request = toCreateRequest(draft);

        // The constraints on CreateShowCasesRequest normally run at the
        // controller, via @Valid. Submitting reaches the service directly, so
        // they are run here instead of restated — a draft must not be able to
        // post a showcase that a direct post would have been refused.
        Set<ConstraintViolation<CreateShowCasesRequest>> violations =
                validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    violations.stream()
                            .map(ConstraintViolation::getMessage)
                            .sorted()
                            .collect(Collectors.joining("; "))
            );
        }

        // Everything past this point is the ordinary posting path, so the
        // category lookup, the duplicate-title check and the auto-approval
        // handoff all behave exactly as they always did. Sharing one
        // transaction means a refusal leaves the draft untouched.
        ShowCasesResponse response = showCasesService.create(request);
        showcaseDraftRepository.delete(draft);
        return response;
    }

    private void apply(ShowcaseDraft draft, SaveShowcaseDraftRequest request) {
        draft.setCategoryId(request.categoryId());
        draft.setTitle(request.title());
        draft.setOverview(request.overview());
        draft.setCoverImageUrl(request.coverImageUrl());
        draft.setLiveUrl(request.liveUrl());
        draft.setRepoUrl(request.repoUrl());
        draft.setVideoUrl(request.videoUrl());
        draft.setTagIds(request.tagIds());
        draft.setTags(request.tags());
    }

    private CreateShowCasesRequest toCreateRequest(ShowcaseDraft draft) {
        return new CreateShowCasesRequest(
                draft.getCategoryId(),
                draft.getTitle(),
                draft.getOverview(),
                draft.getCoverImageUrl(),
                draft.getLiveUrl(),
                draft.getRepoUrl(),
                draft.getVideoUrl(),
                toSet(draft.getTagIds()),
                toSet(draft.getTags())
        );
    }

    /**
     * The draft stores lists because jsonb has an order and a set does not;
     * the post takes sets. Ordered on the way back so two drafts saved with the
     * same tags in the same order produce the same request.
     */
    private <T> Set<T> toSet(List<T> values) {
        return values == null ? null : new LinkedHashSet<>(values);
    }

    private ShowcaseDraftResponse toResponse(ShowcaseDraft draft) {
        return new ShowcaseDraftResponse(
                draft.getId(),
                draft.getAuthor().getId(),
                draft.getCategoryId(),
                draft.getTitle(),
                draft.getOverview(),
                draft.getCoverImageUrl(),
                draft.getLiveUrl(),
                draft.getRepoUrl(),
                draft.getVideoUrl(),
                draft.getTagIds() == null
                        ? null
                        : List.copyOf(draft.getTagIds()),
                draft.getTags() == null
                        ? null
                        : List.copyOf(draft.getTags()),
                draft.getCreatedAt(),
                draft.getUpdatedAt()
        );
    }

    private ShowcaseDraft findOwnDraft(UUID id) {
        requireRole(USER_ROLE);
        return showcaseDraftRepository
                .findByIdAndAuthor_Id(id, currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Draft not found"
                ));
    }

    private UserProfile findUserProfile(UUID userId) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Authenticated user profile not found"
                ));
    }

    private UUID currentUserId() {
        try {
            return UUID.fromString(AuthUtils.extractUserId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated subject is not a user id"
            );
        }
    }

    private void requireRole(String role) {
        if (!AuthUtils.hasRole(role)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Required realm role: " + role
            );
        }
    }
}
