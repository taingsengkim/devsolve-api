package kh.edu.istad.ite.devsoleapi.feature.problem.tag;

import jakarta.persistence.EntityManager;
import kh.edu.istad.ite.devsoleapi.feature.problem.Problem;
import kh.edu.istad.ite.devsoleapi.feature.problem.ProblemRepository;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The catalogue query against a real database, for the same reason as
 * {@code ProblemRepositoryTest}: the ordering is a {@code case} expression and
 * two sort keys, none of which a mocked repository can get wrong.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TagRepositoryTest {

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private EntityManager entityManager;

    /**
     * The usage-count updates once ran with {@code clearAutomatically}, which
     * detached everything the caller was holding rather than just the tags. The
     * services link the tags and then render the problem they have just
     * written, so adding a tag returned a 500 from the response assembly while
     * the write itself had gone through.
     */
    @Test
    void updatingUsageCountsLeavesTheProblemBeingWrittenAttached() {
        Tag tag = saveTag("Java", "java", 0);
        UUID problemId = problemRepository.saveAndFlush(Problem.builder()
                .authorId(UUID.randomUUID())
                .categoryId(UUID.randomUUID())
                .title("A valid repository test title")
                .description("A valid repository test description")
                .status(ProblemStatus.DRAFT)
                .build()).getId();
        // Cleared and re-read so the collection below is a real uninitialized
        // proxy, which is the only state the detaching could be seen in.
        entityManager.clear();
        Problem problem = problemRepository.findById(problemId).orElseThrow();

        tagRepository.incrementUsageCounts(Set.of(tag.getId()));
        tagRepository.decrementUsageCounts(Set.of(tag.getId()));

        assertDoesNotThrow(
                () -> problem.getAcceptedSolutions().size(),
                "a usage-count update must not detach the problem being written"
        );
    }

    @Test
    void prefixMatchesRankAboveSubstringMatchesRegardlessOfUsage() {
        saveTag("JavaScript", "javascript", 1);
        saveTag("Nested Java", "nested-java", 500);

        assertEquals(
                List.of("javascript", "nested-java"),
                slugsOf(search("java")),
                "a prefix match belongs above a more popular substring match"
        );
    }

    @Test
    void withinTheSameRankTheMostUsedComesFirst() {
        saveTag("Spring Data", "spring-data", 3);
        saveTag("Spring Boot", "spring-boot", 90);

        assertEquals(List.of("spring-boot", "spring-data"), slugsOf(search("spring")));
    }

    @Test
    void anEmptyQueryListsEverythingByUsage() {
        saveTag("Rare", "rare", 1);
        saveTag("Common", "common", 200);

        assertEquals(List.of("common", "rare"), slugsOf(search("")));
    }

    @Test
    void nonMatchingQueryReturnsNothingRatherThanEverything() {
        saveTag("Docker", "docker", 5);

        assertTrue(search("kubernetes").isEmpty());
    }

    @Test
    void theLimitIsHonoured() {
        saveTag("Go", "go", 3);
        saveTag("Gradle", "gradle", 2);
        saveTag("GraphQL", "graphql", 1);

        assertEquals(
                List.of("go"),
                slugsOf(tagRepository.search("g", PageRequest.of(0, 1)))
        );
    }

    private List<Tag> search(String query) {
        return tagRepository.search(query, PageRequest.of(0, 20));
    }

    private static List<String> slugsOf(List<Tag> tags) {
        return tags.stream().map(Tag::getSlug).toList();
    }

    private Tag saveTag(String name, String slug, int usageCount) {
        return tagRepository.saveAndFlush(Tag.builder()
                .name(name)
                .slug(slug)
                .usageCount(usageCount)
                .build());
    }
}
