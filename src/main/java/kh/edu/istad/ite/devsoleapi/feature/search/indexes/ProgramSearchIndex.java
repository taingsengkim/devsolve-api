package kh.edu.istad.ite.devsoleapi.feature.search.indexes;

import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
import kh.edu.istad.ite.devsoleapi.feature.program.Program;
import kh.edu.istad.ite.devsoleapi.feature.program.ProgramRepository;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.ProgramState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.SubmissionState;
import kh.edu.istad.ite.devsoleapi.feature.program.enums.Visibility;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAsset;
import kh.edu.istad.ite.devsoleapi.feature.program.program_asset.ProgramAssetRepository;
import kh.edu.istad.ite.devsoleapi.feature.search.DocumentBatch;
import kh.edu.istad.ite.devsoleapi.feature.search.IndexSettings;
import kh.edu.istad.ite.devsoleapi.feature.search.SearchDocument;
import kh.edu.istad.ite.devsoleapi.feature.search.SearchDocuments;
import kh.edu.istad.ite.devsoleapi.feature.search.SearchIndexDefinition;
import kh.edu.istad.ite.devsoleapi.feature.search.SyncCursor;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Public bug bounty programs.
 *
 * <p>The one index where the searchable text reaches past the row itself: an
 * in-scope asset's identifier is indexed alongside the program's own name and
 * description, because "who runs a program on this domain" is the question
 * researchers actually arrive with, and it is unanswerable from the programs
 * table alone.
 */
/*
 * The order on each definition is the order results are grouped in, and it is
 * a product decision rather than an implementation detail: a query that hits a
 * program and a person should lead with the program.
 */
@Component
@Order(10)
@RequiredArgsConstructor
public class ProgramSearchIndex implements SearchIndexDefinition {

    public static final String NAME = "programs";

    private final ProgramRepository programRepository;
    private final OrganizationRepository organizationRepository;
    private final ProgramAssetRepository programAssetRepository;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public IndexSettings settings() {
        return new IndexSettings(
                List.of(
                        SearchDocuments.TITLE,
                        SearchDocuments.SUBTITLE,
                        "organizationName",
                        "assetIdentifiers",
                        SearchDocuments.BODY
                ),
                List.of(
                        "organizationId",
                        "organizationSlug",
                        "industry",
                        "country",
                        "engagementType",
                        "offersBounties",
                        "minimumBounty",
                        "maximumBounty",
                        "assetTypes",
                        "maxSeverities"
                ),
                List.of(
                        SearchDocuments.UPDATED_AT,
                        SearchDocuments.CREATED_AT,
                        "publishedAt",
                        "viewCount",
                        "minimumBounty",
                        "maximumBounty"
                ),
                // Popularity as the last word rather than the first: it settles
                // ties between programs the text ranking already found equally
                // relevant, and never promotes a weaker match.
                IndexSettings.rankedBy("viewCount:desc")
        );
    }

