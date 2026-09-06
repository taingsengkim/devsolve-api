package kh.edu.istad.ite.devsoleapi.feature.program.invitation;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationAuthorizationService;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationPermission;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.program.invitation.dto.InviteToProgramRequest;
import kh.edu.istad.ite.devsoleapi.feature.program.invitation.dto.ProgramInvitationResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramInvitationServiceImplTest {

    @Mock
    private ProgramInvitationRepository invitationRepository;

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private OrganizationAuthorizationService organizationAuthorization;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ProgramInvitationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProgramInvitationServiceImpl(
                invitationRepository,
                programRepository,
                userProfileRepository,
                organizationAuthorization,
                new ProgramInvitationMapper(),
                eventPublisher
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---- what an invitation grants, and when -----------------------------

    /**
     * The distinction the whole feature turns on. A researcher who has been
     * asked but has not answered must be able to read the program — nobody can
     * judge an invitation to a program whose scope they are not allowed to see.
     */
    @Test
    void anUnansweredInvitationOpensTheProgramToBeReadButNotReportedTo() {
        Program program = privateProgram();
        UUID researcherId = UUID.randomUUID();
        ProgramInvitation invitation = invitation(
                program,
                researcherId,
                ProgramInvitationStatus.INVITED
        );

        when(invitationRepository.findByProgram_IdAndResearcher_Id(
                program.getId(),
                researcherId
        )).thenReturn(Optional.of(invitation));

        assertTrue(service.canView(program, researcherId));

        ResponseStatusException refusal = assertThrows(
                ResponseStatusException.class,
                () -> service.requireAcceptedMember(program, researcherId)
        );
        assertEquals(HttpStatus.FORBIDDEN, refusal.getStatusCode());
        assertTrue(refusal.getReason().contains("Accept your invitation"));
    }

    @Test
    void acceptingOpensReporting() {
        Program program = privateProgram();
        UUID researcherId = UUID.randomUUID();

        when(invitationRepository.findByProgram_IdAndResearcher_Id(
                program.getId(),
                researcherId
        )).thenReturn(Optional.of(invitation(
                program,
                researcherId,
                ProgramInvitationStatus.ACCEPTED
        )));

        assertTrue(service.canView(program, researcherId));
        service.requireAcceptedMember(program, researcherId);
    }

    /**
     * Declining and revocation both close the door, and each says which it was
     * — a researcher who declined last month should not be told their access
     * was withdrawn.
     */
    @Test
    void decliningAndRevokingBothCloseAccessAndSaySoDifferently() {
        Program program = privateProgram();
        UUID declined = UUID.randomUUID();
        UUID revoked = UUID.randomUUID();

        when(invitationRepository.findByProgram_IdAndResearcher_Id(
                program.getId(),
                declined
        )).thenReturn(Optional.of(invitation(
                program,
                declined,
                ProgramInvitationStatus.DECLINED
        )));
        when(invitationRepository.findByProgram_IdAndResearcher_Id(
                program.getId(),
                revoked
        )).thenReturn(Optional.of(invitation(
                program,
                revoked,
                ProgramInvitationStatus.REVOKED
        )));

        assertFalse(service.canView(program, declined));
        assertFalse(service.canView(program, revoked));

        assertTrue(assertThrows(
                ResponseStatusException.class,
                () -> service.requireAcceptedMember(program, declined)
        ).getReason().contains("declined"));

        assertTrue(assertThrows(
                ResponseStatusException.class,
                () -> service.requireAcceptedMember(program, revoked)
        ).getReason().contains("withdrawn"));
    }

    /**
     * Somebody who was never asked got here with an ID they were not given.
     * They are told the program takes invitations and nothing else about it.
     */
    @Test
    void aStrangerIsToldNothingAboutTheProgram() {
        Program program = privateProgram();
        UUID stranger = UUID.randomUUID();

        when(invitationRepository.findByProgram_IdAndResearcher_Id(
                program.getId(),
                stranger
        )).thenReturn(Optional.empty());

        assertFalse(service.canView(program, stranger));

        String reason = assertThrows(
                ResponseStatusException.class,
                () -> service.requireAcceptedMember(program, stranger)
        ).getReason();
        assertTrue(reason.contains("private"));
        assertFalse(reason.contains(program.getName()));
    }

    /** An anonymous caller is on no guest list, and must not blow up the check. */
    @Test
    void anAnonymousCallerCannotViewAPrivateProgram() {
        assertFalse(service.canView(privateProgram(), null));
        verify(invitationRepository, never())
                .findByProgram_IdAndResearcher_Id(any(), any());
    }

    /** A public program is nobody's private business; the list is not consulted. */
    @Test
    void aPublicProgramNeedsNoInvitation() {
        Program program = privateProgram();
        program.setVisibility(Visibility.PUBLIC);

        assertTrue(service.canView(program, UUID.randomUUID()));
        assertTrue(service.canView(program, null));
        verify(invitationRepository, never())
                .findByProgram_IdAndResearcher_Id(any(), any());
    }

    // ---- inviting --------------------------------------------------------

    @Test
    void invitingAsksTheResearcherAndTellsThem() {
        UUID actorId = UUID.randomUUID();
        Program program = privateProgram();
        UUID researcherId = UUID.randomUUID();
        UserProfile researcher = user(researcherId, "Ada Lovelace");

        authenticate(actorId, true);
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(userProfileRepository.findById(actorId))
                .thenReturn(Optional.of(user(actorId, "Triage Lead")));
        when(userProfileRepository.findByIdAndStatus(
                researcherId,
                UserStatus.ACTIVE
        )).thenReturn(Optional.of(researcher));
        when(invitationRepository.findByProgram_IdAndResearcher_Id(
                program.getId(),
                researcherId
        )).thenReturn(Optional.empty());
        when(invitationRepository.saveAndFlush(any(ProgramInvitation.class)))
                .thenAnswer(call -> call.getArgument(0));

        ProgramInvitationResponse response = service.invite(
                program.getId(),
                new InviteToProgramRequest(researcherId, "  Come and look.  ")
        );

        assertEquals(ProgramInvitationStatus.INVITED, response.status());
        assertEquals("Come and look.", response.note());
        assertEquals(program.getId(), response.programId());
        assertEquals("Ada Lovelace", response.researcherName());
        assertNotNull(response.invitedAt());
        verify(organizationAuthorization).requirePermission(
                eq(program.getOrganizationId()),
                eq(actorId),
                // Deciding who may see a private program is the same judgement
                // as deciding who may report to the company, and not the same
                // as editing the program.
                eq(OrganizationPermission.MANAGE_RESEARCHERS)
        );
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    /**
     * Re-inviting somebody who already accepted would reset them to INVITED and
     * quietly take away the access they are currently using.
     */
    @Test
    void anAcceptedResearcherCannotBeReInvited() {
        UUID actorId = UUID.randomUUID();
        Program program = privateProgram();
        UUID researcherId = UUID.randomUUID();

        authenticate(actorId, true);
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(userProfileRepository.findById(actorId))
                .thenReturn(Optional.of(user(actorId, "Triage Lead")));
        when(userProfileRepository.findByIdAndStatus(
                researcherId,
                UserStatus.ACTIVE
        )).thenReturn(Optional.of(user(researcherId, "Ada Lovelace")));
        when(invitationRepository.findByProgram_IdAndResearcher_Id(
                program.getId(),
                researcherId
        )).thenReturn(Optional.of(invitation(
                program,
                researcherId,
                ProgramInvitationStatus.ACCEPTED
        )));

        ResponseStatusException conflict = assertThrows(
                ResponseStatusException.class,
                () -> service.invite(
                        program.getId(),
                        new InviteToProgramRequest(researcherId, null)
                )
        );

        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        verify(invitationRepository, never())
                .saveAndFlush(any(ProgramInvitation.class));
    }

    /** A researcher who said no can be asked again — the row is reused. */
    @Test
    void aDeclinedResearcherCanBeAskedAgain() {
        UUID actorId = UUID.randomUUID();
        Program program = privateProgram();
        UUID researcherId = UUID.randomUUID();
        ProgramInvitation existing = invitation(
                program,
                researcherId,
                ProgramInvitationStatus.DECLINED
        );
        int revisionBefore = existing.getRevision();

        authenticate(actorId, true);
        when(programRepository.findById(program.getId()))
                .thenReturn(Optional.of(program));
        when(userProfileRepository.findById(actorId))
                .thenReturn(Optional.of(user(actorId, "Triage Lead")));
        when(userProfileRepository.findByIdAndStatus(
                researcherId,
                UserStatus.ACTIVE
        )).thenReturn(Optional.of(user(researcherId, "Ada Lovelace")));
        when(invitationRepository.findByProgram_IdAndResearcher_Id(
                program.getId(),
                researcherId
        )).thenReturn(Optional.of(existing));
        when(invitationRepository.saveAndFlush(any(ProgramInvitation.class)))
                .thenAnswer(call -> call.getArgument(0));

        ProgramInvitationResponse response = service.invite(
                program.getId(),
                new InviteToProgramRequest(researcherId, null)
        );

        assertEquals(ProgramInvitationStatus.INVITED, response.status());
        // Stepped, so the second invitation is not swallowed as a duplicate
        // notification of the first.
        assertTrue(existing.getRevision() > revisionBefore);
    }

    // ---- answering -------------------------------------------------------

    @Test
    void acceptingAnInvitationRecordsItAndTellsTheCompany() {
        UUID researcherId = UUID.randomUUID();
        Program program = privateProgram();
        ProgramInvitation invitation = invitation(
                program,
                researcherId,
                ProgramInvitationStatus.INVITED
        );

        authenticate(researcherId, false);
        when(invitationRepository.findByProgram_IdAndResearcher_Id(
                program.getId(),
                researcherId
        )).thenReturn(Optional.of(invitation));
        when(invitationRepository.saveAndFlush(invitation))
                .thenReturn(invitation);

        ProgramInvitationResponse response = service.accept(program.getId());

        assertEquals(ProgramInvitationStatus.ACCEPTED, response.status());
        assertNotNull(response.respondedAt());
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void anInvitationCanOnlyBeAnsweredOnce() {
        UUID researcherId = UUID.randomUUID();
        Program program = privateProgram();

        authenticate(researcherId, false);
        when(invitationRepository.findByProgram_IdAndResearcher_Id(
                program.getId(),
                researcherId
        )).thenReturn(Optional.of(invitation(
                program,
                researcherId,
                ProgramInvitationStatus.ACCEPTED
        )));

        assertEquals(
                HttpStatus.CONFLICT,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.accept(program.getId())
                ).getStatusCode()
        );
    }

    @Test
    void answeringAnInvitationNobodySentIsANotFound() {
        UUID researcherId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();

        authenticate(researcherId, false);
        when(invitationRepository.findByProgram_IdAndResearcher_Id(
                programId,
                researcherId
        )).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.accept(programId)
        );
    }

    // ---- fixtures --------------------------------------------------------

    private Program privateProgram() {
        Program program = new Program();
        program.setId(UUID.randomUUID());
        program.setOrganizationId(UUID.randomUUID());
        program.setName("Ledger private program");
        program.setHandle("ledger-private");
        program.setVisibility(Visibility.PRIVATE);
        return program;
    }

    private ProgramInvitation invitation(
            Program program,
            UUID researcherId,
            ProgramInvitationStatus status
    ) {
        ProgramInvitation invitation = new ProgramInvitation(
                program,
                user(researcherId, "Ada Lovelace")
        );
        invitation.setId(UUID.randomUUID());
        invitation.setStatus(status);
        invitation.setRevision(1);
        return invitation;
    }

    private UserProfile user(UUID id, String fullName) {
        UserProfile profile = new UserProfile();
        profile.setId(id);
        profile.setFullName(fullName);
        return profile;
    }


    private void authenticate(UUID subject, boolean staff) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(subject.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();

        List<SimpleGrantedAuthority> authorities = staff
                ? List.of(new SimpleGrantedAuthority("ROLE_USER"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, authorities)
        );
        lenient().when(organizationAuthorization.requirePermission(
                any(),
                any(),
                any()
        )).thenReturn(null);
    }
}
