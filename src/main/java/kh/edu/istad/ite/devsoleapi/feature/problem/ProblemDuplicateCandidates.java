package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.common.projection.IdCountProjection;
import kh.edu.istad.ite.devsoleapi.feature.problem.enums.ProblemStatus;
import kh.edu.istad.ite.devsoleapi.feature.search.MeilisearchClient;
import kh.edu.istad.ite.devsoleapi.feature.search.MeilisearchException;
import kh.edu.istad.ite.devsoleapi.feature.search.indexes.ProblemSearchIndex;
import kh.edu.istad.ite.devsoleapi.feature.solution.SolutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Everything the duplicate check might want to show, before anything has
 * decided whether it is worth showing.
 *
 * <p>Recall is the whole job here — precision is the model's, one step later.
 * That splits the work the way the two halves are actually good: Postgres and
 * Meilisearch are cheap and find things by the words in them, and reading a
 * dozen candidates to decide which are the same bug is what the model is for.
 * Handing the model a wider net costs one line of prompt per candidate and
 * saves a duplicate going unnoticed, so both engines run and their results are
 * merged rather than one being chosen.
 *
 * <p>The two find different things, which is the reason for the second call.
 * The trigram query matches title against title and title against description,
 * and it survives typos. Meilisearch also indexes the error message and the
 * tags — so a draft that quotes a stack trace finds the problem that quotes the
 * same one even when the two titles have nothing in common.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class ProblemDuplicateCandidates {

    /** The three a reader is allowed to see, matching the public feed. */
    private static final Set<ProblemStatus> PUBLIC_STATUSES = EnumSet.of(
            ProblemStatus.PUBLISHED,
            ProblemStatus.RESOLVED,
            ProblemStatus.CLOSED
    );

    /**
     * Below this a needle matches most of the corpus; above it the trigram
     * operators lose their meaning — see the comment on
     * {@link ProblemRepository#findRelated}, whose limits these mirror because
     * it is the query being called.
     */
    private static final int MIN_NEEDLE_LENGTH = 4;
    private static final int MAX_NEEDLE_LENGTH = 200;

    private static final int LEXICAL_FETCH = 8;
    private static final int SEMANTIC_FETCH = 8;

    /** Meilisearch scores the query as a whole; a paragraph is not a query. */
    private static final int MAX_SEARCH_QUERY_LENGTH = 300;

    /**
     * Ids gathered beyond what the caller asked for, so that a row dropped by
     * the recheck below does not silently cost a slot. Meilisearch is written
     * asynchronously, so it can still be holding a problem that has since been
     * deleted or unpublished.
     */
    private static final int OVERSHOOT = 4;

    private final ProblemRepository problemRepository;
    private final SolutionRepository solutionRepository;
    private final MeilisearchClient meilisearch;

    /**
     * @param limit how many to return, in the order they were found
     */
    @Transactional(readOnly = true)
    List<DuplicateCandidate> find(
            String title,
            String description,
            UUID excludeId,
            int limit
    ) {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        int wanted = limit + OVERSHOOT;

        // Title first, and its results keep their position: it is the field
        // most likely to be a paraphrase of an older title, which is the match
        // worth showing at the top.
        collectLexical(ids, title, excludeId);
        collectLexical(ids, description, excludeId);
        collectSemantic(ids, title, description, excludeId);

        if (ids.isEmpty()) {
            return List.of();
        }

        List<UUID> shortlist = ids.stream().limit(wanted).toList();
        Map<UUID, Problem> problems = problemRepository.findAllById(shortlist)
                .stream()
                .filter(ProblemDuplicateCandidates::visible)
                .collect(Collectors.toUnmodifiableMap(
                        Problem::getId,
                        Function.identity()
                ));

        List<UUID> kept = shortlist.stream()
                .filter(problems::containsKey)
                .limit(limit)
                .toList();
        if (kept.isEmpty()) {
            return List.of();
        }

        Map<UUID, Long> solutionCounts = solutionRepository
                .countPublishedByProblemIds(kept)
                .stream()
                .collect(Collectors.toUnmodifiableMap(
                        IdCountProjection::getId,
                        IdCountProjection::getTotal
                ));

        List<DuplicateCandidate> candidates = new ArrayList<>(kept.size());
        for (UUID id : kept) {
            Problem problem = problems.get(id);
            candidates.add(new DuplicateCandidate(
                    id,
                    problem.getTitle(),
                    problem.getDescription(),
                    problem.getErrorMessage(),
                    problem.getStatus(),
                    solutionCounts.getOrDefault(id, 0L),
                    problem.getViewCount()
            ));
        }
        return candidates;
    }

    /**
     * A row read back by id has to be rechecked. The ids came from a search
     * index that is written asynchronously and from a query that ran a moment
     * earlier; neither is a promise that the problem is still public.
     */
    private static boolean visible(Problem problem) {
        return problem.getDeletedAt() == null
                && PUBLIC_STATUSES.contains(problem.getStatus());
    }

    private void collectLexical(
            LinkedHashSet<UUID> ids,
            String text,
            UUID excludeId
    ) {
        String needle = needle(text);
        if (needle == null) {
            return;
        }
        problemRepository.findRelated(needle, excludeId, LEXICAL_FETCH)
                .forEach(row -> ids.add(row.getId()));
    }

    /**
     * Failing soft is the only sensible behaviour: Meilisearch is optional
     * everywhere else in this application, and the trigram results are already
     * in hand. A search engine being down should cost the check some recall,
     * not the whole answer.
     */
    private void collectSemantic(
            LinkedHashSet<UUID> ids,
            String title,
            String description,
            UUID excludeId
    ) {
        if (!meilisearch.isEnabled()) {
            return;
        }

        String query = clip(
                (title + " " + orEmpty(description)).trim(),
                MAX_SEARCH_QUERY_LENGTH
        );
        if (query.length() < MIN_NEEDLE_LENGTH) {
            return;
        }

        try {
            JsonNode response = meilisearch.search(
                    meilisearch.indexUid(ProblemSearchIndex.NAME),
                    Map.of(
                            "q", query,
                            "limit", SEMANTIC_FETCH,
                            "attributesToRetrieve", List.of("id")
                    )
            );
            for (JsonNode hit : response.path("hits")) {
                UUID id = parseId(hit.path("id").asText(""));
                if (id != null && !id.equals(excludeId)) {
                    ids.add(id);
                }
            }
        } catch (MeilisearchException exception) {
            log.debug(
                    "Duplicate check ran without search recall: {}",
                    exception.getMessage()
            );
        }
    }

    private static UUID parseId(String value) {
        try {
            return value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Null when there is nothing here worth running a trigram query for. */
    private static String needle(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return normalized.length() < MIN_NEEDLE_LENGTH
                ? null
                : clip(normalized, MAX_NEEDLE_LENGTH);
    }

    private static String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
