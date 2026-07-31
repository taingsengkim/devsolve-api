package kh.edu.istad.ite.devsoleapi.feature.moderation.action;

import kh.edu.istad.ite.devsoleapi.common.props.KeycloakAdminProps;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto.ModerationActionResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModerationServiceImplTest {

    @Mock
    private ModerationActionRepository moderationActionRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ModerationActionMapper moderationActionMapper;

    @Mock
    private Keycloak keycloak;

    @Mock
    private KeycloakAdminProps keycloakAdminProps;

    private ModerationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ModerationServiceImpl(
                moderationActionRepository,
                userProfileRepository,
                moderationActionMapper,
                keycloak,
                keycloakAdminProps
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getModerationHistoryAppliesFiltersAndNewestSort() {
        UUID targetId = UUID.randomUUID();
        ModerationAction action = new ModerationAction();
        action.setId(UUID.randomUUID());
        ModerationActionResponse expected = response(action.getId());
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);

        authenticateAdmin();
        when(moderationActionRepository.searchHistory(
                eq(ModerationTargetType.SHOWCASE),
                eq(targetId),
                eq(ModerationActionType.WARN),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(action)));
        when(moderationActionMapper
                .mapModerationActionToModerationActionResponse(action))
                .thenReturn(expected);

        Page<ModerationActionResponse> result =
                service.getModerationHistory(
                        ModerationTargetType.SHOWCASE,
                        targetId,
                        ModerationActionType.WARN,
                        2,
                        15
                );

        assertEquals(List.of(expected), result.getContent());
        verify(moderationActionRepository).searchHistory(
                eq(ModerationTargetType.SHOWCASE),
                eq(targetId),
                eq(ModerationActionType.WARN),
                pageableCaptor.capture()
        );
        Pageable pageable = pageableCaptor.getValue();
        assertEquals(2, pageable.getPageNumber());
        assertEquals(15, pageable.getPageSize());
        assertEquals(
                Sort.Direction.DESC,
                pageable.getSort()
                        .getOrderFor("createdAt")
                        .getDirection()
        );
    }

    @Test
    void getModerationActionByIdReturnsStoredDecision() {
        UUID actionId = UUID.randomUUID();
        ModerationAction action = new ModerationAction();
        action.setId(actionId);
        ModerationActionResponse expected = response(actionId);

        authenticateAdmin();
        when(moderationActionRepository.findById(actionId))
                .thenReturn(Optional.of(action));
        when(moderationActionMapper
                .mapModerationActionToModerationActionResponse(action))
                .thenReturn(expected);

        ModerationActionResponse actual =
                service.getModerationActionById(actionId);

        assertSame(expected, actual);
    }

    private ModerationActionResponse response(UUID id) {
        return new ModerationActionResponse(
                id,
                null,
                null,
                ModerationTargetType.SHOWCASE,
                UUID.randomUUID(),
                ModerationActionType.WARN,
                "Policy violation",
                null,
                null
        );
    }

    private void authenticateAdmin() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );
    }
}
