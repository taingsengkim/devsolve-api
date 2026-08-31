package kh.edu.istad.ite.devsoleapi.feature.problem.tag;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

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

    private void saveTag(String name, String slug, int usageCount) {
        tagRepository.saveAndFlush(Tag.builder()
                .name(name)
                .slug(slug)
                .usageCount(usageCount)
                .build());
    }
}
