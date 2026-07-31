package kh.edu.istad.ite.devsoleapi.common.pagination;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageableValidatorTest {

    private static final Set<String> ALLOWED_SORTS = Set.of(
            "createdAt",
            "name"
    );

    @Test
    void acceptsAllowedSingleAndMultipleSortProperties() {
        Pageable pageable = PageRequest.of(
                0,
                20,
                Sort.by(
                        Sort.Order.asc("createdAt"),
                        Sort.Order.desc("name")
                )
        );

        Pageable result = PageableValidator.requireAllowedSort(
                pageable,
                ALLOWED_SORTS
        );

        assertSame(pageable, result);
    }

    @Test
    void rejectsSwaggerPlaceholderSort() {
        ResponseStatusException exception = assertInvalidSort(
                "[\"string\"]"
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(
                "Unsupported sort property '[\"string\"]'. "
                        + "Allowed properties: createdAt, name",
                exception.getReason()
        );
    }

    @Test
    void rejectsUnknownNestedAndExpressionSortProperties() {
        assertInvalidSort("unknown");
        assertInvalidSort("owner.email");
        assertInvalidSort("lower(name)");
    }

    @Test
    void rejectsPageSizesAboveMaximum() {
        Pageable pageable = PageRequest.of(
                0,
                101,
                Sort.by("createdAt")
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> PageableValidator.requireAllowedSort(
                        pageable,
                        ALLOWED_SORTS
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("pageSize must be <= 100", exception.getReason());
    }

    private ResponseStatusException assertInvalidSort(String property) {
        Pageable pageable = PageRequest.of(
                0,
                20,
                Sort.by(property)
        );
        return assertThrows(
                ResponseStatusException.class,
                () -> PageableValidator.requireAllowedSort(
                        pageable,
                        ALLOWED_SORTS
                )
        );
    }
}
