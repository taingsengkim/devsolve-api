package kh.edu.istad.ite.devsoleapi.feature.moderation.action;

import kh.edu.istad.ite.devsoleapi.common.props.KeycloakAdminProps;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto.CreateModerationActionRequest;
import kh.edu.istad.ite.devsoleapi.feature.moderation.action.dto.ModerationActionResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

    @Mock
    private KeycloakAccountService keycloakAccountService;

    @Mock
    private RealmResource realmResource;

    @Mock
    private UsersResource usersResource;

    @Mock
    private UserResource userResource;

    @Mock
    private RoleMappingResource roleMappingResource;

    @Mock
    private RoleScopeResource roleScopeResource;

    private ModerationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ModerationServiceImpl(
                moderationActionRepository,
                userProfileRepository,
                moderationActionMapper,
                keycloakAccountService,
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

    @Test
    void reinstateReturnsTheAccountToActiveAndReEnablesTheLogin() {
        UUID adminId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UserProfile target = profile(targetId, UserStatus.REMOVED);

        authenticateAdmin(adminId);
        stubModerationTarget(adminId, targetId, target);
        stubMapper();

        runInTransaction(() -> service.createModerationAction(
                targetId,
                new CreateModerationActionRequest(
                        ModerationActionType.REINSTATE,
                        "Appeal upheld",
                        null
                )
        ));

        assertEquals(UserStatus.ACTIVE, target.getStatus());
        verify(userProfileRepository).save(target);
        verify(keycloakAccountService).enable(targetId);
        verify(keycloakAccountService, never()).disable(any());
    }

    @Test
    void banRemovesTheAccountAndDisablesTheLogin() {
        UUID adminId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UserProfile target = profile(targetId, UserStatus.ACTIVE);

        authenticateAdmin(adminId);
        stubModerationTarget(adminId, targetId, target);
        stubMapper();

        runInTransaction(() -> service.createModerationAction(
                targetId,
                new CreateModerationActionRequest(
                        ModerationActionType.BAN,
                        "Fraudulent reports",
                        null
                )
        ));

        assertEquals(UserStatus.REMOVED, target.getStatus());
        verify(keycloakAccountService).disable(targetId);
        verify(keycloakAccountService, never()).enable(any());
    }

    @Test
    void reinstatingAnAlreadyActiveAccountIsRejected() {
        UUID adminId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UserProfile target = profile(targetId, UserStatus.ACTIVE);

        authenticateAdmin(adminId);
        stubModerationTarget(adminId, targetId, target);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createModerationAction(
                        targetId,
                        new CreateModerationActionRequest(
                                ModerationActionType.REINSTATE,
                                "Appeal upheld",
                                null
                        )
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(keycloakAccountService, never()).enable(any());
    }

    /**
     * The Keycloak sync is deferred to {@code afterCommit}, so the assertions
     * need a transaction boundary to close before they can observe it.
     */
    private void runInTransaction(Runnable work) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            work.run();
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    /**
     * Only needed by paths that reach persistence; the rejection tests throw
     * before the mapper is touched.
     */
    private void stubMapper() {
        when(moderationActionMapper
                .mapCreateModerationActionRequestToModerationAction(any()))
                .thenReturn(new ModerationAction());
    }

    private void stubModerationTarget(
            UUID adminId,
            UUID targetId,
            UserProfile target
    ) {
        when(userProfileRepository.findById(adminId))
                .thenReturn(Optional.of(profile(adminId, UserStatus.ACTIVE)));
        when(userProfileRepository.findById(targetId))
                .thenReturn(Optional.of(target));
        when(keycloakAdminProps.getTargetRealm()).thenReturn("devsolve");
        when(keycloak.realm("devsolve")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.get(targetId.toString())).thenReturn(userResource);
        when(userResource.roles()).thenReturn(roleMappingResource);
        when(roleMappingResource.realmLevel()).thenReturn(roleScopeResource);
        when(roleScopeResource.listAll()).thenReturn(List.of());
    }

    private UserProfile profile(UUID id, UserStatus status) {
        UserProfile profile = new UserProfile();
        profile.setId(id);
        profile.setEmail(id + "@example.com");
        profile.setFullName("User " + id);
        profile.setStatus(status);
        return profile;
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
        authenticateAdmin(UUID.randomUUID());
    }

    private void authenticateAdmin(UUID adminId) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(adminId.toString())
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
