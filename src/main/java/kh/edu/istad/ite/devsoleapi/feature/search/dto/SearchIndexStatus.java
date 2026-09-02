package kh.edu.istad.ite.devsoleapi.feature.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param uid           the index's real name in Meilisearch, prefix included.
 *                      Worth reporting because it is what an operator types
 *                      into Meilisearch's own tooling.
 * @param documentCount -1 when Meilisearch could not be asked.
 */
public record SearchIndexStatus(
        String index,
        String uid,

        @Schema(description = "-1 when Meilisearch could not be reached.")
        long documentCount
) {
}
