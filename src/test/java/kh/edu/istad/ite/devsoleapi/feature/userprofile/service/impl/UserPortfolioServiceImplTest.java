package kh.edu.istad.ite.devsoleapi.feature.userprofile.service.impl;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemService;
import kh.edu.istad.ite.devsoleapi.feature.problem.dto.ProblemResponse;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesService;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowCasesSummaryResponse;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionService;
import kh.edu.istad.ite.devsoleapi.feature.solution.dto.SolutionResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPortfolioServiceImplTest {

    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private ProblemService problemService;
    @Mock
    private SolutionService solutionService;
    @Mock
    private ShowCasesService showCasesService;

    @Test
    void problemsRequireActiveProfileAndUseNewestPublishedFirst() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findByIdAndStatus(
                userId,
                UserStatus.ACTIVE
        )).thenReturn(Optional.of(profile(userId)));
        ArgumentCaptor<Pageable> pageableCaptor =
                ArgumentCaptor.forClass(Pageable.class);
        when(problemService.findPublishedByAuthor(
                org.mockito.ArgumentMatchers.eq(userId),
                pageableCaptor.capture()
        )).thenReturn(Page.empty());

        Page<ProblemResponse> result = service().getProblems(
                userId,
                1,
                25
        );

        assertEquals(0, result.getTotalElements());
        assertEquals(1, pageableCaptor.getValue().getPageNumber());
        assertEquals(25, pageableCaptor.getValue().getPageSize());
        assertEquals(
                Sort.Direction.DESC,
                pageableCaptor.getValue().getSort()
                        .getOrderFor("publishedAt")
                        .getDirection()
        );
    }

    @Test
    void solutionsDelegateToPublicAuthorQuery() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findByIdAndStatus(
                userId,
                UserStatus.ACTIVE
        )).thenReturn(Optional.of(profile(userId)));
        when(solutionService.getPublicByAuthor(userId, 0, 20))
                .thenReturn(Page.empty());

        Page<SolutionResponse> result = service().getSolutions(
                userId,
                0,
                20
        );

        assertEquals(0, result.getTotalElements());
        verify(solutionService).getPublicByAuthor(userId, 0, 20);
    }

    @Test
    void showcasesDelegateToApprovedAuthorQuery() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findByIdAndStatus(
                userId,
                UserStatus.ACTIVE
        )).thenReturn(Optional.of(profile(userId)));
        when(showCasesService.getPublishedByAuthor(userId, 0, 20))
                .thenReturn(Page.empty());

        Page<ShowCasesSummaryResponse> result = service().getShowcases(
                userId,
                0,
                20
        );

        assertEquals(0, result.getTotalElements());
        verify(showCasesService).getPublishedByAuthor(userId, 0, 20);
    }

    @Test
    void inactiveProfileCannotExposeAnyPortfolioContent() {
        UUID userId = UUID.randomUUID();
        when(userProfileRepository.findByIdAndStatus(
                userId,
                UserStatus.ACTIVE
        )).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service().getProblems(userId, 0, 20)
        );
        verify(problemService, never()).findPublishedByAuthor(
                any(),
                any()
        );
    }

    private UserPortfolioServiceImpl service() {
        return new UserPortfolioServiceImpl(
                userProfileRepository,
                problemService,
                solutionService,
                showCasesService
        );
    }

    private UserProfile profile(UUID userId) {
        UserProfile profile = new UserProfile();
        profile.setId(userId);
        profile.setEmail(userId + "@example.com");
        profile.setFullName("Public User");
        profile.setStatus(UserStatus.ACTIVE);
        return profile;
    }
}
