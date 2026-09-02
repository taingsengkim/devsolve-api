package kh.edu.istad.ite.devsoleapi.common.props;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Where Meilisearch lives and how hard the API leans on it.
 *
 * <p>Off by default. Search is derived data — every document in it is a
 * rebuild away from a row in PostgreSQL — so a deployment with no Meilisearch
 * running is a deployment without the search endpoint, not a broken one.
 */
@Configuration
@ConfigurationProperties(prefix = "app.meilisearch")
@Getter
@Setter
@NoArgsConstructor
public class MeilisearchProps {

    private boolean enabled = false;

    private String url = "http://localhost:7700";

    /**
     * The master key, or an API key with document and settings write access.
     * Blank is only viable against a Meilisearch started without a master key,
     * which is a local-laptop configuration and nothing else.
     */
    private String apiKey = "";

    /**
     * Prepended to every index name, so one Meilisearch can hold staging and
     * production side by side without either one reindexing over the other.
     */
    private String indexPrefix = "";

    private Duration connectTimeout = Duration.ofSeconds(2);

    /**
     * Generous next to the connect timeout because it also covers the indexing
     * writes, which hand over a whole batch of documents.
     */
    private Duration readTimeout = Duration.ofSeconds(10);

    /** Documents per round trip, on both the read from PostgreSQL and the write. */
    private int batchSize = 200;

    /**
     * How long after one sync pass finishes the next one starts, and therefore
     * the worst case for how stale a search result can be.
     *
     * <p>Bound here for documentation and for anything that wants to read it;
     * the scheduler itself resolves {@code app.meilisearch.sync-interval} as a
     * placeholder, because {@code @Scheduled} is fixed at startup and cannot
     * take a value off a bean.
     */
    private Duration syncInterval = Duration.ofSeconds(30);

    /**
     * The wait before the first pass, which is what keeps a cold start from
     * spending its first second on indexing instead of on serving.
     */
    private Duration syncInitialDelay = Duration.ofSeconds(15);

    /**
     * How far back each sync pass reaches beyond the last change it saw.
     *
     * <p>A watermark on {@code updated_at} alone has a hole in it: a
     * transaction that stamps a row at T can commit after a pass that already
     * read past T, and that row would never be seen again. Re-reading the last
     * few seconds on every pass closes it. Writes are keyed by document id, so
     * anything caught twice is simply written twice.
     */
    private Duration syncOverlap = Duration.ofSeconds(30);
}
