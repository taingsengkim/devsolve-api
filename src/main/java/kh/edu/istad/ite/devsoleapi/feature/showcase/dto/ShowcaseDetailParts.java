package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.ShowcaseStepResponse;

import java.util.List;

/**
 * The extra queries a showcase detail needs beyond the showcase row. The
 * showcase itself is deliberately not in here: it is read fresh on every
 * request so visibility and content can never be served from a stale copy.
 *
 * <p>{@code related} is cached alongside the rest even though nothing evicts it
 * when a sibling showcase is published — a "more like this" strip that takes a
 * few minutes to notice a new neighbour is not wrong in any way a reader can
 * see, and the alternative is a join on every detail view.
 *
 * <p>The compact constructor fills nulls in because a released entry can
 * outlive the shape that wrote it: a cached record from before a field existed
 * comes back with that field null, and one deploy's worth of null lists is not
 * worth an NPE.
 */
public record ShowcaseDetailParts(
        List<ShowcaseTagResponse> tags,
        List<ShowcaseStepResponse> steps,
        List<RelatedShowcaseResponse> related
) {

    public ShowcaseDetailParts {
        tags = tags == null ? List.of() : tags;
        steps = steps == null ? List.of() : steps;
        related = related == null ? List.of() : related;
    }
}
