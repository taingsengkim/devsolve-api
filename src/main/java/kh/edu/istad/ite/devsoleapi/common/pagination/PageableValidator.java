package kh.edu.istad.ite.devsoleapi.common.pagination;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

public final class PageableValidator {

    private static final int MAX_PAGE_SIZE = 100;

    private PageableValidator() {
    }

    public static Pageable requireAllowedSort(
            Pageable pageable,
            Set<String> allowedProperties
    ) {
        if (pageable.isPaged() && pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "pageSize must be <= " + MAX_PAGE_SIZE
            );
        }
        for (String property : pageable.getSort()
                .map(order -> order.getProperty())
                .toList()) {
            if (!allowedProperties.contains(property)) {
                String allowed = String.join(
                        ", ",
                        allowedProperties.stream().sorted().toList()
                );
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unsupported sort property '" + property
                                + "'. Allowed properties: " + allowed
                );
            }
        }
        return pageable;
    }
}
