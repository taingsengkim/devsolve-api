package kh.edu.istad.ite.devsoleapi.feature.showcase;

import kh.edu.istad.ite.devsoleapi.feature.problem.tag.Tag;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.TagRepository;
import kh.edu.istad.ite.devsoleapi.feature.showcase.tag.ShowcaseTag;
import kh.edu.istad.ite.devsoleapi.feature.showcase.tag.ShowcaseTagId;
import kh.edu.istad.ite.devsoleapi.feature.showcase.tag.ShowcaseTagRepository;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "more like this" strip, run against a database rather than a mock.
 *
 * <p>Its ordering is the whole point of it and lives entirely in a scalar
 * subquery, which no unit test can see: a strip ordered by nothing in
 * particular still returns rows, and still looks plausible on the page.
 */
@SpringBootTest
@ActiveProfiles("test")
class ShowcaseRelatedQueryTest {

    @Autowired
    private ShowCasesRepository showCasesRepository;
    @Autowired
    private ShowcaseTagRepository showcaseTagRepository;
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private UserProfileRepository userProfileRepository;

    private ShowCases subject;
    private Tag redis;
    private Tag postgres;
    private Tag docker;

    @BeforeEach
    void seed() {
        showcaseTagRepository.deleteAll();
        showCasesRepository.deleteAll();
        tagRepository.deleteAll();
        userProfileRepository.deleteAll();

        UserProfile author = userProfileRepository.save(profile("dara"));

        redis = tagRepository.save(tag("Redis", "redis"));
        postgres = tagRepository.save(tag("Postgres", "postgres"));
        docker = tagRepository.save(tag("Docker", "docker"));

        subject = save("The showcase being read", ReviewStatus.APPROVED, author, 0);
        link(subject, redis, postgres, docker);
    }

    @Test
    void neighboursAreOrderedByHowManyTagsTheyShare() {
        UserProfile author = userProfileRepository.findAll().getFirst();

        ShowCases twoTags = save("Two shared", ReviewStatus.APPROVED, author, 1);
        link(twoTags, redis, postgres);
        // More views than the two-tag match, to prove views break ties rather
        // than outranking the overlap.
        ShowCases oneTagPopular =
                save("One shared, popular", ReviewStatus.APPROVED, author, 900);
        link(oneTagPopular, redis);
        ShowCases oneTagQuiet =
                save("One shared, quiet", ReviewStatus.APPROVED, author, 3);
        link(oneTagQuiet, docker);

        List<String> titles = titlesOf(showCasesRepository.findRelatedByTags(
                subject.getId(),
                null,
                List.of(redis.getId(), postgres.getId(), docker.getId()),
                ReviewStatus.APPROVED,
                PageRequest.of(0, 6)
        ));

        assertEquals(
                List.of("Two shared", "One shared, popular", "One shared, quiet"),
                titles
        );
    }

    @Test
    void nothingUnpublishedDeletedOrUntaggedGetsIn() {
        UserProfile author = userProfileRepository.findAll().getFirst();

        ShowCases pending = save("Still pending", ReviewStatus.PENDING, author, 0);
        link(pending, redis);
        ShowCases rejected = save("Rejected", ReviewStatus.REJECTED, author, 0);
        link(rejected, redis);
        ShowCases removed = save("Soft deleted", ReviewStatus.APPROVED, author, 0);
        removed.setDeletedAt(LocalDateTime.now());
        showCasesRepository.save(removed);
        link(removed, redis);
        save("Shares nothing", ReviewStatus.APPROVED, author, 0);

        List<String> titles = titlesOf(showCasesRepository.findRelatedByTags(
                subject.getId(),
                null,
                List.of(redis.getId()),
                ReviewStatus.APPROVED,
                PageRequest.of(0, 6)
        ));

        assertTrue(titles.isEmpty(), "found " + titles);
    }

    @Test
    void theShowcaseBeingReadIsNeverItsOwnNeighbour() {
        List<String> titles = titlesOf(showCasesRepository.findRelatedByTags(
                subject.getId(),
                null,
                List.of(redis.getId()),
                ReviewStatus.APPROVED,
                PageRequest.of(0, 6)
        ));

        assertFalse(titles.contains("The showcase being read"));
    }

    /**
     * The fallback for a tagless showcase. Nothing here shares its category —
     * it has none — so this asserts the query runs and excludes rather than
     * matching; the branch that chooses it is covered in the service test.
     */
    @Test
    void theCategoryFallbackRunsAndMatchesNothingWithoutOne() {
        assertTrue(showCasesRepository.findRelatedByCategory(
                subject.getId(),
                null,
                ReviewStatus.APPROVED,
                PageRequest.of(0, 6)
        ).isEmpty());
    }

    private List<String> titlesOf(List<ShowCases> showcases) {
        return showcases.stream().map(ShowCases::getTitle).toList();
    }

    private ShowCases save(
            String title,
            ReviewStatus reviewStatus,
            UserProfile author,
            int viewCount
    ) {
        ShowCases showcase = new ShowCases();
        showcase.setAuthor(author);
        showcase.setTitle(title);
        showcase.setOverview("An overview of " + title);
        showcase.setReviewStatus(reviewStatus);
        showcase.setViewCount(viewCount);
        return showCasesRepository.save(showcase);
    }

    private void link(ShowCases showcase, Tag... tags) {
        for (Tag tag : tags) {
            showcaseTagRepository.save(ShowcaseTag.builder()
                    .id(new ShowcaseTagId(showcase.getId(), tag.getId()))
                    .showcase(showcase)
                    .tag(tag)
                    .build());
        }
    }

    private Tag tag(String name, String slug) {
        return Tag.builder().name(name).slug(slug).build();
    }

    private UserProfile profile(String username) {
        UserProfile profile = new UserProfile();
        profile.setId(UUID.randomUUID());
        profile.setEmail(username + "@example.test");
        profile.setUsername(username);
        profile.setFullName("Sok Dara");
        profile.setStatus(UserStatus.ACTIVE);
        return profile;
    }
}
