package kh.edu.istad.ite.devsoleapi.feature.search;

import kh.edu.istad.ite.devsoleapi.feature.search.dto.SearchResponse;

import java.util.List;

public interface SearchService {

    /**
     * Runs one query.
     *
     * @param query the text to look for. Blank is allowed and matches
     *              everything, which is how a caller browses an index rather
     *              than searching it.
     * @param type  one of {@link #searchableTypes()}, or null to search all of
     *              them and get a short list from each.
     * @param page  zero-based. Ignored when {@code type} is null.
     * @param size  hits per page, or hits per type in the grouped mode. Null
     *              takes the default for whichever mode is running, which are
     *              not the same number.
     */
    SearchResponse search(String query, String type, int page, Integer size);

    /** The values {@code type} accepts, in the order results are grouped. */
    List<String> searchableTypes();
}
