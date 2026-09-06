package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

import kh.edu.istad.ite.devsoleapi.feature.moderation.action.ModerationTargetType;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlagQueueRepository.FlagRow;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlagQueueRepository.FlagTally;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagQueueSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.ResolveFlagRequest;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.TargetFlagActionResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.takedown.ContentTakedownService;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentFlagServiceImplTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ContentFlagRepository contentFlagRepository;

    @Mock
    private FlagQueueRepository flagQueueRepository;

    @Mock
    private ContentFlagMapper contentFlagMapper;

    @Mock
    private ContentTakedownService contentTakedownService;

    private ContentFlagServiceImpl service;

    @BeforeEach
    void setUp() {
        // The assembler and the URL builder are the real ones: they hold the
        // translation from five status vocabularies into one, which is the part
        // of a response a moderator actually reads.
        service = new ContentFlagServiceImpl(
                userProfileRepository,
                contentFlagRepository,
                flagQueueRepository,
                contentFlagMapper,
                new FlagRowAssembler(new FlagTargetUrls(
                        "/community/problems",
                        "/community/showcases",
                        "/programs",
                        "/reports"
                )),
                contentTakedownService
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMyFlagsReturnsOnlyAuthenticatedUsersFlags() {
        UUID reporterId = UUID.randomUUID();
        StubRow row = new StubRow();
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        authenticate(reporterId, false);
        when(flagQueueRepository.search(
                eq("PENDING"),
                isNull(),
                isNull(),
                eq(reporterId.toString()),
                isNull(),
                eq("NEWEST"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(row)));

        Page<FlagResponse> result = service.getMyFlags(
                FlagStatus.PENDING,
                1,
                10
        );

        assertEquals(1, result.getContent().size());
        verify(flagQueueRepository).search(
                eq("PENDING"),
                isNull(),
                isNull(),
                eq(reporterId.toString()),
                isNull(),
                eq("NEWEST"),
                pageableCaptor.capture()
        );
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(1, pageable.getPageNumber());
        assertEquals(10, pageable.getPageSize());
        // Ordering is a parameter of the statement, not something Spring
        // appends: two of the three orderings are over lateral columns.
        assertTrue(pageable.getSort().isUnsorted());
    }

    /**
     * How many other people reported the same post is moderation-internal. Told
     * to the reporter it becomes a number to watch, and a reason to organise
     * more of them.
     */
    @Test
    void myFlagsDoNotSayHowManyOthersReportedTheSameContent() {
        UUID reporterId = UUID.randomUUID();
        StubRow row = new StubRow();
        row.reportCount = 7;
        row.pendingCount = 5;
        row.reasons = "SPAM,OFFENSIVE";

        authenticate(reporterId, false);
        when(flagQueueRepository.search(
                isNull(),
                isNull(),
                isNull(),
                eq(reporterId.toString()),
                isNull(),
                eq("NEWEST"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(row)));

        FlagResponse response = service
                .getMyFlags(null, 0, 20)
                .getContent()
                .getFirst();

        assertNull(response.reportCountOnTarget());
        assertNull(response.pendingReportCountOnTarget());
        assertNull(response.allReasons());
        // The content itself is still there — that is the whole point.
        assertEquals("Flyway migration fails", response.target().title());
    }

    @Test
    void adminFlagQueueAppliesAllFilters() {
        StubRow row = new StubRow();
        row.reportCount = 3;
        row.pendingCount = 2;
        row.reasons = "OFF_TOPIC,SPAM";

        authenticate(UUID.randomUUID(), true);
        when(flagQueueRepository.search(
                eq("PENDING"),
                eq("SHOWCASE"),
                eq("SPAM"),
                isNull(),
                eq("%boot%"),
                eq("MOST_REPORTED"),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(row)));

        FlagResponse response = service.getAdminFlags(
                FlagStatus.PENDING,
                FlaggableType.SHOWCASE,
                FlagReason.SPAM,
                "  boot  ",
                FlagQueueSort.MOST_REPORTED,
                0,
                20
        ).getContent().getFirst();

        assertEquals(3L, response.reportCountOnTarget());
        assertEquals(2L, response.pendingReportCountOnTarget());
        // Sorted into enum order rather than however string_agg emitted them,
        // so a card's reasons do not reshuffle between page loads.
        assertEquals(
                List.of(FlagReason.SPAM, FlagReason.OFF_TOPIC),
                response.allReasons()
        );
    }

    /**
     * A moderator searching for a literal {@code 100%} must not match every
     * row. Wildcards in the term are escaped, not honoured.
     */
    @Test
    void searchWildcardsAreEscapedRatherThanHonoured() {
        authenticate(UUID.randomUUID(), true);
        when(flagQueueRepository.search(
                any(),
                any(),
                any(),
                any(),
                eq("%100\\% user\\_id%"),
                any(),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        service.getAdminFlags(
                null,
                null,
                null,
                "100% user_id",
                null,
                0,
                20
        );

        verify(flagQueueRepository).search(
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq("%100\\% user\\_id%"),
                eq("NEWEST"),
                any(Pageable.class)
        );
    }

    @Test
    void blankSearchIsNoSearch() {
        authenticate(UUID.randomUUID(), true);
        when(flagQueueRepository.search(
                any(), any(), any(), any(), isNull(), any(), any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        service.getAdminFlags(null, null, null, "   ", null, 0, 20);

        verify(flagQueueRepository).search(
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq("NEWEST"),
                any(Pageable.class)
        );
    }

    @Test
    void nonAdminsCannotReadTheQueue() {
        authenticate(UUID.randomUUID(), false);

        assertThrows(
                ResponseStatusException.class,
                () -> service.getAdminFlags(
                        null, null, null, null, null, 0, 20
                )
        );
        verifyNoInteractions(flagQueueRepository);
    }

    @Test
    void resolveFlagRecordsReviewerTimeAndResolutionNote() {
        UUID adminId = UUID.randomUUID();
        UUID flagId = UUID.randomUUID();
        UserProfile admin = new UserProfile();
        admin.setId(adminId);
        ContentFlag flag = new ContentFlag();
        flag.setId(flagId);
        flag.setStatus(FlagStatus.PENDING);
        ResolveFlagRequest request = new ResolveFlagRequest(
                "  Content was removed and the author was warned.  ",
                false
        );

        authenticate(adminId, true);
        when(contentFlagRepository.findById(flagId))
                .thenReturn(Optional.of(flag));
        when(userProfileRepository.findById(adminId))
                .thenReturn(Optional.of(admin));
        when(contentFlagRepository.saveAndFlush(flag)).thenReturn(flag);
        when(flagQueueRepository.findRow(flagId.toString()))
                .thenReturn(Optional.of(new StubRow()));

        FlagResponse actual = service.resolveFlag(flagId, request);

        assertNotNull(actual);
        assertEquals(FlagStatus.REVIEWED, flag.getStatus());
        assertEquals(admin, flag.getReviewedBy());
        assertNotNull(flag.getReviewedAt());
        assertEquals(
                "Content was removed and the author was warned.",
                flag.getResolutionNote()
        );
        verify(contentFlagRepository).saveAndFlush(flag);
    }

    @Test
    void resolvingAProgramFlagWithRemovalTakesTheProgramDown() {
        UUID adminId = UUID.randomUUID();
        UUID flagId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UserProfile admin = new UserProfile();
        admin.setId(adminId);
        ContentFlag flag = new ContentFlag();
        flag.setId(flagId);
        flag.setStatus(FlagStatus.PENDING);
        flag.setFlaggableType(FlaggableType.PROGRAM);
        flag.setFlaggableId(programId);
        ResolveFlagRequest request = new ResolveFlagRequest(
                "Program scope invites attacks on third parties.",
                true
        );

        authenticate(adminId, true);
        when(contentFlagRepository.findById(flagId))
                .thenReturn(Optional.of(flag));
        when(userProfileRepository.findById(adminId))
                .thenReturn(Optional.of(admin));
        when(contentFlagRepository.saveAndFlush(flag)).thenReturn(flag);
        when(flagQueueRepository.findRow(flagId.toString()))
                .thenReturn(Optional.of(new StubRow()));

        service.resolveFlag(flagId, request);

        verify(contentTakedownService).takeDown(
                ModerationTargetType.PROGRAM,
                programId,
                "Program scope invites attacks on third parties."
        );
        assertEquals(FlagStatus.REVIEWED, flag.getStatus());
    }

    /**
     * Every flaggable type is removable from here now. This one used to throw
     * 400 and roll the whole transaction back, which left the flag pending and
     * discarded the note the admin had just written.
     */
    @Test
    void resolvingAShowcaseFlagWithRemovalTakesTheShowcaseDown() {
        UUID adminId = UUID.randomUUID();
        UUID flagId = UUID.randomUUID();
        UUID showcaseId = UUID.randomUUID();
        UserProfile admin = new UserProfile();
        admin.setId(adminId);
        ContentFlag flag = new ContentFlag();
        flag.setId(flagId);
        flag.setStatus(FlagStatus.PENDING);
        flag.setFlaggableType(FlaggableType.SHOWCASE);
        flag.setFlaggableId(showcaseId);
        ResolveFlagRequest request = new ResolveFlagRequest(
                "Step-by-step instructions for attacking a third party.",
                true
        );

        authenticate(adminId, true);
        when(contentFlagRepository.findById(flagId))
                .thenReturn(Optional.of(flag));
        when(userProfileRepository.findById(adminId))
                .thenReturn(Optional.of(admin));
        when(contentFlagRepository.saveAndFlush(flag)).thenReturn(flag);
        when(flagQueueRepository.findRow(flagId.toString()))
                .thenReturn(Optional.of(new StubRow()));

        service.resolveFlag(flagId, request);

        verify(contentTakedownService).takeDown(
                ModerationTargetType.SHOWCASE,
                showcaseId,
                "Step-by-step instructions for attacking a third party."
        );
        assertEquals(FlagStatus.REVIEWED, flag.getStatus());
        assertNotNull(flag.getReviewedAt());
    }

    /**
     * Resolving without the removal box ticked must not touch the content. The
     * flag closes, the post stays up.
     */
    @Test
    void resolvingWithoutRemovalLeavesTheContentAlone() {
        UUID adminId = UUID.randomUUID();
        UUID flagId = UUID.randomUUID();
        UserProfile admin = new UserProfile();
        admin.setId(adminId);
        ContentFlag flag = new ContentFlag();
        flag.setId(flagId);
        flag.setStatus(FlagStatus.PENDING);
        flag.setFlaggableType(FlaggableType.COMMENT);
        flag.setFlaggableId(UUID.randomUUID());

        authenticate(adminId, true);
        when(contentFlagRepository.findById(flagId))
                .thenReturn(Optional.of(flag));
        when(userProfileRepository.findById(adminId))
                .thenReturn(Optional.of(admin));
        when(contentFlagRepository.saveAndFlush(flag)).thenReturn(flag);
        when(flagQueueRepository.findRow(flagId.toString()))
                .thenReturn(Optional.of(new StubRow()));

        service.resolveFlag(
                flagId,
                new ResolveFlagRequest("Not a policy breach.", false)
        );

        verifyNoInteractions(contentTakedownService);
        assertEquals(FlagStatus.REVIEWED, flag.getStatus());
    }

    /**
     * Ten reports about one post are one decision. Closing them one at a time
     * is ten chances to take the same post down twice.
     */
    @Test
    void resolvingATargetClosesEveryOpenReportAndRemovesTheContentOnce() {
        UUID adminId = UUID.randomUUID();
        UUID showcaseId = UUID.randomUUID();
        UserProfile admin = new UserProfile();
        admin.setId(adminId);
        ContentFlag first = pendingFlag(FlaggableType.SHOWCASE, showcaseId);
        ContentFlag second = pendingFlag(FlaggableType.SHOWCASE, showcaseId);

        authenticate(adminId, true);
        when(userProfileRepository.findById(adminId))
                .thenReturn(Optional.of(admin));
        when(contentFlagRepository
                .findByFlaggableTypeAndFlaggableIdAndStatus(
                        FlaggableType.SHOWCASE,
                        showcaseId,
                        FlagStatus.PENDING
                ))
                .thenReturn(List.of(first, second));

        TargetFlagActionResponse response = service.resolveTargetFlags(
                FlaggableType.SHOWCASE,
                showcaseId,
                new ResolveFlagRequest("  Spam.  ", true)
        );

        assertEquals(2, response.affected());
        assertTrue(response.contentRemoved());
        assertEquals(FlagStatus.REVIEWED, first.getStatus());
        assertEquals(FlagStatus.REVIEWED, second.getStatus());
        assertEquals("Spam.", first.getResolutionNote());
        assertEquals("Spam.", second.getResolutionNote());
        verify(contentTakedownService).takeDown(
                ModerationTargetType.SHOWCASE,
                showcaseId,
                "Spam."
        );
        verify(contentFlagRepository).saveAll(List.of(first, second));
    }

    /**
     * Two moderators working one queue will reach the same card. The second
     * one is not an error — the content still comes down if they asked for it.
     */
    @Test
    void resolvingATargetAColleagueAlreadyClearedStillRemovesTheContent() {
        UUID adminId = UUID.randomUUID();
        UUID problemId = UUID.randomUUID();
        UserProfile admin = new UserProfile();
        admin.setId(adminId);

        authenticate(adminId, true);
        when(userProfileRepository.findById(adminId))
                .thenReturn(Optional.of(admin));
        when(contentFlagRepository
                .findByFlaggableTypeAndFlaggableIdAndStatus(
                        FlaggableType.PROBLEM,
                        problemId,
                        FlagStatus.PENDING
                ))
                .thenReturn(List.of());

        TargetFlagActionResponse response = service.resolveTargetFlags(
                FlaggableType.PROBLEM,
                problemId,
                new ResolveFlagRequest("Off topic.", true)
        );

        assertEquals(0, response.affected());
        verify(contentTakedownService).takeDown(
                ModerationTargetType.PROBLEM,
                problemId,
                "Off topic."
        );
    }

    @Test
    void dismissingATargetClosesItsReportsWithoutTouchingTheContent() {
        UUID adminId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UserProfile admin = new UserProfile();
        admin.setId(adminId);
        ContentFlag flag = pendingFlag(FlaggableType.COMMENT, commentId);

        authenticate(adminId, true);
        when(userProfileRepository.findById(adminId))
                .thenReturn(Optional.of(admin));
        when(contentFlagRepository
                .findByFlaggableTypeAndFlaggableIdAndStatus(
                        FlaggableType.COMMENT,
                        commentId,
                        FlagStatus.PENDING
                ))
                .thenReturn(List.of(flag));

        TargetFlagActionResponse response = service.dismissTargetFlags(
                FlaggableType.COMMENT,
                commentId
        );

        assertEquals(1, response.affected());
        assertEquals(FlagStatus.DISMISSED, flag.getStatus());
        assertNull(flag.getResolutionNote());
        verifyNoInteractions(contentTakedownService);
    }

    /**
     * The badges. Only pending reports break down by reason and kind — a tab
     * whose count grew forever while the work did not would say nothing.
     */
    @Test
    void summaryBreaksDownOnlyTheOpenQueue() {
        authenticate(UUID.randomUUID(), true);
        when(flagQueueRepository.tally()).thenReturn(List.of(
                tally("PENDING", "SPAM", "PROBLEM", 5),
                tally("PENDING", "SPAM", "COMMENT", 2),
                tally("PENDING", "OFF_TOPIC", "PROBLEM", 1),
                tally("REVIEWED", "SPAM", "PROBLEM", 40),
                tally("DISMISSED", "OTHER", "SHOWCASE", 3)
        ));

        FlagQueueSummaryResponse summary = service.getQueueSummary();

        assertEquals(8, summary.totalPending());
        assertEquals(40, summary.totalResolved());
        assertEquals(3, summary.totalDismissed());
        assertEquals(7L, summary.byReason().get(FlagReason.SPAM));
        assertEquals(1L, summary.byReason().get(FlagReason.OFF_TOPIC));
        assertEquals(6L, summary.byType().get(FlaggableType.PROBLEM));
        assertEquals(2L, summary.byType().get(FlaggableType.COMMENT));
        // Every value present, zeros included: a tab that vanishes at zero is
        // one nobody can use to check that it reached zero.
        assertEquals(
                FlagReason.values().length,
                summary.byReason().size()
        );
        assertEquals(0L, summary.byType().get(FlaggableType.SOLUTION));
    }

    private ContentFlag pendingFlag(FlaggableType type, UUID targetId) {
        ContentFlag flag = new ContentFlag();
        flag.setId(UUID.randomUUID());
        flag.setStatus(FlagStatus.PENDING);
        flag.setFlaggableType(type);
        flag.setFlaggableId(targetId);
        return flag;
    }

    private FlagTally tally(
            String status,
            String reason,
            String type,
            long total
    ) {
        return new FlagTally() {
            @Override
            public String getStatus() {
                return status;
            }

            @Override
            public String getReason() {
                return reason;
            }

            @Override
            public String getFlaggableType() {
                return type;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    private void authenticate(UUID subject, boolean admin) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(subject.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();

        List<SimpleGrantedAuthority> authorities = admin
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, authorities)
        );
    }

    /**
     * A queue row with everything filled in, so a test only has to say what it
     * is about. A Mockito mock would need thirty stubbings before the assembler
     * could read it, and strict stubbing would then fail every test that did
     * not touch all thirty.
     */
    private static final class StubRow implements FlagRow {

        private final UUID id = UUID.randomUUID();
        private final UUID flaggableId = UUID.randomUUID();
        private final UUID reporterId = UUID.randomUUID();
        private String flaggableType = "PROBLEM";
        private String targetState = "PUBLISHED";
        private long reportCount = 1;
        private long pendingCount = 1;
        private String reasons = "SPAM";

        @Override
        public UUID getId() {
            return id;
        }

        @Override
        public String getSource() {
            return "USER";
        }

        @Override
        public String getFlaggableType() {
            return flaggableType;
        }

        @Override
        public UUID getFlaggableId() {
            return flaggableId;
        }

        @Override
        public String getReason() {
            return "SPAM";
        }

        @Override
        public String getDescription() {
            return "Reads like an advert.";
        }

        @Override
        public String getStatus() {
            return "PENDING";
        }

        @Override
        public UUID getReviewedBy() {
            return null;
        }

        @Override
        public LocalDateTime getReviewedAt() {
            return null;
        }

        @Override
        public String getResolutionNote() {
            return null;
        }

        @Override
        public LocalDateTime getCreatedAt() {
            return LocalDateTime.now();
        }

        @Override
        public UUID getReporterId() {
            return reporterId;
        }

        @Override
        public String getReporterName() {
            return "Taing Sengkim";
        }

        @Override
        public String getReporterAvatarUrl() {
            return null;
        }

        @Override
        public Integer getReporterReputation() {
            return 120;
        }

        @Override
        public String getTargetTitle() {
            return "Flyway migration fails";
        }

        @Override
        public String getTargetBody() {
            return "Running the migration against PostgreSQL 16 fails.";
        }

        @Override
        public String getTargetState() {
            return targetState;
        }

        @Override
        public Boolean getTargetDeleted() {
            return false;
        }

        @Override
        public LocalDateTime getTargetCreatedAt() {
            return LocalDateTime.now().minusDays(1);
        }

        @Override
        public String getTargetThumbnailUrl() {
            return null;
        }

        @Override
        public String getTargetSlug() {
            return null;
        }

        @Override
        public UUID getTargetAuthorId() {
            return UUID.randomUUID();
        }

        @Override
        public String getTargetAuthorName() {
            return "Spider Kim";
        }

        @Override
        public String getTargetAuthorAvatarUrl() {
            return null;
        }

        @Override
        public String getTargetParentType() {
            return null;
        }

        @Override
        public UUID getTargetParentId() {
            return null;
        }

        @Override
        public long getReportCount() {
            return reportCount;
        }

        @Override
        public long getPendingCount() {
            return pendingCount;
        }

        @Override
        public String getReasons() {
            return reasons;
        }

        @Override
        public Boolean getAutomated() {
            return false;
        }

        @Override
        public LocalDateTime getFirstReportedAt() {
            return LocalDateTime.now().minusHours(2);
        }

        @Override
        public LocalDateTime getLastReportedAt() {
            return LocalDateTime.now();
        }

        @Override
        public UUID getLatestFlagId() {
            return id;
        }
    }
}
