package kh.edu.istad.ite.devsoleapi.feature.showcase.dto;

import kh.edu.istad.ite.devsoleapi.feature.showcasestep.dto.ShowcaseStepResponse;

import java.util.List;

/**
 * The two extra queries a showcase detail needs beyond the showcase row. The
 * showcase itself is deliberately not in here: it is read fresh on every
 * request so visibility and content can never be served from a stale copy.
 */
public record ShowcaseDetailParts(
        List<ShowcaseTagResponse> tags,
        List<ShowcaseStepResponse> steps
) {
}
