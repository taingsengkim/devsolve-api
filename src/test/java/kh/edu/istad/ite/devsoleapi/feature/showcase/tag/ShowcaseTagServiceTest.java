package kh.edu.istad.ite.devsoleapi.feature.showcase.tag;

import kh.edu.istad.ite.devsoleapi.feature.problem.tag.Tag;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.TagRepository;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.TagResolver;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCases;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowcaseRevision;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseTagResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShowcaseTagServiceTest {

    @Mock
    private ShowcaseTagRepository showcaseTagRepository;

    @Mock
    private ShowcaseRevisionTagRepository showcaseRevisionTagRepository;

    @Mock
    private TagRepository tagRepository;

    private ShowcaseTagService service;

    @BeforeEach
    void setUp() {
        service = new ShowcaseTagService(
                showcaseTagRepository,
                showcaseRevisionTagRepository,
                tagRepository,
                new TagResolver(tagRepository)
        );
    }

    @Test
    void replacingShowcaseTagsOnlyTouchesTheDifference() {
        ShowCases showcase = showcase();
        Tag kept = tag("Docker", "docker");
        Tag added = tag("Kubernetes", "kubernetes");
        Tag removed = tag("Vagrant", "vagrant");

        when(showcaseTagRepository.findAllByShowcaseId(showcase.getId()))
                .thenReturn(List.of(
                        link(showcase, kept),
                        link(showcase, removed)
                ));
        when(tagRepository.findAllByIdIn(Set.of(kept.getId(), added.getId())))
                .thenReturn(List.of(kept, added));

        List<ShowcaseTagResponse> tags = service.replaceShowcaseTags(
                showcase,
                Set.of(kept.getId(), added.getId()),
                null
        );

        assertEquals(
                List.of("Docker", "Kubernetes"),
                tags.stream().map(ShowcaseTagResponse::name).toList()
        );
        verify(showcaseTagRepository).deleteById(
                new ShowcaseTagId(showcase.getId(), removed.getId())
        );
        verify(showcaseTagRepository, never()).deleteById(
                new ShowcaseTagId(showcase.getId(), kept.getId())
        );
        verify(tagRepository).decrementUsageCounts(Set.of(removed.getId()));
        verify(tagRepository).incrementUsageCounts(Set.of(added.getId()));

        ArgumentCaptor<List<ShowcaseTag>> savedCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(showcaseTagRepository).saveAllAndFlush(savedCaptor.capture());
        assertEquals(1, savedCaptor.getValue().size());
        assertEquals(
                added.getId(),
                savedCaptor.getValue().getFirst().getId().getTagId()
        );
    }

    @Test
    void tagNamesWithoutASlugMatchCreateTheTag() {
        ShowCases showcase = showcase();
        Tag created = tag("Server Actions", "server-actions");

        when(showcaseTagRepository.findAllByShowcaseId(showcase.getId()))
                .thenReturn(List.of());
        when(tagRepository.findBySlug("server-actions"))
                .thenReturn(Optional.empty());
        when(tagRepository.saveAndFlush(org.mockito.ArgumentMatchers
                .any(Tag.class)))
                .thenReturn(created);

        List<ShowcaseTagResponse> tags = service.replaceShowcaseTags(
                showcase,
                null,
                Set.of("  Server Actions  ")
        );

        assertEquals(List.of("Server Actions"), tags.stream()
                .map(ShowcaseTagResponse::name)
                .toList());
        verify(tagRepository).incrementUsageCounts(Set.of(created.getId()));
    }

    @Test
    void moreThanTenTagsIsRejected() {
        ShowCases showcase = showcase();
        Set<String> names = new java.util.LinkedHashSet<>();
        for (int index = 0; index < 11; index++) {
            names.add("tag-" + index);
        }

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.replaceShowcaseTags(showcase, null, names)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("at most 10 tags"));
        verify(showcaseTagRepository, never())
                .saveAllAndFlush(anyCollection());
    }

    @Test
    void revisionTagsCarryNoUsageCounts() {
        ShowcaseRevision revision = revision();
        Tag tag = tag("Docker", "docker");

        when(tagRepository.findAllByIdIn(Set.of(tag.getId())))
                .thenReturn(List.of(tag));

        service.replaceRevisionTags(revision, Set.of(tag.getId()), null);

        verify(showcaseRevisionTagRepository)
                .deleteAllByRevisionId(revision.getId());
        verify(showcaseRevisionTagRepository)
                .saveAllAndFlush(anyCollection());
        verify(tagRepository, never()).incrementUsageCounts(anyCollection());
        verify(tagRepository, never()).decrementUsageCounts(anyCollection());
    }

    @Test
    void approvingARevisionPublishesItsTags() {
        ShowcaseRevision revision = revision();
        ShowCases showcase = revision.getShowcase();
        Tag published = tag("Vagrant", "vagrant");
        Tag candidate = tag("Kubernetes", "kubernetes");

        when(showcaseRevisionTagRepository
                .findAllByRevisionId(revision.getId()))
                .thenReturn(List.of(revisionLink(revision, candidate)));
        when(showcaseTagRepository.findAllByShowcaseId(showcase.getId()))
                .thenReturn(List.of(link(showcase, published)));

        List<ShowcaseTagResponse> tags =
                service.promoteTags(showcase, revision);

        assertEquals(
                List.of("Kubernetes"),
                tags.stream().map(ShowcaseTagResponse::name).toList()
        );
        verify(showcaseTagRepository).deleteById(
                new ShowcaseTagId(showcase.getId(), published.getId())
        );
        verify(tagRepository).decrementUsageCounts(Set.of(published.getId()));
        verify(tagRepository).incrementUsageCounts(Set.of(candidate.getId()));
    }

    @Test
    void aNewRevisionStartsFromThePublishedTags() {
        ShowcaseRevision revision = revision();
        ShowCases showcase = revision.getShowcase();
        Tag published = tag("Docker", "docker");

        when(showcaseTagRepository.findAllByShowcaseId(showcase.getId()))
                .thenReturn(List.of(link(showcase, published)));

        service.snapshotTags(showcase, revision);

        ArgumentCaptor<List<ShowcaseRevisionTag>> savedCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(showcaseRevisionTagRepository)
                .saveAllAndFlush(savedCaptor.capture());
        assertEquals(
                published.getId(),
                savedCaptor.getValue().getFirst().getId().getTagId()
        );
    }

    @Test
    void hardDeletingAShowcaseReleasesItsUsageCounts() {
        ShowCases showcase = showcase();
        Tag tag = tag("Docker", "docker");

        when(showcaseTagRepository.findAllByShowcaseId(showcase.getId()))
                .thenReturn(List.of(link(showcase, tag)));

        service.deleteShowcaseTags(showcase);

        verify(showcaseTagRepository).deleteById(
                new ShowcaseTagId(showcase.getId(), tag.getId())
        );
        verify(tagRepository).decrementUsageCounts(Set.of(tag.getId()));
        verify(showcaseTagRepository, never())
                .saveAllAndFlush(anyCollection());
    }

    private ShowCases showcase() {
        ShowCases showcase = new ShowCases();
        showcase.setId(UUID.randomUUID());
        return showcase;
    }

    private ShowcaseRevision revision() {
        ShowcaseRevision revision = new ShowcaseRevision();
        revision.setId(UUID.randomUUID());
        revision.setShowcase(showcase());
        return revision;
    }

    private Tag tag(String name, String slug) {
        return Tag.builder()
                .id(UUID.randomUUID())
                .name(name)
                .slug(slug)
                .build();
    }

    private ShowcaseTag link(ShowCases showcase, Tag tag) {
        return ShowcaseTag.builder()
                .id(new ShowcaseTagId(showcase.getId(), tag.getId()))
                .showcase(showcase)
                .tag(tag)
                .build();
    }

    private ShowcaseRevisionTag revisionLink(
            ShowcaseRevision revision,
            Tag tag
    ) {
        return ShowcaseRevisionTag.builder()
                .id(new ShowcaseRevisionTagId(revision.getId(), tag.getId()))
                .revision(revision)
                .tag(tag)
                .build();
    }
}
