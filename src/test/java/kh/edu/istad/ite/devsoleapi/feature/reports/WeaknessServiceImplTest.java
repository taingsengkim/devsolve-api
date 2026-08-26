package kh.edu.istad.ite.devsoleapi.feature.reports;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.CreateWeaknessRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.UpdateWeaknessRequest;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.WeaknessMapper;
import kh.edu.istad.ite.devsoleapi.feature.reports.dto.WeaknessResponse;
import kh.edu.istad.ite.devsoleapi.feature.reports.entities.Weakness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeaknessServiceImplTest {

    @Mock
    private WeaknessRepository weaknessRepository;

    @Mock
    private ReportRepository reportRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void administratorAddsAWeaknessAndTheIdentifierIsStoredCanonically() {
        authenticate("ADMIN");
        when(weaknessRepository.existsByCweIdIgnoreCase("CWE-79"))
                .thenReturn(false);
        when(weaknessRepository.existsByNameIgnoreCase("Cross-site Scripting"))
                .thenReturn(false);
        when(weaknessRepository.saveAndFlush(any(Weakness.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WeaknessResponse response = service().create(
                new CreateWeaknessRequest(
                        "cwe 79",
                        "  Cross-site Scripting  ",
                        "Script runs in the browser of another user.",
                        null
                )
        );

        assertEquals("CWE-79", response.cweId());
        assertEquals("Cross-site Scripting", response.name());
        // Unspecified means available: an administrator adding a class is
        // adding one people should be able to file under.
        assertTrue(response.isActive());
    }

    @Test
    void anIdentifierThatIsNotACweIsRefused() {
        authenticate("ADMIN");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().create(new CreateWeaknessRequest(
                        "OWASP-A03",
                        "Injection",
                        null,
                        null
                ))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(weaknessRepository, never()).saveAndFlush(any());
    }

    @Test
    void theSameCweCannotBeAddedTwice() {
        authenticate("ADMIN");
        when(weaknessRepository.existsByCweIdIgnoreCase("CWE-89"))
                .thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().create(new CreateWeaknessRequest(
                        "89",
                        "SQL Injection",
                        null,
                        null
                ))
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void aNonAdministratorCannotTouchTheCatalog() {
        authenticate("USER");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().create(new CreateWeaknessRequest(
                        "79",
                        "Cross-site Scripting",
                        null,
                        null
                ))
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
    }

    @Test
    void anOmittedFieldOnAnUpdateIsLeftAlone() {
        authenticate("ADMIN");
        Weakness weakness = weakness("CWE-79", "Cross-site Scripting");
        when(weaknessRepository.findById(weakness.getId()))
                .thenReturn(Optional.of(weakness));
        when(weaknessRepository.saveAndFlush(weakness)).thenReturn(weakness);

        service().update(
                weakness.getId(),
                new UpdateWeaknessRequest(null, null, null, false)
        );

        assertEquals("CWE-79", weakness.getCweId());
        assertEquals("Cross-site Scripting", weakness.getName());
        assertEquals(false, weakness.getIsActive());
    }

    @Test
    void aWeaknessReportsAreFiledUnderIsRetiredRatherThanDeleted() {
        authenticate("ADMIN");
        Weakness weakness = weakness("CWE-89", "SQL Injection");
        when(weaknessRepository.findById(weakness.getId()))
                .thenReturn(Optional.of(weakness));
        when(reportRepository.countByWeaknessId(weakness.getId()))
                .thenReturn(3L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().delete(weakness.getId())
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertTrue(exception.getReason().contains("Deactivate it instead"));
        verify(weaknessRepository, never()).delete(any());
    }

    @Test
    void anUnusedWeaknessCanBeDeleted() {
        authenticate("ADMIN");
        Weakness weakness = weakness("CWE-89", "Typo");
        when(weaknessRepository.findById(weakness.getId()))
                .thenReturn(Optional.of(weakness));
        when(reportRepository.countByWeaknessId(weakness.getId()))
                .thenReturn(0L);

        service().delete(weakness.getId());

        verify(weaknessRepository).delete(weakness);
    }

    @Test
    void deletingAWeaknessThatIsNotThereIsANotFound() {
        authenticate("ADMIN");
        UUID missingId = UUID.randomUUID();
        when(weaknessRepository.findById(missingId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service().delete(missingId)
        );
    }

    @Test
    void searchingMatchesTheIdentifierAsWellAsTheName() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("name"));
        when(weaknessRepository.searchActive(eq("%89%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        weakness("CWE-89", "SQL Injection")
                )));

        Page<WeaknessResponse> page = service().findActive("  89  ", pageable);

        assertEquals(1, page.getTotalElements());
        assertEquals("CWE-89", page.getContent().getFirst().cweId());
    }

    @Test
    void aBlankSearchReturnsTheWholeCatalog() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("name"));
        when(weaknessRepository.searchActive(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service().findActive(null, pageable);

        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(weaknessRepository)
                .searchActive(pattern.capture(), any(Pageable.class));
        assertEquals("%", pattern.getValue());
    }

    /**
     * A LIKE wildcard the searcher typed is a literal, not a wildcard, or
     * typing "%" would quietly return everything.
     */
    @Test
    void aWildcardInTheSearchTermIsEscaped() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("name"));
        when(weaknessRepository.searchActive(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service().findActive("100%", pageable);

        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(weaknessRepository)
                .searchActive(pattern.capture(), any(Pageable.class));
        assertEquals("%100!%%", pattern.getValue());
    }

    @Test
    void anUnsupportedSortPropertyIsRefused() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by("secretColumn"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().findActive(null, pageable)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void theAdministratorListingCanIncludeRetiredEntries() {
        authenticate("ADMIN");
        Pageable pageable = PageRequest.of(0, 20, Sort.by("name"));
        when(weaknessRepository.searchAll(eq("%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service().findForAdmin(null, false, pageable);

        verify(weaknessRepository).searchAll(eq("%"), any(Pageable.class));
        verify(weaknessRepository, never())
                .searchActive(any(), any(Pageable.class));
    }

    private WeaknessServiceImpl service() {
        return new WeaknessServiceImpl(
                weaknessRepository,
                reportRepository,
                new WeaknessMapper()
        );
    }

    private Weakness weakness(String cweId, String name) {
        return Weakness.builder()
                .id(UUID.randomUUID())
                .cweId(cweId)
                .name(name)
                .isActive(true)
                .build();
    }

    private void authenticate(String role) {
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
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                )
        );
    }
}
