package kh.edu.istad.ite.devsoleapi.feature.problem.tag.dto;

import java.util.UUID;

/**
 * A tag as the catalogue lists it. Unlike the summaries embedded in a problem
 * or showcase, this carries the usage count the picker ranks on.
 *
 * @param usageCount published links to this tag, across problems and showcases
 */
public record TagResponse(
        UUID id,
        String name,
        String slug,
        int usageCount
) {
}
