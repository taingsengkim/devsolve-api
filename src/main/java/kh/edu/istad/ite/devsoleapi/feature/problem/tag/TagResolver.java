package kh.edu.istad.ite.devsoleapi.feature.problem.tag;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Turns the tag IDs and free-text tag names carried by a request into the tag
 * rows they refer to, creating a tag for any name whose slug is not taken yet.
 *
 * <p>Problems and showcases tag their content out of the same {@code tags}
 * table, so the limit and the slug rules live here rather than in either
 * service.
 */
@Component
@RequiredArgsConstructor
public class TagResolver {

    public static final int MAX_TAGS = 10;
    private static final int MAX_SLUG_LENGTH = 50;

    private final TagRepository tagRepository;

    /**
     * @param ownerLabel what the tags are being attached to ("problem",
     *                   "showcase"), used in the error messages
     * @return the resolved tags keyed by ID, in the order they were requested
     */
    public Map<UUID, Tag> resolve(
            Set<UUID> tagIds,
            Set<String> tagNames,
            String ownerLabel
    ) {
        // Mutable copies: an immutable empty Set throws on contains(null).
        Set<UUID> requestedIds = tagIds == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(tagIds);
        Set<String> requestedNames = tagNames == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(tagNames);
        if (requestedIds.size() + requestedNames.size() > MAX_TAGS) {
            throw tooManyTags(ownerLabel);
        }
        if (requestedIds.contains(null)) {
            throw badRequest("Tag IDs cannot be null");
        }

        Map<UUID, Tag> desired = new LinkedHashMap<>();
        if (!requestedIds.isEmpty()) {
            List<Tag> tagsById = tagRepository.findAllByIdIn(requestedIds);
            if (tagsById.size() != requestedIds.size()) {
                throw badRequest("One or more tag IDs do not exist");
            }
            tagsById.forEach(tag -> desired.put(tag.getId(), tag));
        }

        Set<String> normalizedSlugs = new HashSet<>();
        for (String requestedName : requestedNames) {
            String name = trimToNull(requestedName);
            if (name == null) {
                throw badRequest("Tag names cannot be blank");
            }
            String slug = normalizeSlug(name);
            if (!normalizedSlugs.add(slug)) {
                throw badRequest("Duplicate normalized tag names");
            }

            Tag tag = tagRepository.findBySlug(slug)
                    .orElseGet(() -> tagRepository.saveAndFlush(
                            Tag.builder()
                                    .name(name)
                                    .slug(slug)
                                    .build()
                    ));
            if (desired.putIfAbsent(tag.getId(), tag) != null) {
                throw badRequest(
                        "The same tag was supplied by both ID and name"
                );
            }
        }
        if (desired.size() > MAX_TAGS) {
            throw tooManyTags(ownerLabel);
        }
        return desired;
    }

    public static String normalizeSlug(String value) {
        String normalized = slugify(value);
        if (normalized.isBlank()) {
            throw badRequest("Tag name must contain letters or numbers");
        }
        if (normalized.length() > MAX_SLUG_LENGTH) {
            throw badRequest("Normalized tag slug cannot exceed 50 characters");
        }
        return normalized;
    }

    /**
     * The same normalization for a search box, which cannot throw: a query of
     * punctuation alone is not an error, it just has nothing to match on and
     * comes back as the empty string — the pattern that matches every tag.
     */
    public static String searchSlug(String value) {
        return slugify(value);
    }

    /**
     * Folds accents away, lowercases, and collapses everything that is not a
     * letter or digit into single dashes. Blank when the input held neither.
     */
    private static String slugify(String value) {
        return Normalizer.normalize(
                        Objects.requireNonNullElse(value, ""),
                        Normalizer.Form.NFKD
                )
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ResponseStatusException tooManyTags(String ownerLabel) {
        return badRequest(
                "A " + ownerLabel + " can contain at most "
                        + MAX_TAGS + " tags"
        );
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}
