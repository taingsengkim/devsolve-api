package kh.edu.istad.ite.devsoleapi.feature.search;

/**
 * Meilisearch could not be reached, or refused the request.
 *
 * <p>Thrown by {@link MeilisearchClient} and handled differently on each side
 * of the feature: the indexer swallows it and retries on the next pass, since
 * the index is rebuildable and no user is waiting; the search endpoint turns it
 * into a 503, since a reader asked a question that cannot be answered.
 */
public class MeilisearchException extends RuntimeException {

    public MeilisearchException(String message) {
        super(message);
    }

    public MeilisearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
