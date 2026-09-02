package kh.edu.istad.ite.devsoleapi.feature.search.dto;

/**
 * The acknowledgement of a rebuild request, not its result — the work carries
 * on after this has been sent. Watch {@code GET /api/v1/admin/search} for the
 * document counts.
 */
public record SearchRebuildResponse(boolean started, String message) {
}
