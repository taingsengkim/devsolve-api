package kh.edu.istad.ite.devsoleapi.feature.category;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.storage.ImageStorageService;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowcaseRevisionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ImageStorageService imageStorageService;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private ShowCasesRepository showCasesRepository;

    @Mock
    private ShowcaseRevisionRepository showcaseRevisionRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private final UUID categoryId = UUID.randomUUID();

    @BeforeEach
    void categoryExistsAndIsUnused() {
        authenticateAdmin();
        when(categoryRepository.existsById(categoryId)).thenReturn(true);
        when(problemRepository.countByCategoryId(categoryId)).thenReturn(0L);
        when(showCasesRepository.countByCategory_Id(categoryId))
                .thenReturn(0L);
        when(showcaseRevisionRepository.countByCategory_Id(categoryId))
                .thenReturn(0L);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAdmin() {
        Jwt jwt = Jwt.withTokenValue("admin-token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("realm_access", java.util.Map.of(
                        "roles", List.of("ADMIN")
                ))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                )
        );
    }

    @Test
    void unusedCategoryIsDeleted() {

        categoryService.deleteCategory(categoryId);

        verify(categoryRepository).deleteById(categoryId);
    }

    @Test
    void categoryUsedByProblemsIsRefused() {

        when(problemRepository.countByCategoryId(categoryId)).thenReturn(4L);

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> categoryService.deleteCategory(categoryId)
        );

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        // Deleting used to succeed here and orphan every one of those
        // problems, because problems.category_id carried no foreign key.
        verify(categoryRepository, never()).deleteById(any());
    }

    @Test
    void categoryUsedByShowcasesIsRefused() {

        when(showCasesRepository.countByCategory_Id(categoryId))
                .thenReturn(2L);

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> categoryService.deleteCategory(categoryId)
        );

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        verify(categoryRepository, never()).deleteById(any());
    }

    @Test
    void categoryUsedOnlyByAShowcaseRevisionIsRefused() {

        when(showcaseRevisionRepository.countByCategory_Id(categoryId))
                .thenReturn(1L);

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> categoryService.deleteCategory(categoryId)
        );

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
    }

    @Test
    void refusalNamesWhatIsUsingItAndWhatToDoInstead() {

        when(problemRepository.countByCategoryId(categoryId)).thenReturn(3L);
        when(showCasesRepository.countByCategory_Id(categoryId))
                .thenReturn(1L);

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> categoryService.deleteCategory(categoryId)
        );

        String reason = failure.getReason();
        assertTrue(reason.contains("3 problems"), reason);
        assertTrue(reason.contains("1 showcase"), reason);
        // An admin told only "conflict" has no idea what to do next.
        assertTrue(reason.contains("isActive"), reason);
    }

    @Test
    void contentCreatedDuringTheCheckStillLosesToTheDatabase() {

        // The count passed, then something filed content under the category
        // before the delete landed. The foreign key is what actually holds.
        doThrow(new DataIntegrityViolationException("fk_problems_category"))
                .when(categoryRepository).flush();

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> categoryService.deleteCategory(categoryId)
        );

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
    }

    @Test
    void onlyAdminsCanDelete() {

        SecurityContextHolder.clearContext();

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> categoryService.deleteCategory(categoryId)
        );

        assertEquals(HttpStatus.FORBIDDEN, failure.getStatusCode());
        verify(problemRepository, never()).countByCategoryId(any());
    }

    @Test
    void deletingAMissingCategoryIsNotFound() {

        UUID unknown = UUID.randomUUID();
        when(categoryRepository.existsById(unknown)).thenReturn(false);

        assertThrows(
                ResourceNotFoundException.class,
                () -> categoryService.deleteCategory(unknown)
        );
    }
}
