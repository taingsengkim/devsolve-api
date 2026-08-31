package kh.edu.istad.ite.devsoleapi.feature.problem.tag.dto;

import java.util.UUID;

/**
 * What a tag deletion took with it, so an admin who forced one can see how much
 * content it touched.
 */
public record TagDeletionResponse(
        UUID id,
        String name,
        String slug,
        long unlinkedProblems,
        long unlinkedShowcases,
        long unlinkedRevisions
) {
}
