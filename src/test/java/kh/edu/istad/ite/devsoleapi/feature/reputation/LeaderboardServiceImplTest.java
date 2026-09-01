package kh.edu.istad.ite.devsoleapi.feature.reputation;

import kh.edu.istad.ite.devsoleapi.feature.reports.ReportRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaderboardServiceImplTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ReportRepository reportRepository;

    private final LeaderboardMapper leaderboardMapper = new LeaderboardMapper();

    private LeaderboardServiceImpl leaderboardService() {
        return new LeaderboardServiceImpl(new LeaderboardCache(
                userProfileRepository,
                reportRepository,
                leaderboardMapper
        ));
    }

    private UserProfile profile(int reputation) {
        return profile(UUID.randomUUID(), reputation);
    }

    private UserProfile profile(UUID id, int reputation) {
        UserProfile profile = new UserProfile();
        profile.setId(id);
        profile.setUsername("r" + reputation);
        profile.setFullName("Researcher " + reputation);
        profile.setReputation(reputation);
        profile.setStatus(UserStatus.ACTIVE);
        return profile;
    }

    private ReportRepository.SeverityTally tally(
            UUID userId,
            Severity severity,
            long findings
    ) {
        return new ReportRepository.SeverityTally() {

            @Override
            public UUID getUserId() {
                return userId;
            }

            @Override
            public Severity getSeverity() {
                return severity;
            }

            @Override
            public long getFindings() {
                return findings;
            }
        };
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

        Page<LeaderboardResponse> leaderboard = leaderboardService()
                .getLeaderboard(LeaderboardPeriod.ALL_TIME, pageable);

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

        Page<LeaderboardResponse> leaderboard = leaderboardService()
                .getLeaderboard(LeaderboardPeriod.ALL_TIME, pageable);

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

        leaderboardService()
                .getLeaderboard(LeaderboardPeriod.ALL_TIME, pageable);

        verify(userProfileRepository)
                .findAllByStatusOrderByReputationDescIdAsc(
                        eq(UserStatus.ACTIVE),
                        eq(pageable)
                );
    }

    /**
     * The whole point of the period parameter: a window must not be answered
     * from the running reputation total, which has no history in it.
     */
    @Test
    void aWindowedBoardScoresTheWindowRatherThanTheLifetimeTotal() {

        UUID deep = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
        UUID prolific =
                UUID.fromString("00000000-0000-0000-0000-0000000000b2");

        // One critical this week outscores five lows, even though the lows
        // belong to the researcher with the bigger all-time total.
        when(reportRepository.tallyReputationAwardedSince(any()))
                .thenReturn(List.of(
                        tally(prolific, Severity.LOW, 5),
                        tally(deep, Severity.CRITICAL, 1)
                ));

        when(userProfileRepository.findAllById(any()))
                .thenReturn(List.of(
                        profile(deep, 100),
                        profile(prolific, 9000)
                ));

        Page<LeaderboardResponse> leaderboard = leaderboardService()
                .getLeaderboard(
                        LeaderboardPeriod.WEEK,
                        PageRequest.of(0, 10)
                );

        assertEquals(
                List.of(deep, prolific),
                leaderboard.getContent().stream()
                        .map(LeaderboardResponse::id)
                        .toList()
        );
        assertEquals(
                List.of(100, 25),
                leaderboard.getContent().stream()
                        .map(LeaderboardResponse::reputation)
                        .toList()
        );
        assertEquals(2, leaderboard.getTotalElements());
    }

    /**
     * A lifetime figure printed in a row that claims to be about this week is
     * worse than no figure at all.
     */
    @Test
    void aWindowedRowLeavesLifetimeOnlyCountsNull() {

        UUID researcher =
                UUID.fromString("00000000-0000-0000-0000-0000000000c3");

        when(reportRepository.tallyReputationAwardedSince(any()))
                .thenReturn(List.of(
                        tally(researcher, Severity.CRITICAL, 2),
                        tally(researcher, Severity.HIGH, 1)
                ));
        when(userProfileRepository.findAllById(any()))
                .thenReturn(List.of(profile(researcher, 5000)));

        LeaderboardResponse row = leaderboardService()
                .getLeaderboard(
                        LeaderboardPeriod.MONTH,
                        PageRequest.of(0, 10)
                )
                .getContent()
                .getFirst();

        assertNull(row.totalReports());
        assertNull(row.validReports());
        assertEquals(240, row.reputation());
        assertEquals(3, row.recognitionCount());
        assertEquals(2, row.criticalReports());
    }

    @Test
    void anEmptyWindowIsAnEmptyBoardRatherThanAFallbackToAllTime() {

        when(reportRepository.tallyReputationAwardedSince(any()))
                .thenReturn(List.of());

        Page<LeaderboardResponse> leaderboard = leaderboardService()
                .getLeaderboard(
                        LeaderboardPeriod.DAY,
                        PageRequest.of(0, 10)
                );

        assertTrue(leaderboard.getContent().isEmpty());
        assertEquals(0, leaderboard.getTotalElements());
    }

    @Test
    void everyWindowedPeriodHasACutOffAndAllTimeDoesNot() {

        LocalDateTime now = LocalDateTime.of(2026, 8, 31, 12, 0);

        assertEquals(now.minusDays(1), LeaderboardPeriod.DAY.since(now));
        assertEquals(now.minusWeeks(1), LeaderboardPeriod.WEEK.since(now));
        assertEquals(now.minusMonths(1), LeaderboardPeriod.MONTH.since(now));
        assertTrue(LeaderboardPeriod.DAY.isWindowed());
        assertFalse(LeaderboardPeriod.ALL_TIME.isWindowed());
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

