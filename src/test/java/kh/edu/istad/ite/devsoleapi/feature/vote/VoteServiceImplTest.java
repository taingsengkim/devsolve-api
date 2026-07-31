package kh.edu.istad.ite.devsoleapi.feature.vote;

import kh.edu.istad.ite.devsoleapi.feature.vote.dto.VoteRequest;
import kh.edu.istad.ite.devsoleapi.feature.vote.dto.VoteResponse;
import kh.edu.istad.ite.devsoleapi.feature.vote.dto.VoteSummaryResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteServiceImplTest {

    @Mock
    private VoteRepository voteRepository;

    @Mock
    private VoteTargetAccessService targetAccessService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void setVoteUsesAtomicUpsertAndReturnsStoredVote() {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Vote stored = vote(
                userId,
                VoteType.SHOWCASE,
                targetId,
                (short) 1
        );
        authenticate(userId);
        when(targetAccessService.requireVotable(
                VoteType.SHOWCASE,
                targetId
        )).thenReturn(new VoteTarget(UUID.randomUUID()));
        when(voteRepository
                .findByUserIdAndVotableTypeAndVotableId(
                        userId,
                        VoteType.SHOWCASE,
                        targetId
                ))
                .thenReturn(Optional.of(stored));

        VoteResponse response = service().setVote(
                VoteType.SHOWCASE,
                targetId,
                new VoteRequest((short) 1)
        );

        verify(voteRepository).upsert(
                any(UUID.class),
                eq(userId),
                eq("showcase"),
                eq(targetId),
                eq((short) 1)
        );
        assertEquals(stored.getId(), response.id());
        assertEquals(VoteType.SHOWCASE, response.type());
        assertEquals((short) 1, response.value());
    }

    @Test
    void selfVoteIsRejectedBeforePersistence() {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        authenticate(userId);
        when(targetAccessService.requireVotable(
                VoteType.PROBLEM,
                targetId
        )).thenReturn(new VoteTarget(userId));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().setVote(
                        VoteType.PROBLEM,
                        targetId,
                        new VoteRequest((short) 1)
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(voteRepository, never()).upsert(
                any(),
                any(),
                any(),
                any(),
                eq((short) 1)
        );
    }

    @Test
    void removeVoteIsIdempotent() {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        authenticate(userId);
        when(voteRepository
                .deleteByUserIdAndVotableTypeAndVotableId(
                        userId,
                        VoteType.COMMENT,
                        targetId
                ))
                .thenReturn(0L);

        service().removeVote(VoteType.COMMENT, targetId);

        verify(voteRepository)
                .deleteByUserIdAndVotableTypeAndVotableId(
                        userId,
                        VoteType.COMMENT,
                        targetId
                );
    }

    @Test
    void anonymousSummaryDoesNotExposeAUserVote() {
        UUID targetId = UUID.randomUUID();
        VoteSummaryProjection projection =
                mock(VoteSummaryProjection.class);
        when(projection.getScore()).thenReturn(7L);
        when(projection.getUpvotes()).thenReturn(9L);
        when(projection.getDownvotes()).thenReturn(2L);
        when(voteRepository.summarize(
                VoteType.SOLUTION,
                targetId
        )).thenReturn(projection);

        VoteSummaryResponse response = service().getSummary(
                VoteType.SOLUTION,
                targetId
        );

        verify(targetAccessService).requireVotable(
                VoteType.SOLUTION,
                targetId
        );
        assertEquals(7L, response.score());
        assertEquals(9L, response.upvotes());
        assertEquals(2L, response.downvotes());
        assertNull(response.currentUserVote());
    }

    @Test
    void authenticatedSummaryIncludesCurrentUserVote() {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Vote currentVote = vote(
                userId,
                VoteType.PROBLEM,
                targetId,
                (short) -1
        );
        VoteSummaryProjection projection =
                mock(VoteSummaryProjection.class);
        authenticate(userId);
        when(voteRepository.summarize(
                VoteType.PROBLEM,
                targetId
        )).thenReturn(projection);
        when(voteRepository
                .findByUserIdAndVotableTypeAndVotableId(
                        userId,
                        VoteType.PROBLEM,
                        targetId
                ))
                .thenReturn(Optional.of(currentVote));

        VoteSummaryResponse response = service().getSummary(
                VoteType.PROBLEM,
                targetId
        );

        assertEquals((short) -1, response.currentUserVote());
    }

    @Test
    void mineCanFilterByTypeAndUsesNewestFirstPagination() {
        UUID userId = UUID.randomUUID();
        Vote vote = vote(
                userId,
                VoteType.COMMENT,
                UUID.randomUUID(),
                (short) 1
        );
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);
        authenticate(userId);
        when(voteRepository.findMine(
                eq(userId),
                eq(VoteType.COMMENT),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(vote)));

        Page<VoteResponse> result = service().getMine(
                VoteType.COMMENT,
                1,
                10
        );

        verify(voteRepository).findMine(
                eq(userId),
                eq(VoteType.COMMENT),
                pageableCaptor.capture()
        );
        assertEquals(1, pageableCaptor.getValue().getPageNumber());
        assertEquals(10, pageableCaptor.getValue().getPageSize());
        assertTrue(pageableCaptor.getValue()
                .getSort()
                .getOrderFor("createdAt")
                .isDescending());
        assertEquals(1, result.getNumberOfElements());
    }

    private VoteServiceImpl service() {
        return new VoteServiceImpl(
                voteRepository,
                targetAccessService
        );
    }

    private Vote vote(
            UUID userId,
            VoteType type,
            UUID targetId,
            short value
    ) {
        return Vote.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .votableType(type)
                .votableId(targetId)
                .voteValue(value)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private void authenticate(UUID userId) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of())
        );
    }
}
