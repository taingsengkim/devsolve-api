package kh.edu.istad.ite.devsoleapi.feature.security;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The paging contract for the incident table, enforced in one place.
 *
 * <p>Sorting is an allow-list, not "whatever property name you send". The
 * query behind this is a {@code Specification} over the entity, so an
 * unchecked sort name is a caller choosing a column — a typo comes back as an
 * unhandled 500, and the field names are not the client's to know.
 */
public final class SecurityIncidentPaging {

    /** Matches the cap on the sibling listings. */
    public static final int MAX_PAGE_SIZE = 100;

    private static final Map<String, String> SORTABLE = Map.of(
            "blockedAt", "blockedAt",
            "filename", "filename",
            "verdict", "verdict"
    );

    private static final Sort DEFAULT_SORT =
            Sort.by(Sort.Direction.DESC, "blockedAt");

    private SecurityIncidentPaging() {
    }

    /**
     * Caps the page size and rejects any sort name not on the list. An
     * unsorted request is newest-first, which is the only order an incident
     * table makes sense in.
     */
    public static Pageable resolve(Pageable pageable) {

        return PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), MAX_PAGE_SIZE),
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
