package kh.edu.istad.ite.devsoleapi.feature.problem;

import kh.edu.istad.ite.devsoleapi.common.cache.CacheNames;
import kh.edu.istad.ite.devsoleapi.feature.ai.AiReviewClient;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Reads the candidates against the draft and says which of them actually
 * matter.
 *
 * <p>This is the step that makes the feature worth building. Trigram similarity
 * and a search index both match on the words that are there, and a developer
 * describing a bug they do not understand rarely reaches for the same words as
 * the person who already solved it — "app dies on boot" and "SIGSEGV during
 * static initialisation" have nothing lexical in common and are the same
 * problem. Deciding that is reading comprehension, so it is done by something
 * that reads.
 *
 * <p>Its own bean, and not a private method on the service, for the cache
 * below: {@code @Cacheable} is applied by a proxy, and a method calling its own
 * annotated method goes straight past it.
 */
@Component
@RequiredArgsConstructor
public class ProblemDuplicateReviewer {

    /** Enough of the draft to judge it; the rest is rarely load-bearing and always paid for. */
    private static final int DRAFT_BODY_LENGTH = 2_000;

    private static final int CANDIDATE_BODY_LENGTH = 600;
    private static final int CANDIDATE_ERROR_LENGTH = 300;

    /**
     * Stable across requests, and marked as a cache breakpoint by the client,
     * so the instructions are not re-read at full price on every check.
     */
    private static final String SYSTEM_PROMPT = """
            You are the duplicate check on DevSolve, a platform where developers \
            post problems they are stuck on and other developers answer them.

            You are given a DRAFT problem somebody is about to post, and a list \
            of CANDIDATE problems already on the platform. The candidates were \
            retrieved by keyword similarity, so most of them are usually noise. \
            Your job is to say which ones would genuinely help the author, and \
            why.

            Classify each candidate you keep:

            DUPLICATE       The same underlying problem. Reading it would answer \
            the draft outright.
            NEAR_DUPLICATE  The same underlying cause in a different setting - \
            another framework, another version, another symptom of one bug. The \
            author would have to adapt the answer rather than just read it.
            RELATED         Not the same problem, but genuinely useful to \
            somebody stuck on the draft.

            Rules:

            - Judge the underlying technical problem, not the wording. A \
            paraphrase of the same bug is a DUPLICATE even when it shares no \
            vocabulary with the draft.
            - Sharing a language, a framework or a library is not a \
            relationship. Neither is sharing a generic symptom such as "it \
            crashes", "it hangs" or "it is slow". Two unrelated bugs that both \
            mention Docker are not related.
            - Leave a candidate out entirely rather than reaching for RELATED. \
            An empty list is a correct and common answer, and is far better \
            than a list the author learns to ignore.
            - confidence is 0-100 and describes how sure you are of the \
            classification, not how alike the words are.
            - reason is one sentence of at most 140 characters, addressed to \
            the author, naming the concrete thing the two share. Say what is \
            shared, not that something is shared. Do not restate the \
            candidate's title, and do not mention that you are an AI or how you \
            reached the verdict.
            - When a candidate is solved, the reason is a good place to say so.
            - id must be copied exactly from the candidate list. Never invent \
            an id and never return one that is not in the list.
            - Order the list with the most useful candidate first.
            """;

    private final AiReviewClient ai;

    public boolean isEnabled() {
        return ai.isEnabled();
    }

    /**
     * @param fingerprint identifies the draft together with the exact set of
     *                    candidates it was judged against, which is what makes
     *                    the cached verdicts safe to reuse: change either side
     *                    and this changes, so a stale verdict cannot be served
     *                    against a candidate list that has moved on
     * @throws kh.edu.istad.ite.devsoleapi.feature.ai.AiUnavailableException
     *         if the model cannot answer. Deliberately not caught here: a
     *         failure must not be cached, and only the caller knows what to
     *         serve instead.
     */
    @Cacheable(cacheNames = CacheNames.PROBLEM_DUPLICATE_REVIEW, key = "#fingerprint")
    public DuplicateJudgements review(
            String fingerprint,
            String title,
            String description,
            List<DuplicateCandidate> candidates
    ) {
        if (candidates.isEmpty()) {
            return DuplicateJudgements.empty();
        }
        return ai.ask(
                SYSTEM_PROMPT,
                userMessage(title, description, candidates),
                DuplicateJudgements.class
        );
    }

    private String userMessage(
            String title,
            String description,
            List<DuplicateCandidate> candidates
    ) {
        StringBuilder message = new StringBuilder(512);

        message.append("DRAFT\n")
                .append("title: ").append(title).append('\n');
        if (description != null && !description.isBlank()) {
            message.append("description: ")
                    .append(clip(description, DRAFT_BODY_LENGTH))
                    .append('\n');
        }

        message.append("\nCANDIDATES\n");
        for (DuplicateCandidate candidate : candidates) {
            message.append("\nid: ").append(candidate.id()).append('\n')
                    .append("title: ").append(candidate.title()).append('\n')
                    .append("status: ").append(candidate.status())
                    .append(" (").append(candidate.solutionCount())
                    .append(" published solutions)\n");
            if (candidate.errorMessage() != null
                    && !candidate.errorMessage().isBlank()) {
                message.append("error: ")
                        .append(clip(
                                candidate.errorMessage(),
                                CANDIDATE_ERROR_LENGTH
                        ))
                        .append('\n');
            }
            if (candidate.description() != null
                    && !candidate.description().isBlank()) {
                message.append("description: ")
                        .append(clip(
                                candidate.description(),
                                CANDIDATE_BODY_LENGTH
                        ))
                        .append('\n');
            }
        }
        return message.toString();
    }

    private static String clip(String value, int max) {
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
