package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

import org.springframework.transaction.annotation.Transactional;
import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.comments.CommentService;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.CreateFlagRequest;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.ResolveFlagRequest;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramService;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;
@Service
@RequiredArgsConstructor

public class ContentFlagServiceImpl implements ContentFlagService{
    private final UserProfileRepository userProfileRepository;
    private final ContentFlagRepository contentFlagRepository;
    private final ContentFlagMapper contentFlagMapper;
    private final CommentService commentService;
    private final ProgramService programService;

    @Override
    @Transactional
    public FlagResponse createFlag(
            CreateFlagRequest request
    ) {

        UUID userId = extractCurrentUserId();

        UserProfile reporter = userProfileRepository
                .findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User profile has not been found"
                ));

        boolean alreadyReported =
                contentFlagRepository
                        .existsByReporter_IdAndFlaggableTypeAndFlaggableId(
                                userId,
                                request.flaggableType(),
                                request.flaggableId()
                        );

        if (alreadyReported) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You have already reported this content"
            );
        }

        ContentFlag flag =
                contentFlagMapper
                        .mapCreateFlagRequestToContentFlag(request);

        flag.setReporter(reporter);
        flag.setStatus(FlagStatus.PENDING);

        ContentFlag savedFlag =
                contentFlagRepository.save(flag);

        return contentFlagMapper
                .mapContentFlagToFlagResponse(savedFlag);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FlagResponse> getMyFlags(
            FlagStatus status,
            int pageNumber,
            int pageSize
    ) {
        UUID reporterId = extractCurrentUserId();
        validatePagination(pageNumber, pageSize);

        Pageable pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(
                        Sort.Direction.DESC,
                        "createdAt"
                )
        );

        return contentFlagRepository
                .findMyFlags(
                        reporterId,
                        status,
                        pageable
                )
                .map(contentFlagMapper
                        ::mapContentFlagToFlagResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FlagResponse> getAdminFlags(
            FlagStatus status,
            FlaggableType flaggableType,
            FlagReason reason,
            int pageNumber,
            int pageSize
    ) {
        requireAdmin("Only ADMIN can view flags");

        validatePagination(pageNumber, pageSize);

        Pageable pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(
                        Sort.Direction.DESC,
                        "createdAt"
                )
        );

        return contentFlagRepository
                .searchAdminFlags(
                        status,
                        flaggableType,
                        reason,
                        pageable
                )
                .map(contentFlagMapper::mapContentFlagToFlagResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public FlagResponse getAdminFlagById(UUID id) {
        requireAdmin("Only ADMIN can view flags");

        return contentFlagMapper
                .mapContentFlagToFlagResponse(
                        findFlag(id)
                );
    }

    @Override
    @Transactional
    public FlagResponse dismissFlag(
            UUID id
    ) {

        requireAdmin("Only ADMIN can dismiss flags");

        UUID adminId = extractCurrentUserId();

        ContentFlag flag = findPendingFlag(id);
        UserProfile admin = findAdmin(adminId);

        flag.setStatus(FlagStatus.DISMISSED);
        flag.setReviewedBy(admin);
        flag.setReviewedAt(LocalDateTime.now());

        ContentFlag savedFlag =
                contentFlagRepository.save(flag);

        return contentFlagMapper
                .mapContentFlagToFlagResponse(savedFlag);
    }

    @Override
    @Transactional
    public FlagResponse resolveFlag(
            UUID id,
            ResolveFlagRequest request
    ) {
        requireAdmin("Only ADMIN can resolve flags");

        UUID adminId = extractCurrentUserId();
        ContentFlag flag = findPendingFlag(id);
        UserProfile admin = findAdmin(adminId);

        flag.setStatus(FlagStatus.REVIEWED);
        flag.setReviewedBy(admin);
        flag.setReviewedAt(LocalDateTime.now());
        flag.setResolutionNote(
                request.resolutionNote().trim()
        );

        if (request.removeContent()) {
            removeFlaggedContent(flag, adminId);
        }

        ContentFlag savedFlag =
                contentFlagRepository.save(flag);

        return contentFlagMapper
                .mapContentFlagToFlagResponse(savedFlag);
    }

    /**
     * Acts on the content the flag points at.
     *
     * <p>Comments and programs are wired up: each has a single removal path an
     * admin owns, so resolving through here and removing through the feature's
     * own admin endpoint mean the same thing. The remaining flaggable types
     * have their own review workflows — a showcase goes back through the
     * review queue, a problem through its moderation status — and reaching
     * around those from here would leave two paths that disagree about what
     * "removed" means. Resolving a flag on one of those still records the
     * decision, which is what it did before.
     */
    private void removeFlaggedContent(ContentFlag flag, UUID adminId) {
        switch (flag.getFlaggableType()) {
            case COMMENT -> commentService.removeByModerator(
                    flag.getFlaggableId(),
                    adminId
            );
            case PROGRAM -> programService.removeProgramByAdmin(
                    flag.getFlaggableId()
            );
            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Content removal on resolve is only supported for "
                            + "comments and programs; use the "
                            + flag.getFlaggableType().name().toLowerCase()
                            + " moderation endpoints instead"
            );
        }
    }

    private ContentFlag findFlag(UUID id) {
        return contentFlagRepository
                .findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Flag not found"
                ));
    }

    private ContentFlag findPendingFlag(UUID id) {
        ContentFlag flag = findFlag(id);

        if (flag.getStatus() != FlagStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Flag has already been reviewed"
            );
        }
        return flag;
    }

    private UserProfile findAdmin(UUID adminId) {
        return userProfileRepository
                .findById(adminId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Admin profile not found"
                ));
    }

    private void requireAdmin(String message) {
        if (!AuthUtils.hasRole("ADMIN")) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    message
            );
        }
    }

    private UUID extractCurrentUserId() {
        try {
            return UUID.fromString(
                    AuthUtils.extractUserId()
            );
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user ID is not a valid UUID",
                    exception
            );
        }
    }

    private void validatePagination(
            int pageNumber,
            int pageSize
    ) {

        if (pageNumber < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page number must be greater than or equal to 0"
            );
        }

        if (pageSize < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must be greater than 0"
            );
        }

        if (pageSize > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must not exceed 100"
            );
        }
    }

}
