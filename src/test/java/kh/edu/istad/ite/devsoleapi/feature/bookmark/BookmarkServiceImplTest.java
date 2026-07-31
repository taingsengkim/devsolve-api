package kh.edu.istad.ite.devsoleapi.feature.bookmark;

import kh.edu.istad.ite.devsoleapi.feature.bookmark.dto.BookmarkResponse;
import kh.edu.istad.ite.devsoleapi.feature.bookmark.dto.BookmarkStatusResponse;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
class BookmarkServiceImplTest {

    @Mock
    private BookmarkRepository bookmarkRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private BookmarkTargetAccessService targetAccessService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bookmarkUsesAuthenticatedUserAndIdempotentInsert() {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UserProfile user = user(userId);
        BookmarkTarget target = new BookmarkTarget(
                "Secure REST APIs",
                "An authentication guide",
                null
        );
        Bookmark stored = bookmark(
                user,
                BookmarkType.PROBLEM,
                targetId
        );
        authenticate(userId);
        when(targetAccessService.requireBookmarkable(
                BookmarkType.PROBLEM,
                targetId
        )).thenReturn(target);
        when(userProfileRepository.findById(userId))
                .thenReturn(Optional.of(user));
        when(bookmarkRepository
                .findByUser_IdAndBookmarkableTypeAndBookmarkableId(
                        userId,
                        BookmarkType.PROBLEM,
                        targetId
                ))
                .thenReturn(Optional.of(stored));

        BookmarkResponse response = service().bookmark(
                BookmarkType.PROBLEM,
                targetId
        );

        assertEquals(stored.getId(), response.id());
        assertTrue(response.available());
        assertEquals("Secure REST APIs", response.targetTitle());
        verify(bookmarkRepository).insertIfAbsent(
                any(UUID.class),
                eq(userId),
                eq("problem"),
                eq(targetId)
        );
    }

    @Test
    void mineIsPrivateFilteredAndIncludesUnavailableTargets() {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Bookmark stored = bookmark(
                user(userId),
                BookmarkType.SHOWCASE,
                targetId
        );
        authenticate(userId);
        when(bookmarkRepository.findMine(
                eq(userId),
                eq(BookmarkType.SHOWCASE),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(stored)));
        when(targetAccessService.findBookmarkable(
                BookmarkType.SHOWCASE,
                targetId
        )).thenReturn(Optional.empty());

        Page<BookmarkResponse> result = service().getMine(
                BookmarkType.SHOWCASE,
                0,
                20
        );

        BookmarkResponse response = result.getContent().getFirst();
        assertFalse(response.available());
        assertNull(response.targetTitle());
    }

    @Test
    void statusIsScopedToCurrentUser() {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        authenticate(userId);
        when(targetAccessService.requireBookmarkable(
                BookmarkType.PROGRAM,
                targetId
        )).thenReturn(new BookmarkTarget("Program", null, null));
        when(bookmarkRepository
                .existsByUser_IdAndBookmarkableTypeAndBookmarkableId(
                        userId,
                        BookmarkType.PROGRAM,
                        targetId
                ))
                .thenReturn(true);

        BookmarkStatusResponse response = service().getStatus(
                BookmarkType.PROGRAM,
                targetId
        );

        assertTrue(response.bookmarked());
    }

    @Test
    void unbookmarkIsIdempotentAndUsesCurrentUser() {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        authenticate(userId);

        service().unbookmark(BookmarkType.SOLUTION, targetId);

        verify(bookmarkRepository)
                .deleteByUser_IdAndBookmarkableTypeAndBookmarkableId(
                        userId,
                        BookmarkType.SOLUTION,
                        targetId
                );
    }

    private BookmarkServiceImpl service() {
        return new BookmarkServiceImpl(
                bookmarkRepository,
                userProfileRepository,
                targetAccessService
        );
    }

    private Bookmark bookmark(
            UserProfile user,
            BookmarkType type,
            UUID targetId
    ) {
        return Bookmark.builder()
                .id(UUID.randomUUID())
                .user(user)
                .bookmarkableType(type)
                .bookmarkableId(targetId)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private UserProfile user(UUID userId) {
        UserProfile user = new UserProfile();
        user.setId(userId);
        user.setEmail(userId + "@example.com");
        user.setFullName("Test User");
        return user;
    }

    private void authenticate(UUID userId) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of())
        );
    }
}
