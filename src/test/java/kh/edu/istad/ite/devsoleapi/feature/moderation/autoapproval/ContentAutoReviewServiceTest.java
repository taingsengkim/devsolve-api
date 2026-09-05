package kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval;

import kh.edu.istad.ite.devsoleapi.common.exception.ResourceNotFoundException;
import kh.edu.istad.ite.devsoleapi.feature.moderation.autoapproval.dto.ContentAutoReviewResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * What an author reads back about their own pending post.
 */
@ExtendWith(MockitoExtension.class)
class ContentAutoReviewServiceTest {

    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID CONTENT = UUID.randomUUID();
    private static final String TITLE = "Race in the outbox poller";

    @Mock
    private ContentAutoReviewRepository reviewRepository;

    @InjectMocks
    private ContentAutoReviewService service;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /**
     * The reason and the whole sentence, both. A client that wants to render one
     * paragraph shows {@code message}; one that wants to lay the model's own
     * words out separately has {@code reason}.
     */
    @Test
    void aHeldPostExplainsItselfToItsAuthor() {
        authenticate(AUTHOR);
        stub(held(
                AutoApprovalTarget.PROBLEM,
                AutoApprovalHold.UNCLEAR,
                "Too little detail to tell what this is about"
        ));

        ContentAutoReviewResponse response = service.findOne(
                AutoApprovalTarget.PROBLEM,
                CONTENT
        );

        assertEquals(AutoApprovalStatus.HELD, response.status());
        assertEquals(AutoApprovalHold.UNCLEAR, response.hold());
        assertEquals(
                "Too little detail to tell what this is about",
                response.reason()
        );
        assertTrue(response.message().contains(TITLE));
        assertTrue(response.message().contains("fuller description"));
        assertTrue(response.message()
                .contains("Too little detail to tell what this is about"));
    }

    /**
     * The outcome an author is never notified about is the one this endpoint
     * exists for: it says plainly that no automatic check stands behind the
     * wait, rather than implying a verdict nobody reached.
     */
    @Test
    void aPostTheCheckNeverRanOnSaysSo() {
        authenticate(AUTHOR);
        stub(held(
                AutoApprovalTarget.SHOWCASE,
                AutoApprovalHold.NOT_CHECKED,
                "Auto-approval is off"
        ));

        ContentAutoReviewResponse response = service.findOne(
                AutoApprovalTarget.SHOWCASE,
                CONTENT
        );

        assertEquals(AutoApprovalStatus.NOT_CHECKED, response.status());
        assertEquals(AutoApprovalHold.NOT_CHECKED, response.hold());
        assertTrue(response.message().contains("was not checked automatically"));
        assertTrue(response.message().contains("showcase"));
    }

    @Test
    void anApprovedPostReadsAsPublished() {
        authenticate(AUTHOR);
        ContentAutoReview review = review(AutoApprovalTarget.PROBLEM);
        review.setApproved(true);
        review.setHold(null);
        review.setReason("Safe and on topic");
        stub(review);

        ContentAutoReviewResponse response = service.findOne(
                AutoApprovalTarget.PROBLEM,
                CONTENT
        );

        assertEquals(AutoApprovalStatus.APPROVED, response.status());
        assertNull(response.hold());
        assertTrue(response.message().contains("is now live"));
    }

    /**
     * 404 rather than 403 for a stranger. Whether a given post was held, and for
     * which of the three reasons, is not something to learn by watching which
     * status code comes back.
     */
    @Test
    void somebodyElsesVerdictIsNotFound() {
        authenticate(UUID.randomUUID());
        stub(held(
                AutoApprovalTarget.PROBLEM,
                AutoApprovalHold.UNSAFE,
                "Reads as an attack"
        ));

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.findOne(AutoApprovalTarget.PROBLEM, CONTENT)
        );
    }

    @Test
    void aPostThatWasNeverCheckedHasNoVerdictToShow() {
        authenticate(AUTHOR);
        when(reviewRepository.findByTargetAndContentId(
                AutoApprovalTarget.PROBLEM,
                CONTENT
        )).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.findOne(AutoApprovalTarget.PROBLEM, CONTENT)
        );
    }

    private void stub(ContentAutoReview review) {
        when(reviewRepository.findByTargetAndContentId(
                review.getTarget(),
                review.getContentId()
        )).thenReturn(Optional.of(review));
    }

    private ContentAutoReview held(
            AutoApprovalTarget target,
            AutoApprovalHold hold,
            String reason
    ) {
        ContentAutoReview review = review(target);
        review.setApproved(false);
        review.setHold(hold);
        review.setReason(reason);
        return review;
    }

    private ContentAutoReview review(AutoApprovalTarget target) {
        return ContentAutoReview.builder()
                .id(UUID.randomUUID())
                .target(target)
                .contentId(CONTENT)
                .authorId(AUTHOR)
                .title(TITLE)
                .checkedAt(LocalDateTime.now())
                .build();
    }

    private void authenticate(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("sub", userId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(
                        jwt,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
    }
}
