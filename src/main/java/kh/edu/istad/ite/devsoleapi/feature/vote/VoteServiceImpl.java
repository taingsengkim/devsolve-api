package kh.edu.istad.ite.devsoleapi.feature.vote;

import kh.edu.istad.ite.devsoleapi.config.security.AuthUtils;
import kh.edu.istad.ite.devsoleapi.feature.vote.dto.VoteRequest;
import kh.edu.istad.ite.devsoleapi.feature.vote.dto.VoteResponse;
import kh.edu.istad.ite.devsoleapi.feature.vote.dto.VoteSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VoteServiceImpl implements VoteService {

    private final VoteRepository voteRepository;
    private final VoteTargetAccessService targetAccessService;

    @Override
    @Transactional
    public VoteResponse setVote(
            VoteType type,
            UUID targetId,
            VoteRequest request
    ) {
        UUID userId = requireCurrentUserId();
        VoteTarget target = targetAccessService.requireVotable(
                type,
                targetId
        );
        if (userId.equals(target.authorId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot vote on your own content"
            );
        }

        voteRepository.upsert(
                UUID.randomUUID(),
                userId,
                type.databaseValue(),
                targetId,
                request.value()
        );

        Vote vote = voteRepository
                .findByUserIdAndVotableTypeAndVotableId(
                        userId,
                        type,
                        targetId
                )
                .orElseThrow(() -> new IllegalStateException(
                        "Vote upsert completed without a stored vote"
                ));
        return toResponse(vote);
    }

    @Override
    @Transactional
    public void removeVote(VoteType type, UUID targetId) {
        voteRepository.deleteByUserIdAndVotableTypeAndVotableId(
                requireCurrentUserId(),
                type,
                targetId
        );
    }

    @Override
    @Transactional(readOnly = true)
    public VoteSummaryResponse getSummary(
            VoteType type,
            UUID targetId
    ) {
        targetAccessService.requireVotable(type, targetId);
        VoteSummaryProjection summary = voteRepository.summarize(
                type,
                targetId
        );
        Short currentUserVote = optionalCurrentUserId()
                .flatMap(userId -> voteRepository
                        .findByUserIdAndVotableTypeAndVotableId(
                                userId,
                                type,
                                targetId
                        ))
                .map(Vote::getVoteValue)
                .orElse(null);

        return new VoteSummaryResponse(
                type,
                targetId,
                summary.getScore(),
                summary.getUpvotes(),
                summary.getDownvotes(),
                currentUserVote
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<VoteResponse> getMine(
            VoteType type,
            int pageNumber,
            int pageSize
    ) {
        Pageable pageable = PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        return voteRepository.findMine(
                requireCurrentUserId(),
                type,
                pageable
        ).map(this::toResponse);
    }

    private VoteResponse toResponse(Vote vote) {
        return new VoteResponse(
                vote.getId(),
                vote.getVotableType(),
                vote.getVotableId(),
                vote.getVoteValue(),
                vote.getCreatedAt()
        );
    }

    private UUID requireCurrentUserId() {
        return optionalCurrentUserId().orElseThrow(() ->
                new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "A valid Keycloak access token is required"
                )
        );
    }

    private Optional<UUID> optionalCurrentUserId() {
        Authentication authentication = AuthUtils.getAuth();
        if (!(authentication
                instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(
                    jwtAuthentication.getToken().getSubject()
            ));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user ID is not a valid UUID",
                    exception
            );
        }
    }
}
