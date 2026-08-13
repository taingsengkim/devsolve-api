package kh.edu.istad.ite.devsoleapi.feature.reputation;

import kh.edu.istad.ite.devsoleapi.feature.program.enums.Severity;
import kh.edu.istad.ite.devsoleapi.feature.reputation.dto.LeaderboardResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceImplTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    private final LeaderboardMapper leaderboardMapper = new LeaderboardMapper();

    private LeaderboardServiceImpl leaderboardService() {
        return new LeaderboardServiceImpl(
                userProfileRepository,
                leaderboardMapper
        );
    }

    private UserProfile profile(int reputation) {
        UserProfile profile = new UserProfile();
        profile.setId(UUID.randomUUID());
        profile.setFullName("Researcher " + reputation);
        profile.setReputation(reputation);
        return profile;
    }

    @Test
    void ranksAreNumberedFromTheTopOfTheFirstPage() {

        Pageable pageable = PageRequest.of(0, 3);
        when(userProfileRepository.findAllByStatusOrderByReputationDescIdAsc(
                eq(UserStatus.ACTIVE),
                any()
        )).thenReturn(new PageImpl<>(
                List.of(profile(300), profile(200), profile(100)),
                pageable,
                7
        ));

        Page<LeaderboardResponse> leaderboard =
                leaderboardService().getLeaderboard(pageable);

        assertEquals(
                List.of(1, 2, 3),
                leaderboard.getContent().stream()
                        .map(LeaderboardResponse::rank)
                        .toList()
        );
        assertEquals(7, leaderboard.getTotalElements());
    }

    @Test
    void ranksContinueAcrossPagesRatherThanRestartingAtOne() {

        Pageable pageable = PageRequest.of(2, 3);
        when(userProfileRepository.findAllByStatusOrderByReputationDescIdAsc(
                eq(UserStatus.ACTIVE),
                any()
        )).thenReturn(new PageImpl<>(
                List.of(profile(40), profile(30)),
                pageable,
                8
        ));

        Page<LeaderboardResponse> leaderboard =
                leaderboardService().getLeaderboard(pageable);

        assertEquals(
                List.of(7, 8),
                leaderboard.getContent().stream()
                        .map(LeaderboardResponse::rank)
                        .toList()
        );
    }

    @Test
    void suspendedAndRemovedAccountsDoNotRank() {

        Pageable pageable = PageRequest.of(0, 10);
        when(userProfileRepository.findAllByStatusOrderByReputationDescIdAsc(
                any(),
                any()
        )).thenReturn(new PageImpl<>(List.of(), pageable, 0));

        leaderboardService().getLeaderboard(pageable);

        verify(userProfileRepository)
                .findAllByStatusOrderByReputationDescIdAsc(
                        eq(UserStatus.ACTIVE),
                        eq(pageable)
                );
    }

    @Test
    void severityCurveRewardsDepthOverVolume() {

        // Twenty low findings must not outweigh one critical, or the
        // leaderboard rewards farming trivia.
        assertEquals(0, ReputationPolicy.pointsFor(Severity.NONE));
        assertEquals(5, ReputationPolicy.pointsFor(Severity.LOW));
        assertEquals(15, ReputationPolicy.pointsFor(Severity.MEDIUM));
        assertEquals(40, ReputationPolicy.pointsFor(Severity.HIGH));
        assertEquals(100, ReputationPolicy.pointsFor(Severity.CRITICAL));
        assertEquals(0, ReputationPolicy.pointsFor(null));
    }
}
