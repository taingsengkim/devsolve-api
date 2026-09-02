package kh.edu.istad.ite.devsoleapi.feature.search.indexes;

import kh.edu.istad.ite.devsoleapi.feature.organization.Organization;
import kh.edu.istad.ite.devsoleapi.feature.organization.OrganizationRepository;
import kh.edu.istad.ite.devsoleapi.feature.organization.enums.OrganizationStatus;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Organizations that have been approved and are still around.
 *
 * <p>Three columns on this row are deliberately not here. {@code joiningReason}
 * is what the applicant wrote to the reviewers, {@code rejectionReason} is what
 * the reviewers wrote back, and {@code ownerJobTitle} was collected for the
 * same review. None of them appear on the public organization response, and an
 * index is a poor place to discover that you have published something twice.
 */
@Component
@Order(40)
@RequiredArgsConstructor
public class OrganizationSearchIndex implements SearchIndexDefinition {

    public static final String NAME = "organizations";

    private final OrganizationRepository organizationRepository;

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
                        SearchDocuments.BODY,
                        "country"
                ),
                List.of("industry", "country"),
                List.of(
                        SearchDocuments.UPDATED_AT,
                        SearchDocuments.CREATED_AT
                ),
                // No popularity signal on this row worth ranking by — an
                // organization has no view count of its own — so the default
                // pipeline is the whole pipeline.
                List.of()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentBatch loadChangedSince(SyncCursor cursor, int size) {
        Slice<Organization> organizations = organizationRepository.findChangedSince(
                cursor.changedAt(),
                cursor.id(),
                PageRequest.of(0, size)
        );

        List<SearchDocument> documents = organizations.stream()
                .map(this::toDocument)
                .toList();

        return DocumentBatch.of(documents, organizations.hasNext());
    }

    private SearchDocument toDocument(Organization organization) {
        LocalDateTime changedAt = organization.getUpdatedAt();

        if (organization.getDeletedAt() != null
                || organization.getStatus() != OrganizationStatus.ACTIVE) {
            return SearchDocument.removed(organization.getId(), changedAt);
        }

        Map<String, Object> document = SearchDocuments.envelope(
                NAME,
                organization.getId(),
                organization.getName(),
                organization.getSlug(),
                organization.getDescription(),
                organization.getLogoUrl(),
                organization.getSlug()
        );

        document.put("coverImageUrl", organization.getCoverImageUrl());
        document.put("websiteUrl", organization.getWebsiteUrl());
        document.put(
                "industry",
                SearchDocuments.nameOf(organization.getIndustry())
        );
        document.put("country", organization.getCountry());
        document.put("companySize", organization.getCompanySize());
        document.put(
                "verifiedAt",
                SearchDocuments.epochSeconds(organization.getVerifiedAt())
        );
        document.put(
                SearchDocuments.CREATED_AT,
                SearchDocuments.epochSeconds(organization.getCreatedAt())
        );
        document.put(
                SearchDocuments.UPDATED_AT,
                SearchDocuments.epochSeconds(changedAt)
        );

        return SearchDocument.indexed(organization.getId(), changedAt, document);
    }
}
