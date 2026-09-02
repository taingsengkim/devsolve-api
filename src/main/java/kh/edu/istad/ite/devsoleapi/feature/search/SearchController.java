package kh.edu.istad.ite.devsoleapi.feature.search;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import kh.edu.istad.ite.devsoleapi.common.exception.RestErrorResponse;
import kh.edu.istad.ite.devsoleapi.common.listing.ListingCache;
import kh.edu.istad.ite.devsoleapi.feature.search.dto.SearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Search across everything public: programs, showcases, problems,
 * organizations and researchers.
 *
 * <p>Anonymous, like the listings it searches. Nothing reaches these indexes
 * that is not already on a public endpoint, so there is no viewer to tailor
 * results to and no per-viewer state to keep out of a shared cache — which is
 * why this can carry the same cache headers the feeds do.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class SearchController {

    private final SearchService searchService;

    @Operation(
            summary = "Search",
            description = """
                    With a `type`, searches that one index and pages through it.
                    Without one, searches every index at once and returns a
                    short list from each — the shape a search box wants while
                    somebody is still typing.

                    A blank `q` matches everything, which is how to browse an
                    index rather than search it.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Results, possibly empty")
    @ApiResponse(
            responseCode = "400",
            description = "Unknown type, or size above the limit",
            content = @Content(schema = @Schema(implementation = RestErrorResponse.class))
    )
    @ApiResponse(
            responseCode = "503",
            description = "Search is switched off, or Meilisearch is unreachable",
            content = @Content(schema = @Schema(implementation = RestErrorResponse.class))
    )
    @GetMapping("/search")
    public ResponseEntity<SearchResponse> search(
            @RequestParam(required = false)
            @Size(max = 200)
            String q,

            @RequestParam(required = false)
            String type,

            @RequestParam(defaultValue = "0")
            @Min(0)
            int page,

            @RequestParam(required = false)
            @Min(1)
            Integer size
    ) {
        return ListingCache.publicListing(
                searchService.search(q, type, page, size)
        );
    }

    @Operation(
            summary = "The values the type parameter accepts",
            description = "In the order a grouped result set returns them."
    )
    @GetMapping("/search/types")
    public List<String> searchableTypes() {
        return searchService.searchableTypes();
    }
}
