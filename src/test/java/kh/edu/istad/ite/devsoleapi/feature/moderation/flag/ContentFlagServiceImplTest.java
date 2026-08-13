package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

import kh.edu.istad.ite.devsoleapi.feature.comments.CommentService;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagResponse;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.ResolveFlagRequest;
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
import org.springframework.data.domain.Sort;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentFlagServiceImplTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ContentFlagRepository contentFlagRepository;

    @Mock
    private ContentFlagMapper contentFlagMapper;

    @Mock
    private CommentService commentService;

    private ContentFlagServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ContentFlagServiceImpl(
                userProfileRepository,
                contentFlagRepository,
                contentFlagMapper,
                commentService
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMyFlagsReturnsOnlyAuthenticatedUsersFlags() {
        UUID reporterId = UUID.randomUUID();
        ContentFlag flag = new ContentFlag();
        flag.setId(UUID.randomUUID());
        FlagResponse expected = response(
                flag.getId(),
                FlagStatus.PENDING
        );
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        authenticate(reporterId, false);
        when(contentFlagRepository.findMyFlags(
                eq(reporterId),
                eq(FlagStatus.PENDING),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(flag)));
        when(contentFlagMapper.mapContentFlagToFlagResponse(flag))
                .thenReturn(expected);

        Page<FlagResponse> result = service.getMyFlags(
                FlagStatus.PENDING,
                1,
                10
        );

        assertEquals(List.of(expected), result.getContent());
        verify(contentFlagRepository).findMyFlags(
                eq(reporterId),
                eq(FlagStatus.PENDING),
                pageableCaptor.capture()
        );
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(1, pageable.getPageNumber());
        assertEquals(10, pageable.getPageSize());
        assertEquals(
                Sort.Direction.DESC,
                pageable.getSort()
                        .getOrderFor("createdAt")
                        .getDirection()
        );
    }

    @Test
    void adminFlagQueueAppliesAllFilters() {
        ContentFlag flag = new ContentFlag();
        flag.setId(UUID.randomUUID());
        FlagResponse expected = response(
                flag.getId(),
                FlagStatus.PENDING
        );

        authenticate(UUID.randomUUID(), true);
        when(contentFlagRepository.searchAdminFlags(
                eq(FlagStatus.PENDING),
                eq(FlaggableType.SHOWCASE),
                eq(FlagReason.SPAM),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(flag)));
        when(contentFlagMapper.mapContentFlagToFlagResponse(flag))
                .thenReturn(expected);

        Page<FlagResponse> result = service.getAdminFlags(
                FlagStatus.PENDING,
                FlaggableType.SHOWCASE,
                FlagReason.SPAM,
                0,
                20
        );

        assertEquals(List.of(expected), result.getContent());
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
        FlagResponse expected = response(
                flagId,
                FlagStatus.REVIEWED
        );

        authenticate(adminId, true);
        when(contentFlagRepository.findById(flagId))
                .thenReturn(Optional.of(flag));
        when(userProfileRepository.findById(adminId))
                .thenReturn(Optional.of(admin));
        when(contentFlagRepository.save(flag)).thenReturn(flag);
        when(contentFlagMapper.mapContentFlagToFlagResponse(flag))
                .thenReturn(expected);

        FlagResponse actual = service.resolveFlag(
                flagId,
                request
        );

        assertSame(expected, actual);
        assertEquals(FlagStatus.REVIEWED, flag.getStatus());
        assertSame(admin, flag.getReviewedBy());
        assertNotNull(flag.getReviewedAt());
        assertEquals(
                "Content was removed and the author was warned.",
                flag.getResolutionNote()
        );
        verify(contentFlagRepository).save(flag);
    }

    private FlagResponse response(
            UUID id,
            FlagStatus status
    ) {
        return new FlagResponse(
                id,
                null,
                null,
                FlaggableType.SHOWCASE,
                UUID.randomUUID(),
                FlagReason.SPAM,
                null,
                status,
                null,
                null,
                null,
                null
        );
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
}
