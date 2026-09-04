package kh.edu.istad.ite.devsoleapi.feature.showcase;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.common.storage.ImageStorageService;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.CreateShowCasesRequest;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.SaveShowcaseDraftRequest;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShowcaseDraftServiceImplTest {

    private static final ValidatorFactory VALIDATOR_FACTORY =
            Validation.buildDefaultValidatorFactory();

    @Mock
    private ShowcaseDraftRepository showcaseDraftRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private ShowCasesService showCasesService;

    @Mock
    private ImageStorageService imageStorageService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * The point of posting through {@code showCasesService.create} rather than
     * writing a showcase directly: a draft is exempt from the posting rules
     * only while it stays a draft.
     */
    @Test
    void anIncompleteDraftIsRefusedAndNoShowcaseIsPosted() {
        UUID authorId = UUID.randomUUID();
        authenticate("USER", authorId);

        ShowcaseDraft draft = ShowcaseDraft.builder()
                .id(UUID.randomUUID())
                .repoUrl("https://github.com/example/thing")
                .build();
        when(showcaseDraftRepository.findByIdAndAuthor_Id(
                draft.getId(),
                authorId
        )).thenReturn(Optional.of(draft));

        ResponseStatusException thrown = assertThrows(
                ResponseStatusException.class,
                () -> service().submit(draft.getId())
        );

        assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
        String reason = String.valueOf(thrown.getReason());
        assertTrue(reason.contains("Title is required"), reason);
        assertTrue(reason.contains("Overview is required"), reason);

        verify(showCasesService, never()).create(any());
        verify(showcaseDraftRepository, never()).delete(any());
    }

    @Test
    void aCompleteDraftIsPostedWithItsFieldsAndThenDiscarded() {
        UUID authorId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID tagId = UUID.randomUUID();
        authenticate("USER", authorId);

        ShowcaseDraft draft = ShowcaseDraft.builder()
                .id(UUID.randomUUID())
                .categoryId(categoryId)
                .title("A search index that syncs in three seconds")
                .overview("How I moved the Meilisearch sync to a watermark.")
                .tagIds(List.of(tagId))
                .tags(List.of("meilisearch"))
                .build();
        when(showcaseDraftRepository.findByIdAndAuthor_Id(
                draft.getId(),
                authorId
        )).thenReturn(Optional.of(draft));

        service().submit(draft.getId());

        ArgumentCaptor<CreateShowCasesRequest> posted =
                ArgumentCaptor.forClass(CreateShowCasesRequest.class);
        verify(showCasesService).create(posted.capture());
        assertEquals(categoryId, posted.getValue().categoryId());
        assertEquals(
                "A search index that syncs in three seconds",
                posted.getValue().title()
        );
        // The draft holds ordered lists because jsonb has an order; the post
        // takes sets.
        assertEquals(Set.of(tagId), posted.getValue().tagIds());
        assertEquals(Set.of("meilisearch"), posted.getValue().tags());

        verify(showcaseDraftRepository).delete(draft);
    }

    /**
     * A whole-document replace, not a patch. A field the client stops sending
     * is cleared, so an autosave that drops a value means the author deleted
     * it.
     */
    @Test
    void savingReplacesTheWholeDraftRatherThanPatchingIt() {
        UUID authorId = UUID.randomUUID();
        authenticate("USER", authorId);

        ShowcaseDraft draft = ShowcaseDraft.builder()
                .id(UUID.randomUUID())
                // Only because saving maps the draft back to a response, which
                // reads the author's id.
                .author(mock(UserProfile.class))
                .title("Working title")
                .overview("Something I built")
                .liveUrl("https://example.com")
                .build();
        when(showcaseDraftRepository.findByIdAndAuthor_Id(
                draft.getId(),
                authorId
        )).thenReturn(Optional.of(draft));
        when(showcaseDraftRepository.save(draft)).thenReturn(draft);

        service().save(
                draft.getId(),
                new SaveShowcaseDraftRequest(
                        null,
                        "A better title",
                        null,
                        null, null, null, null,
                        null, null
                )
        );

        assertEquals("A better title", draft.getTitle());
        assertNull(draft.getOverview());
        assertNull(draft.getLiveUrl());
    }

    /**
     * Ownership is part of the query, so someone else's draft is reported as
     * missing rather than forbidden — nobody can use the difference to learn
     * that a draft exists.
     */
    @Test
    void aDraftBelongingToAnotherAuthorIsNotFound() {
        UUID authorId = UUID.randomUUID();
        UUID draftId = UUID.randomUUID();
        authenticate("USER", authorId);
        when(showcaseDraftRepository.findByIdAndAuthor_Id(draftId, authorId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service().findById(draftId)
        );
    }

    @Test
    void draftsAreCapped() {
        UUID authorId = UUID.randomUUID();
        authenticate("USER", authorId);
        when(showcaseDraftRepository.countByAuthor_Id(authorId))
                .thenReturn(20L);

        ResponseStatusException thrown = assertThrows(
                ResponseStatusException.class,
                () -> service().create(emptyRequest())
        );

        assertEquals(HttpStatus.CONFLICT, thrown.getStatusCode());
        verify(showcaseDraftRepository, never()).save(any());
    }

    /**
     * The image and the draft are thrown away together. Nothing else points at
     * it — the draft was never posted — so leaving it behind would strand a
     * file with no trace of what it was for.
     */
    @Test
    void discardingADraftAlsoDiscardsItsCover() {
        UUID authorId = UUID.randomUUID();
        authenticate("USER", authorId);

        ShowcaseDraft draft = ShowcaseDraft.builder()
                .id(UUID.randomUUID())
                .coverImageUrl("https://file.example/showcase-drafts/x/cover.png")
                .build();
        when(showcaseDraftRepository.findByIdAndAuthor_Id(
                draft.getId(),
                authorId
        )).thenReturn(Optional.of(draft));

        service().delete(draft.getId());

        verify(imageStorageService)
                .remove("https://file.example/showcase-drafts/x/cover.png");
        verify(showcaseDraftRepository).delete(draft);
    }

    /**
     * The opposite of discarding. The showcase now points at that object, so
     * cleaning it up here would blank the cover of the post just created.
     */
    @Test
    void postingADraftKeepsItsCover() {
        UUID authorId = UUID.randomUUID();
        authenticate("USER", authorId);

        ShowcaseDraft draft = ShowcaseDraft.builder()
                .id(UUID.randomUUID())
                .categoryId(UUID.randomUUID())
                .title("Something worth reading")
                .overview("With enough detail to judge.")
                .coverImageUrl("https://file.example/showcase-drafts/x/cover.png")
                .build();
        when(showcaseDraftRepository.findByIdAndAuthor_Id(
                draft.getId(),
                authorId
        )).thenReturn(Optional.of(draft));

        service().submit(draft.getId());

        verify(imageStorageService, never()).remove(any());
        verify(showcaseDraftRepository).delete(draft);
    }

    /**
     * Autosave sends the whole document. A client that forgets to echo the
     * current cover back must not thereby destroy the file — clearing the field
     * is not the same act as deleting the image.
     */
    @Test
    void anAutosaveThatDropsTheCoverUrlDoesNotDeleteTheFile() {
        UUID authorId = UUID.randomUUID();
        authenticate("USER", authorId);

        ShowcaseDraft draft = ShowcaseDraft.builder()
                .id(UUID.randomUUID())
                .author(mock(UserProfile.class))
                .coverImageUrl("https://file.example/showcase-drafts/x/cover.png")
                .build();
        when(showcaseDraftRepository.findByIdAndAuthor_Id(
                draft.getId(),
                authorId
        )).thenReturn(Optional.of(draft));
        when(showcaseDraftRepository.save(draft)).thenReturn(draft);

        service().save(draft.getId(), emptyRequest());

        verify(imageStorageService, never()).remove(any());
    }

    /**
     * Autosave fires before anything is typed, so an entirely empty save has to
     * be a valid one.
     */
    @Test
    void anEmptyDraftIsAcceptedBecauseAutosaveFiresBeforeAnythingIsTyped() {
        Validator validator = VALIDATOR_FACTORY.getValidator();
        assertTrue(validator.validate(emptyRequest()).isEmpty());
    }

    private SaveShowcaseDraftRequest emptyRequest() {
        return new SaveShowcaseDraftRequest(
                null, null, null, null, null, null, null, null, null
        );
    }

    private ShowcaseDraftServiceImpl service() {
        return new ShowcaseDraftServiceImpl(
                showcaseDraftRepository,
                userProfileRepository,
                showCasesService,
                imageStorageService,
                VALIDATOR_FACTORY.getValidator()
        );
    }

    private void authenticate(String role, UUID userId) {
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
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                )
        );
    }
}
