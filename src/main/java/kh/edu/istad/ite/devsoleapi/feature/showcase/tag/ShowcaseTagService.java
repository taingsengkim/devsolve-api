package kh.edu.istad.ite.devsoleapi.feature.showcase.tag;

import kh.edu.istad.ite.devsoleapi.feature.problem.tag.Tag;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.TagRepository;
import kh.edu.istad.ite.devsoleapi.feature.problem.tag.TagResolver;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowCases;
import kh.edu.istad.ite.devsoleapi.feature.showcase.ShowcaseRevision;
import kh.edu.istad.ite.devsoleapi.feature.showcase.dto.ShowcaseTagResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The tags of a showcase and of its unpublished revision.
 *
 * <p>Tags follow the same moderation path as the rest of a showcase: editing
 * them on a published showcase writes to the revision, and only approval
 * promotes them onto the live record. {@link Tag#getUsageCount()} therefore
 * only ever counts published links.
 */
@Service
@RequiredArgsConstructor
public class ShowcaseTagService {

    private static final String OWNER_LABEL = "showcase";

    private final ShowcaseTagRepository showcaseTagRepository;
    private final ShowcaseRevisionTagRepository showcaseRevisionTagRepository;
    private final TagRepository tagRepository;
    private final TagResolver tagResolver;

    @Transactional(readOnly = true)
    public List<ShowcaseTagResponse> tagsOfShowcase(UUID showcaseId) {
        return toResponses(
                showcaseTagRepository.findAllByShowcaseId(showcaseId)
                        .stream()
                        .map(ShowcaseTag::getTag)
                        .toList()
        );
    }

    /**
     * @return the tags of every requested showcase, keyed by showcase ID;
     * showcases without tags are absent
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<ShowcaseTagResponse>> tagsOfShowcases(
            Collection<UUID> showcaseIds
    ) {
        if (showcaseIds.isEmpty()) {
            return Map.of();
        }
        return showcaseTagRepository.findAllByShowcaseIdIn(showcaseIds)
                .stream()
                .collect(Collectors.groupingBy(
                        link -> link.getId().getShowcaseId(),
                        Collectors.mapping(
                                ShowcaseTag::getTag,
                                Collectors.collectingAndThen(
                                        Collectors.toList(),
                                        this::toResponses
                                )
                        )
                ));
    }

    @Transactional(readOnly = true)
    public List<ShowcaseTagResponse> tagsOfRevision(UUID revisionId) {
        return toResponses(
                showcaseRevisionTagRepository.findAllByRevisionId(revisionId)
                        .stream()
                        .map(ShowcaseRevisionTag::getTag)
                        .toList()
        );
    }

    /**
     * @return the tags of every requested revision, keyed by revision ID;
     * revisions without tags are absent
     */
    @Transactional(readOnly = true)
    public Map<UUID, List<ShowcaseTagResponse>> tagsOfRevisions(
            Collection<UUID> revisionIds
    ) {
        if (revisionIds.isEmpty()) {
            return Map.of();
        }
        return showcaseRevisionTagRepository.findAllByRevisionIdIn(revisionIds)
                .stream()
                .collect(Collectors.groupingBy(
                        link -> link.getId().getRevisionId(),
                        Collectors.mapping(
                                ShowcaseRevisionTag::getTag,
                                Collectors.collectingAndThen(
                                        Collectors.toList(),
                                        this::toResponses
                                )
                        )
                ));
    }

    @Transactional
    public List<ShowcaseTagResponse> replaceShowcaseTags(
            ShowCases showcase,
            Set<UUID> tagIds,
            Set<String> tagNames
    ) {
        return applyToShowcase(
                showcase,
                tagResolver.resolve(tagIds, tagNames, OWNER_LABEL)
        );
    }

    @Transactional
    public List<ShowcaseTagResponse> replaceRevisionTags(
            ShowcaseRevision revision,
            Set<UUID> tagIds,
            Set<String> tagNames
    ) {
        return applyToRevision(
                revision,
                tagResolver.resolve(tagIds, tagNames, OWNER_LABEL).values()
        );
    }

    /**
     * Copies the published tags onto a freshly created revision so an author
     * who edits something else does not silently drop them.
     */
    @Transactional
    public void snapshotTags(ShowCases showcase, ShowcaseRevision revision) {
        applyToRevision(
                revision,
                showcaseTagRepository.findAllByShowcaseId(showcase.getId())
                        .stream()
                        .map(ShowcaseTag::getTag)
                        .toList()
        );
    }

    /** Publishes the reviewed tags of {@code revision} onto {@code showcase}. */
    @Transactional
    public List<ShowcaseTagResponse> promoteTags(
            ShowCases showcase,
            ShowcaseRevision revision
    ) {
        Map<UUID, Tag> desired = new LinkedHashMap<>();
        showcaseRevisionTagRepository.findAllByRevisionId(revision.getId())
                .forEach(link -> desired.put(
                        link.getTag().getId(),
                        link.getTag()
                ));
        return applyToShowcase(showcase, desired);
    }

    @Transactional
    public void deleteRevisionTags(UUID revisionId) {
        showcaseRevisionTagRepository.deleteAllByRevisionId(revisionId);
    }

    /** Drops every published tag link, releasing the usage counts it held. */
    @Transactional
    public void deleteShowcaseTags(ShowCases showcase) {
        applyToShowcase(showcase, Map.of());
    }

    private List<ShowcaseTagResponse> applyToShowcase(
            ShowCases showcase,
            Map<UUID, Tag> desired
    ) {
        Map<UUID, ShowcaseTag> existingByTagId = showcaseTagRepository
                .findAllByShowcaseId(showcase.getId())
                .stream()
                .collect(Collectors.toMap(
                        link -> link.getId().getTagId(),
                        link -> link
                ));

        Set<UUID> removedIds = new HashSet<>(existingByTagId.keySet());
        removedIds.removeAll(desired.keySet());
        Set<UUID> addedIds = new HashSet<>(desired.keySet());
        addedIds.removeAll(existingByTagId.keySet());

        if (!removedIds.isEmpty()) {
            removedIds.forEach(tagId -> showcaseTagRepository.deleteById(
                    new ShowcaseTagId(showcase.getId(), tagId)
            ));
            showcaseTagRepository.flush();
            tagRepository.decrementUsageCounts(removedIds);
        }

        if (!addedIds.isEmpty()) {
            showcaseTagRepository.saveAllAndFlush(
                    addedIds.stream()
                            .map(tagId -> ShowcaseTag.builder()
                                    .id(new ShowcaseTagId(
                                            showcase.getId(),
                                            tagId
                                    ))
                                    .showcase(showcase)
                                    .tag(desired.get(tagId))
                                    .build())
                            .toList()
            );
            tagRepository.incrementUsageCounts(addedIds);
        }

        return toResponses(desired.values());
    }

    /**
     * Revision tags carry no usage counts, so they are simply rewritten
     * rather than diffed.
     */
    private List<ShowcaseTagResponse> applyToRevision(
            ShowcaseRevision revision,
            Collection<Tag> desired
    ) {
        showcaseRevisionTagRepository.deleteAllByRevisionId(revision.getId());
        showcaseRevisionTagRepository.flush();

        if (!desired.isEmpty()) {
            showcaseRevisionTagRepository.saveAllAndFlush(
                    desired.stream()
                            .map(tag -> ShowcaseRevisionTag.builder()
                                    .id(new ShowcaseRevisionTagId(
                                            revision.getId(),
                                            tag.getId()
                                    ))
                                    .revision(revision)
                                    .tag(tag)
                                    .build())
                            .toList()
            );
        }
        return toResponses(desired);
    }

    private List<ShowcaseTagResponse> toResponses(Collection<Tag> tags) {
        return tags.stream()
                .sorted(Comparator.comparing(
                        tag -> tag.getName().toLowerCase(Locale.ROOT)
                ))
                .map(tag -> new ShowcaseTagResponse(
                        tag.getId(),
                        tag.getName(),
                        tag.getSlug()
                ))
                .toList();
    }
}