    /**
     * Repeatable read, which no other index here needs.
     *
     * <p>This one reads organizations in a second statement and folds their
     * timestamps into the key it pages on. Under read committed those two
     * statements get separate snapshots, so an organization committing an
     * update in between would give a document a {@code changedAt} newer than
     * the value the first statement ordered by — and the cursor built from it
     * would start the next page past rows that were never read. One snapshot
     * for both statements is what makes the key the query sorted on and the key
     * the document carries the same number. The transaction only reads, so it
     * cannot be aborted for conflicting with anything.
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DocumentBatch loadChangedSince(SyncCursor cursor, int size) {
        Slice<Program> programs = programRepository.findChangedSince(
                cursor.changedAt(),
                cursor.id(),
                PageRequest.of(0, size)
        );
        if (programs.isEmpty()) {
            return DocumentBatch.empty();
        }

        Map<UUID, Organization> organizations = organizationRepository
                .findAllById(programs.stream()
                        .map(Program::getOrganizationId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        Organization::getId,
                        organization -> organization
                ));

        Map<UUID, List<ProgramAsset>> assets = programAssetRepository
                .findInScopeByProgramIds(programs.stream()
                        .map(Program::getId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.groupingBy(
                        asset -> asset.getProgram().getId()
                ));

        List<SearchDocument> documents = programs.stream()
                .map(program -> toDocument(
                        program,
                        organizations.get(program.getOrganizationId()),
                        assets.getOrDefault(program.getId(), List.of())
                ))
                .toList();

        // The cursor comes off the document rather than the program row,
        // because the key this index pages on is the later of the program's
        // timestamp and its organization's — which is the one the document
        // carries. See ProgramRepository#findChangedSince.
        return DocumentBatch.of(documents, programs.hasNext());
    }

    private SearchDocument toDocument(
            Program program,
            Organization organization,
            List<ProgramAsset> inScopeAssets
    ) {
        // The organization's timestamp counts as the program's for watermark
        // purposes: this row may well be here only because that one moved.
        LocalDateTime changedAt = latest(
                program.getUpdatedAt(),
                organization == null ? null : organization.getUpdatedAt()
        );

        if (!isPubliclyVisible(program, organization)) {
            return SearchDocument.removed(program.getId(), changedAt);
        }

        Map<String, Object> document = SearchDocuments.envelope(
                NAME,
                program.getId(),
                program.getName(),
                program.getHandle(),
                program.getDescription(),
                organization.getLogoUrl(),
                program.getHandle()
        );

        document.put("organizationId", organization.getId().toString());
        document.put("organizationName", organization.getName());
        document.put("organizationSlug", organization.getSlug());
        document.put("industry", SearchDocuments.nameOf(organization.getIndustry()));
        document.put("country", organization.getCountry());
        document.put(
                "engagementType",
                SearchDocuments.nameOf(program.getEngagementType())
        );
        document.put("offersBounties", Boolean.TRUE.equals(program.getOffersBounties()));
        document.put("minimumBounty", program.getMinimumBounty());
        document.put("maximumBounty", program.getMaximumBounty());
        document.put("assetTypes", distinct(inScopeAssets.stream()
                .map(asset -> SearchDocuments.nameOf(asset.getAssetType()))));
        document.put("maxSeverities", distinct(inScopeAssets.stream()
                .map(asset -> SearchDocuments.nameOf(asset.getMaxSeverity()))));
        document.put("assetIdentifiers", distinct(inScopeAssets.stream()
                .map(ProgramAsset::getIdentifier)));
        document.put("viewCount", program.getViewCount());
        document.put(
                "publishedAt",
                SearchDocuments.epochSeconds(program.getPublishedAt())
        );
        document.put(
                SearchDocuments.CREATED_AT,
                SearchDocuments.epochSeconds(program.getCreatedAt())
        );
        document.put(
                SearchDocuments.UPDATED_AT,
                SearchDocuments.epochSeconds(changedAt)
        );

        return SearchDocument.indexed(program.getId(), changedAt, document);
    }

    /**
     * The same five conditions the public listing query applies, plus the two
     * on the organization. They are repeated here rather than shared because
     * the listing expresses them as native SQL against enum columns and this
     * expresses them against loaded entities; what keeps them honest is that
     * both sides fail visibly — a program in the index that the listing will
     * not serve is a 404 from a search result.
     */
    private boolean isPubliclyVisible(Program program, Organization organization) {
        return organization != null
                && organization.getDeletedAt() == null
                && organization.getStatus() == OrganizationStatus.ACTIVE
                && program.getDeletedAt() == null
                && program.getState() == ProgramState.ACTIVE
                && program.getSubmissionState() == SubmissionState.APPROVED
                && program.getVisibility() == Visibility.PUBLIC;
    }

    private List<String> distinct(Stream<String> values) {
        Set<String> unique = values
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ArrayList<>(unique);
    }

    private LocalDateTime latest(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }
}
