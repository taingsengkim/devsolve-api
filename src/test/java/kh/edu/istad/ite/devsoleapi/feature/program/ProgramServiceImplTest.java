package kh.edu.istad.ite.devsoleapi.feature.program;

import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationMemberRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.program.dto.ProgramRequestDto;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.EngagementType;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.program.program_update.ProgramUpdateRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.UserProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramServiceImplTest {

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private ProgramUpdateRepository programUpdateRepository;

    @Mock
    private ProgramMapper programMapper;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createProgramUsesOwnedOrganizationId() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        authenticateCompany(userId);

        UserProfile owner = new UserProfile();
        owner.setId(userId);

        Organization organization = new Organization();
        organization.setId(organizationId);
        organization.setOwner(owner);
        organization.setStatus(OrganizationStatus.ACTIVE);

        Program program = new Program();
        when(organizationRepository.findByOwnerIdAndDeletedAtIsNull(userId))
                .thenReturn(Optional.of(organization));
        when(programMapper.toEntity(any(ProgramRequestDto.class)))
                .thenReturn(program);
        when(programRepository.save(program))
                .thenReturn(program);

        ProgramServiceImpl service = new ProgramServiceImpl(
                programRepository,
                programUpdateRepository,
                programMapper,
                organizationRepository,
                organizationMemberRepository
        );

        service.createProgram(ProgramRequestDto.builder()
                .handle("acme-security")
                .name("Acme Security Program")
                .engagementType(EngagementType.BOUNTY)
                .visibility(Visibility.PUBLIC)
                .build());

        ArgumentCaptor<Program> programCaptor =
                ArgumentCaptor.forClass(Program.class);
        verify(programRepository).save(programCaptor.capture());

        assertEquals(
                organizationId,
                programCaptor.getValue().getOrganizationId()
        );
        assertEquals(
                ProgramState.DRAFT,
                programCaptor.getValue().getState()
        );
        assertEquals(
                SubmissionState.PENDING_REVIEW,
                programCaptor.getValue().getSubmissionState()
        );
    }

    private void authenticateCompany(UUID userId) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_COMPANY"))
                )
        );
    }
}
