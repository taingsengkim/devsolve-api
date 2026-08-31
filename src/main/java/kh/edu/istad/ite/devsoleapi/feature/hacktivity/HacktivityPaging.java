package kh.edu.istad.ite.devsoleapi.feature.hacktivity;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The published paging contract for the feed, enforced in one place.
 *
 * <p>Sorting is an allow-list rather than "whatever property name you send".
 * A {@code ?sort=} naming anything else used to reach the query and come back
 * as an unhandled 500, so a client had no way to tell a typo from an outage
 * and the safe move was to send no sort at all. Here an unknown name is a 400
 * that says what it will accept.
 */
public final class HacktivityPaging {

    /**
     * Matches the cap on the sibling listings. Asking for more is not an
     * error — the page is simply this size, which is friendlier to a client
     * paging through a feed than a 400 halfway down it.
     */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * Sort name as a client sends it, mapped to the property path behind it.
     * {@code severity} lives on the report, but the feed's vocabulary is the
     * card's, not the schema's.
     */
    private static final Map<String, String> SORTABLE = Map.of(
            "createdAt", "createdAt",
            "severity", "report.severity"
    );

    private static final Sort DEFAULT_SORT =
            Sort.by(Sort.Direction.DESC, "createdAt");

    private HacktivityPaging() {
    }

    /**
     * Caps the page size and rewrites the sort into property paths, rejecting
     * any name not on the list. An unsorted request is newest-first, which is
     * the only order a feed makes sense in.
     */
    public static Pageable resolve(Pageable pageable) {

        int size = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);

        return PageRequest.of(
                pageable.getPageNumber(),
                size,
                resolveSort(pageable.getSort())
        );
    }

    private static Sort resolveSort(Sort requested) {

        if (requested.isUnsorted()) {
            return DEFAULT_SORT;
        }

        List<Sort.Order> orders = new ArrayList<>();

        for (Sort.Order order : requested) {
            String property = SORTABLE.get(order.getProperty());

            if (property == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cannot sort by '" + order.getProperty()
                                + "'. Sortable fields are "
                                + String.join(", ", SORTABLE.keySet().stream()
                                .sorted()
                                .toList())
                );
            }

            orders.add(new Sort.Order(order.getDirection(), property));
        }

        return Sort.by(orders);
    }
}
