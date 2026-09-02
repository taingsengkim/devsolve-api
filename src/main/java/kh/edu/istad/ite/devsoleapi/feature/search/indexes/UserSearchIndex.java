package kh.edu.istad.ite.devsoleapi.feature.search.indexes;

import kh.edu.istad.ite.devsoleapi.feature.search.DocumentBatch;
import kh.edu.istad.ite.devsoleapi.feature.search.IndexSettings;
import kh.edu.istad.ite.devsoleapi.feature.search.SearchDocument;
import kh.edu.istad.ite.devsoleapi.feature.search.SearchDocuments;
import kh.edu.istad.ite.devsoleapi.feature.search.SearchIndexDefinition;
import kh.edu.istad.ite.devsoleapi.feature.search.SyncCursor;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserProfile;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.domain.UserStatus;
import kh.edu.istad.ite.devsoleapi.feature.userprofile.repository.UserProfileRepository;
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
 * Researchers, as their public profile shows them.
 *
 * <p>Everything indexed here is a field {@code PublicUserProfileResponse}
 * hands to an anonymous caller. Email is the one that has to be named to be
 * excluded: it is on the profile row, it is on that response, and it is on that
 * response only for a viewer who shares an organization with the person. A
 * search index has no viewer, so it cannot hold a field whose visibility
 * depends on one. Phone, date of birth and gender are not public at all and are
 * likewise absent.
 */
@Component
@Order(50)
@RequiredArgsConstructor
public class UserSearchIndex implements SearchIndexDefinition {

    public static final String NAME = "users";

    private final UserProfileRepository userProfileRepository;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public IndexSettings settings() {
        return new IndexSettings(
                List.of(
                        SearchDocuments.SUBTITLE,
                        SearchDocuments.TITLE,
                        SearchDocuments.BODY,
                        "country"
                ),
                List.of("country"),
                List.of(
                        SearchDocuments.UPDATED_AT,
                        SearchDocuments.CREATED_AT,
                        "reputation",
                        "recognitionCount",
                        "validReports"
                ),
                // Standing is the tiebreaker here rather than views: two people
                // whose names match a query equally well are not equally
                // interesting, and reputation is the platform's own answer to
                // which one is.
                IndexSettings.rankedBy("reputation:desc")
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentBatch loadChangedSince(SyncCursor cursor, int size) {
        Slice<UserProfile> profiles = userProfileRepository.findChangedSince(
                cursor.changedAt(),
                cursor.id(),
                PageRequest.of(0, size)
        );

        List<SearchDocument> documents = profiles.stream()
                .map(this::toDocument)
                .toList();

        return DocumentBatch.of(documents, profiles.hasNext());
    }

    private SearchDocument toDocument(UserProfile profile) {
        LocalDateTime changedAt = profile.getUpdatedAt();

        if (profile.getStatus() != UserStatus.ACTIVE) {
            return SearchDocument.removed(profile.getId(), changedAt);
        }

        // Username first in the searchable list above, so an exact handle beats
        // somebody whose display name merely contains it.
        Map<String, Object> document = SearchDocuments.envelope(
                NAME,
                profile.getId(),
                profile.getFullName(),
                profile.getUsername(),
                profile.getBiography(),
                profile.getAvatarUrl(),
                profile.getUsername()
        );

        document.put("username", profile.getUsername());
        document.put("coverImageUrl", profile.getCoverImageUrl());
        document.put("country", profile.getCountry());
        document.put("reputation", profile.getReputation());
        document.put("totalReports", profile.getTotalReports());
        document.put("validReports", profile.getValidReports());
        document.put("criticalReports", profile.getCriticalReports());
        document.put("recognitionCount", profile.getRecognitionCount());
        document.put(
                SearchDocuments.CREATED_AT,
                SearchDocuments.epochSeconds(profile.getCreatedAt())
        );
        document.put(
                SearchDocuments.UPDATED_AT,
                SearchDocuments.epochSeconds(changedAt)
        );

        return SearchDocument.indexed(profile.getId(), changedAt, document);
    }
}
