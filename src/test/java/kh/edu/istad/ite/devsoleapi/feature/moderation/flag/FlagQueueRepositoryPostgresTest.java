package kh.edu.istad.ite.devsoleapi.feature.moderation.flag;

import kh.edu.istad.ite.devsoleapi.feature.comments.Comment;
import kh.edu.istad.ite.devsoleapi.feature.comments.CommentRepository;
import kh.edu.istad.ite.devsoleapi.feature.comments.enums.CommentableType;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlagQueueRepository.FlagGroupRow;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlagQueueRepository.FlagRow;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.FlagQueueRepository.FlagTally;
import kh.edu.istad.ite.devsoleapi.feature.moderation.flag.dto.FlagResponse;
import kh.edu.istad.ite.devsoleapi.feature.problem.Problem;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ReviewStatus;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCases;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCasesRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The moderation queue against a real Postgres.
 *
 * <p>Nothing about these queries can fail in a mocked test. {@code LATERAL},
 * {@code FILTER}, {@code string_agg} and the five-branch union only exist in
 * the database; a quoted alias that stops matching its projection getter fails
 * at runtime with no compiler to catch it; and the union's branches have to
 * agree on the type of every column, which is decided by Postgres and by
 * nothing in Java.
 *
 * <p>One run validates more than it asserts: Postgres parses the whole
 * statement, so a column renamed out from under any of the five branches fails
 * every test here, including the ones about the other four.
 *
 * <p>What gets the closest look is what the queue shows when the content is
 * gone. That is the case the old per-row fetch got wrong — a 404 and a blank
 * box, exactly where a moderator is asking what they just removed.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=create"
})
class FlagQueueRepositoryPostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private FlagQueueRepository flagQueueRepository;
    @Autowired
    private FlagRowAssembler flagRowAssembler;
    @Autowired
    private ContentFlagRepository contentFlagRepository;
    @Autowired
    private ProblemRepository problemRepository;
    @Autowired
    private ShowCasesRepository showCasesRepository;
    @Autowired
    private CommentRepository commentRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;

    @Test
    @Transactional
    void aProblemFlagCarriesTheProblemItReports() {
        UserProfile author = persistedProfile("Spider Kim");
        UserProfile reporter = persistedProfile("Taing Sengkim");
        Problem problem = persistedProblem(
                author,
                "Spring Boot Flyway Configuration",
                "I have a problem with springboot flyway   when running\n\n"
                        + "migrations on PostgreSQL.",
                ProblemStatus.PUBLISHED
        );
        flag(reporter, FlaggableType.PROBLEM, problem.getId(), FlagReason.SPAM);

        FlagResponse response = onlyResponse(search(null, null));

        assertEquals(
                "Spring Boot Flyway Configuration",
                response.target().title()
        );
        // Whitespace collapsed: the sources are markdown, and a snippet that
        // opens on a blank line renders as an empty card.
        assertEquals(
                "I have a problem with springboot flyway when running "
                        + "migrations on PostgreSQL.",
                response.target().snippet()
        );
        assertEquals(author.getId(), response.target().authorId());
        assertEquals("Spider Kim", response.target().authorName());
        assertEquals(
                FlagTargetStatus.PUBLISHED,
                response.target().contentStatus()
        );
        assertEquals(
                "/community/problems/" + problem.getId(),
                response.target().directUrl()
        );
        assertNotNull(response.target().createdAt());

        assertEquals(reporter.getId(), response.reporter().id());
        assertEquals("Taing Sengkim", response.reporter().name());
        assertEquals(1L, response.reportCountOnTarget());
    }

    /**
     * A showcase waiting on review is not published, and a moderator deciding
     * whether to act needs to know that before they act.
     */
    @Test
    @Transactional
    void aPendingShowcaseReadsAsPendingRatherThanLive() {
        UserProfile author = persistedProfile("Showcase Author");
        ShowCases showcase = persistedShowcase(author, ReviewStatus.PENDING);
        flag(
                persistedProfile("Reporter"),
                FlaggableType.SHOWCASE,
                showcase.getId(),
                FlagReason.OFF_TOPIC
        );

        FlagResponse response = onlyResponse(search(null, null));

        assertEquals(
                FlagTargetStatus.PENDING,
                response.target().contentStatus()
        );
        assertEquals("A build log", response.target().title());
        assertEquals("cover.png", response.target().thumbnailUrl());
        assertEquals(
                "/community/showcases/" + showcase.getId(),
                response.target().directUrl()
        );
    }

    /**
     * A comment has no page of its own, so the queue links to whatever it hangs
     * off with the comment anchored. A tombstoned one still says what it was.
     */
    @Test
    @Transactional
    void aRemovedCommentPointsAtThePageItLivesOn() {
        UserProfile author = persistedProfile("Commenter");
        Problem problem = persistedProblem(
                author,
                "Host problem",
                "Body",
                ProblemStatus.PUBLISHED
        );
        Comment comment = new Comment();
        comment.setCommentableType(CommentableType.PROBLEM);
        comment.setCommentableId(problem.getId());
        comment.setAuthorId(author.getId());
        comment.setContent("Buy followers at example.test");
        comment.setInternal(false);
        comment.setRemovedAt(java.time.LocalDateTime.now());
        Comment saved = commentRepository.saveAndFlush(comment);

        flag(
                persistedProfile("Reporter"),
                FlaggableType.COMMENT,
                saved.getId(),
                FlagReason.SPAM
        );

        FlagResponse response = onlyResponse(search(null, null));

        // No title of its own, and the queue must not invent one.
        assertNull(response.target().title());
        assertEquals(
                "Buy followers at example.test",
                response.target().snippet()
        );
        assertEquals(
                FlagTargetStatus.REMOVED,
                response.target().contentStatus()
        );
        assertEquals(
                "/community/problems/" + problem.getId()
                        + "#comment-" + saved.getId(),
                response.target().directUrl()
        );
    }

    /**
     * The case the per-row fetch got wrong. A problem taken down is hidden from
     * every mapped query by {@code @SQLRestriction}, so the old queue showed
     * the moderator who had just removed it an empty box.
     */
    @Test
    @Transactional
    void contentTakenDownIsStillLegibleInTheQueue() {
        UserProfile author = persistedProfile("Author");
        Problem problem = persistedProblem(
                author,
                "Removed but still reported",
                "Body",
                ProblemStatus.PUBLISHED
        );
        problem.setDeletedAt(Instant.now());
        problemRepository.saveAndFlush(problem);

        flag(
                persistedProfile("Reporter"),
                FlaggableType.PROBLEM,
                problem.getId(),
                FlagReason.OFFENSIVE
        );

        FlagResponse response = onlyResponse(search(null, null));

        assertEquals(
                "Removed but still reported",
                response.target().title()
        );
        assertEquals("Author", response.target().authorName());
        assertEquals(
                FlagTargetStatus.DELETED,
                response.target().contentStatus()
        );
        // Nowhere to send them. A link to a page that 404s is worse than none.
        assertNull(response.target().directUrl());
    }

    /**
     * Nothing is left of a hard-deleted row, but the report is still there and
     * still has to be closed. An absent target object would read as a bug.
     */
    @Test
    @Transactional
    void aFlagWhoseContentIsGoneEntirelyIsNotABlankRow() {
        flag(
                persistedProfile("Reporter"),
                FlaggableType.PROBLEM,
                UUID.randomUUID(),
                FlagReason.OTHER
        );

        FlagResponse response = onlyResponse(search(null, null));

        assertNotNull(response.target());
        assertEquals(
                FlagTargetStatus.DELETED,
                response.target().contentStatus()
        );
        assertNull(response.target().title());
        assertNull(response.target().createdAt());
        assertNull(response.target().directUrl());
    }

    /**
     * The count is of every report on the content, whatever the queue was
     * filtered to. Counting only what the filter admitted would tell a
     * moderator "1 report" about a post four other people have reported.
     */
    @Test
    @Transactional
    void siblingReportsAreCountedAcrossEveryStatus() {
        UserProfile author = persistedProfile("Author");
        Problem problem = persistedProblem(
                author,
                "Reported three times",
                "Body",
                ProblemStatus.PUBLISHED
        );
        flag(
                persistedProfile("First"),
                FlaggableType.PROBLEM,
                problem.getId(),
                FlagReason.SPAM
        );
        flag(
                persistedProfile("Second"),
                FlaggableType.PROBLEM,
                problem.getId(),
                FlagReason.OFF_TOPIC
        );
        ContentFlag closed = flag(
                persistedProfile("Third"),
                FlaggableType.PROBLEM,
                problem.getId(),
                FlagReason.SPAM
        );
        closed.setStatus(FlagStatus.DISMISSED);
        contentFlagRepository.saveAndFlush(closed);

        FlagResponse response = flagRowAssembler.toResponse(
                search(FlagStatus.PENDING, null).getContent().getFirst(),
                true
        );

        assertEquals(3L, response.reportCountOnTarget());
        assertEquals(2L, response.pendingReportCountOnTarget());
        assertEquals(
                List.of(FlagReason.SPAM, FlagReason.OFF_TOPIC),
                response.allReasons()
        );
    }

    /**
     * Search reaches the reported writing and the person who wrote it, both of
     * which live in another table entirely — the whole reason the listing joins
     * content at all. It reaches the reporter too.
     *
     * <p>The three people are deliberately distinct. One name matching both the
     * author of one report and the reporter of another would match two rows,
     * and a test that could not tell those apart would pass just as happily
     * with half the predicate deleted.
     */
    @Test
    @Transactional
    void searchMatchesTheReportedWritingItsAuthorAndItsReporter() {
        UserProfile author = persistedProfile("Ada Lovelace");
        UserProfile bystander = persistedProfile("Grace Hopper");
        UserProfile reporter = persistedProfile("Alan Turing");
        UserProfile otherReporter = persistedProfile("Katherine Johnson");
        Problem wanted = persistedProblem(
                author,
                "Flyway migration fails",
                "Body",
                ProblemStatus.PUBLISHED
        );
        Problem ignored = persistedProblem(
                bystander,
                "Unrelated question",
                "Nothing to do with it",
                ProblemStatus.PUBLISHED
        );
        flag(reporter, FlaggableType.PROBLEM, wanted.getId(), FlagReason.SPAM);
        flag(
                otherReporter,
                FlaggableType.PROBLEM,
                ignored.getId(),
                FlagReason.SPAM
        );

        // The reported writing.
        assertEquals(
                wanted.getId(),
                onlyRow(search(null, "%flyway%")).getFlaggableId()
        );
        // Who wrote it — a column of another table entirely.
        assertEquals(
                wanted.getId(),
                onlyRow(search(null, "%lovelace%")).getFlaggableId()
        );
        // Who reported it.
        assertEquals(
                wanted.getId(),
                onlyRow(search(null, "%turing%")).getFlaggableId()
        );
        assertEquals(0, search(null, "%nothing at all%").getTotalElements());
    }

    /**
     * Five people reporting one post is a stronger signal than any single
     * report, and it is the only thing resembling urgency the platform records.
     */
    @Test
    @Transactional
    void mostReportedPutsTheBusiestContentFirst() {
        UserProfile author = persistedProfile("Author");
        Problem quiet = persistedProblem(
                author,
                "Reported once",
                "Body",
                ProblemStatus.PUBLISHED
        );
        Problem busy = persistedProblem(
                author,
                "Reported twice",
                "Body",
                ProblemStatus.PUBLISHED
        );
        flag(
                persistedProfile("First Reporter"),
                FlaggableType.PROBLEM,
                quiet.getId(),
                FlagReason.SPAM
        );
        flag(
                persistedProfile("Second Reporter"),
                FlaggableType.PROBLEM,
                busy.getId(),
                FlagReason.SPAM
        );
        flag(
                persistedProfile("Third Reporter"),
                FlaggableType.PROBLEM,
                busy.getId(),
                FlagReason.SPAM
        );

        List<FlagRow> rows = flagQueueRepository.search(
                null,
                null,
                null,
                null,
                null,
                FlagQueueSort.MOST_REPORTED.name(),
                PageRequest.of(0, 20, Sort.unsorted())
        ).getContent();

        assertEquals(3, rows.size());
        assertEquals(busy.getId(), rows.get(0).getFlaggableId());
        assertEquals(busy.getId(), rows.get(1).getFlaggableId());
        assertEquals(quiet.getId(), rows.get(2).getFlaggableId());
    }

    /**
     * One card per reported thing. Ten reports about one post are one decision,
     * and the counts on the card are of all ten however the queue was filtered.
     */
    @Test
    @Transactional
    void theGroupedQueueCollapsesOneTargetIntoOneCard() {
        UserProfile author = persistedProfile("Author");
        Problem problem = persistedProblem(
                author,
                "Reported by three people",
                "Body",
                ProblemStatus.PUBLISHED
        );
        flag(
                persistedProfile("First Reporter"),
                FlaggableType.PROBLEM,
                problem.getId(),
                FlagReason.SPAM
        );
        flag(
                persistedProfile("Second Reporter"),
                FlaggableType.PROBLEM,
                problem.getId(),
                FlagReason.OFF_TOPIC
        );
        ContentFlag automated = new ContentFlag();
        automated.setSource(FlagSource.AUTOMATED);
        automated.setFlaggableType(FlaggableType.PROBLEM);
        automated.setFlaggableId(problem.getId());
        automated.setReason(FlagReason.OFFENSIVE);
        automated.setStatus(FlagStatus.PENDING);
        contentFlagRepository.saveAndFlush(automated);

        Page<FlagGroupRow> groups = flagQueueRepository.searchGroups(
                FlagStatus.PENDING.name(),
                null,
                null,
                null,
                null,
                FlagQueueSort.MOST_REPORTED.name(),
                PageRequest.of(0, 20, Sort.unsorted())
        );

        assertEquals(1, groups.getTotalElements());
        var card = flagRowAssembler.toGroup(groups.getContent().getFirst());
        assertEquals(problem.getId(), card.flaggableId());
        assertEquals(FlaggableType.PROBLEM, card.flaggableType());
        assertEquals(3, card.reportCount());
        assertEquals(3, card.pendingCount());
        assertEquals(
                "Reported by three people",
                card.target().title()
        );
        assertTrue(card.automated());
        assertEquals(
                List.of(
                        FlagReason.SPAM,
                        FlagReason.OFFENSIVE,
                        FlagReason.OFF_TOPIC
                ),
                card.reasons()
        );
        assertNotNull(card.firstReportedAt());
        assertNotNull(card.latestFlagId());
    }

    /**
     * A reporter's own list must never widen to somebody else's reports, and
     * the filter is the same parameter the admin queue leaves null.
     */
    @Test
    @Transactional
    void filteringByReporterReturnsOnlyThatPersonsReports() {
        UserProfile mine = persistedProfile("Mine");
        UserProfile theirs = persistedProfile("Theirs");
        Problem problem = persistedProblem(
                mine,
                "A problem",
                "Body",
                ProblemStatus.PUBLISHED
        );
        flag(mine, FlaggableType.PROBLEM, problem.getId(), FlagReason.SPAM);
        flag(theirs, FlaggableType.PROBLEM, problem.getId(), FlagReason.SPAM);

        Page<FlagRow> page = flagQueueRepository.search(
                null,
                null,
                null,
                mine.getId().toString(),
                null,
                FlagQueueSort.NEWEST.name(),
                PageRequest.of(0, 20, Sort.unsorted())
        );

        assertEquals(1, page.getTotalElements());
        assertEquals(
                mine.getId(),
                page.getContent().getFirst().getReporterId()
        );
    }

    /** Every badge above the queue, from one pass over the table. */
    @Test
    @Transactional
    void theTallyCountsStatusReasonAndKindTogether() {
        UserProfile author = persistedProfile("Author");
        Problem problem = persistedProblem(
                author,
                "A problem",
                "Body",
                ProblemStatus.PUBLISHED
        );
        ShowCases showcase = persistedShowcase(author, ReviewStatus.APPROVED);
        flag(
                persistedProfile("First Reporter"),
                FlaggableType.PROBLEM,
                problem.getId(),
                FlagReason.SPAM
        );
        flag(
                persistedProfile("Second Reporter"),
                FlaggableType.SHOWCASE,
                showcase.getId(),
                FlagReason.SPAM
        );

        List<FlagTally> tallies = flagQueueRepository.tally();

        assertEquals(2, tallies.size());
        assertTrue(tallies.stream().allMatch(tally ->
                "PENDING".equals(tally.getStatus())
                        && "SPAM".equals(tally.getReason())
                        && tally.getTotal() == 1
        ));
        assertTrue(tallies.stream().anyMatch(tally ->
                "PROBLEM".equals(tally.getFlaggableType())
        ));
        assertTrue(tallies.stream().anyMatch(tally ->
                "SHOWCASE".equals(tally.getFlaggableType())
        ));
    }

    /** An automated flag has no reporter, and the join must not drop the row. */
    @Test
    @Transactional
    void anAutomatedFlagSurvivesHavingNoReporter() {
        UserProfile author = persistedProfile("Author");
        Problem problem = persistedProblem(
                author,
                "Filtered",
                "Body",
                ProblemStatus.PUBLISHED
        );
        ContentFlag automated = new ContentFlag();
        automated.setSource(FlagSource.AUTOMATED);
        automated.setFlaggableType(FlaggableType.PROBLEM);
        automated.setFlaggableId(problem.getId());
        automated.setReason(FlagReason.OFFENSIVE);
        automated.setStatus(FlagStatus.PENDING);
        contentFlagRepository.saveAndFlush(automated);

        FlagResponse response = onlyResponse(search(null, null));

        assertNull(response.reporter());
        assertEquals(FlagSource.AUTOMATED, response.source());
        assertEquals("Filtered", response.target().title());
        assertFalse(response.target().title().isBlank());
    }

    private Page<FlagRow> search(FlagStatus status, String search) {
        return flagQueueRepository.search(
                status == null ? null : status.name(),
                null,
                null,
                null,
                search,
                FlagQueueSort.NEWEST.name(),
                PageRequest.of(0, 20, Sort.unsorted())
        );
    }

    private FlagRow onlyRow(Page<FlagRow> page) {
        assertEquals(1, page.getTotalElements());
        return page.getContent().getFirst();
    }

    private FlagResponse onlyResponse(Page<FlagRow> page) {
        return flagRowAssembler.toResponse(onlyRow(page), true);
    }

    private ContentFlag flag(
            UserProfile reporter,
            FlaggableType type,
            UUID targetId,
            FlagReason reason
    ) {
        ContentFlag flag = new ContentFlag();
        flag.setReporter(reporter);
        flag.setSource(FlagSource.USER);
        flag.setFlaggableType(type);
        flag.setFlaggableId(targetId);
        flag.setReason(reason);
        flag.setStatus(FlagStatus.PENDING);
        return contentFlagRepository.saveAndFlush(flag);
    }

    private Problem persistedProblem(
            UserProfile author,
            String title,
            String description,
            ProblemStatus status
    ) {
        return problemRepository.saveAndFlush(
                Problem.builder()
                        .authorId(author.getId())
                        .categoryId(UUID.randomUUID())
                        .title(title)
                        .description(description)
                        .status(status)
                        .build()
        );
    }

    private ShowCases persistedShowcase(
            UserProfile author,
            ReviewStatus reviewStatus
    ) {
        ShowCases showcase = new ShowCases();
        showcase.setAuthor(author);
        showcase.setTitle("A build log");
        showcase.setOverview("How it was put together.");
        showcase.setCoverImageUrl("cover.png");
        showcase.setReviewStatus(reviewStatus);
        return showCasesRepository.saveAndFlush(showcase);
    }

    private UserProfile persistedProfile(String fullName) {
        String handle = "user" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12);
        UserProfile profile = new UserProfile();
        profile.setId(UUID.randomUUID());
        profile.setEmail(handle + "@example.test");
        profile.setUsername(handle);
        profile.setFullName(fullName);
        profile.setStatus(UserStatus.ACTIVE);
        return userProfileRepository.saveAndFlush(profile);
    }
}
