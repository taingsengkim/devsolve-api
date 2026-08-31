package kh.edu.istad.ite.devsoleapi.feature.problem.tag;

import kh.edu.istad.ite.devsoleapi.feature.problem.tag.dto.TagResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

    @Mock
    private TagRepository tagRepository;

    private TagService service;

    @BeforeEach
    void setUp() {
        service = new TagService(tagRepository);
    }

    @Test
    void searchNormalizesTheQueryTheSameWayStoredSlugsAre() {
        stubEmptySearch();

        service.search("Node.JS", null);

        assertEquals("node-js", capturedQuery());
    }

    @Test
    void aBlankQueryListsEverything() {
        stubEmptySearch();

        service.search("   ", null);

        assertEquals("", capturedQuery());
    }

    @Test
    void aNullQueryListsEverything() {
        stubEmptySearch();

        service.search(null, null);

        assertEquals("", capturedQuery());
    }

    /**
     * The write path rejects punctuation-only tag names; a search box has no
     * business 400-ing over one, so it degrades to the unfiltered listing.
     */
    @Test
    void punctuationAloneIsNotAnError() {
        stubEmptySearch();

        service.search("!!!", null);

        assertEquals("", capturedQuery());
    }

    @Test
    void limitDefaultsAndIsClampedRatherThanRejected() {
        stubEmptySearch();

        service.search(null, null);
        assertEquals(TagService.DEFAULT_LIMIT, capturedPageable().getPageSize());

        service.search(null, 5_000);
        assertEquals(TagService.MAX_LIMIT, capturedPageable().getPageSize());

        service.search(null, 0);
        assertEquals(1, capturedPageable().getPageSize());
    }

    @Test
    void theResponseCarriesTheUsageCountThePickerRanksOn() {
        Tag tag = Tag.builder()
                .id(UUID.randomUUID())
                .name("Spring Boot")
                .slug("spring-boot")
                .usageCount(42)
                .build();
        when(tagRepository.search(anyString(), any(Pageable.class)))
                .thenReturn(List.of(tag));

        List<TagResponse> responses = service.search("spring", null);

        assertEquals(
                List.of(new TagResponse(
                        tag.getId(),
                        "Spring Boot",
                        "spring-boot",
                        42
                )),
                responses
        );
    }

    private void stubEmptySearch() {
        when(tagRepository.search(anyString(), any(Pageable.class)))
                .thenReturn(List.of());
    }

    private String capturedQuery() {
        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(tagRepository, atLeastOnce())
                .search(query.capture(), any(Pageable.class));
        return query.getValue();
    }

    private Pageable capturedPageable() {
        ArgumentCaptor<Pageable> pageable =
                ArgumentCaptor.forClass(Pageable.class);
        verify(tagRepository, atLeastOnce())
                .search(anyString(), pageable.capture());
        return pageable.getValue();
    }
}
