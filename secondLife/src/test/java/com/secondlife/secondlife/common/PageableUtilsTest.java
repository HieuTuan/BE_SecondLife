package com.secondlife.secondlife.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PageableUtilsTest {

    private static final Set<String> ALLOWED_FIELDS = Set.of("id", "finalPrice", "status", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    @Test
    void shouldFallbackWhenSortContainsBracketedEmptyString() {
        // e.g. sort=[""]
        Pageable pageable = PageRequest.of(0, 10, Sort.by("[\"\"]"));
        Pageable sanitized = PageableUtils.sanitize(pageable, ALLOWED_FIELDS, DEFAULT_SORT);

        assertEquals(0, sanitized.getPageNumber());
        assertEquals(10, sanitized.getPageSize());
        assertEquals(DEFAULT_SORT, sanitized.getSort());
    }

    @Test
    void shouldFallbackWhenSortContainsEmptyBrackets() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("[]"));
        Pageable sanitized = PageableUtils.sanitize(pageable, ALLOWED_FIELDS, DEFAULT_SORT);

        assertEquals(DEFAULT_SORT, sanitized.getSort());
    }

    @Test
    void shouldFallbackWhenSortContainsInvalidField() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("nonExistentField"));
        Pageable sanitized = PageableUtils.sanitize(pageable, ALLOWED_FIELDS, DEFAULT_SORT);

        assertEquals(DEFAULT_SORT, sanitized.getSort());
    }

    @Test
    void shouldPreserveValidField() {
        Pageable pageable = PageRequest.of(1, 20, Sort.by(Sort.Direction.ASC, "finalPrice"));
        Pageable sanitized = PageableUtils.sanitize(pageable, ALLOWED_FIELDS, DEFAULT_SORT);

        assertEquals(1, sanitized.getPageNumber());
        assertEquals(20, sanitized.getPageSize());
        assertTrue(sanitized.getSort().isSorted());
        Sort.Order order = sanitized.getSort().getOrderFor("finalPrice");
        assertNotNull(order);
        assertEquals(Sort.Direction.ASC, order.getDirection());
    }

    @Test
    void shouldCleanBracketedValidField() {
        // e.g. sort=["createdAt"]
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "[\"createdAt\"]"));
        Pageable sanitized = PageableUtils.sanitize(pageable, ALLOWED_FIELDS, DEFAULT_SORT);

        Sort.Order order = sanitized.getSort().getOrderFor("createdAt");
        assertNotNull(order);
        assertEquals(Sort.Direction.ASC, order.getDirection());
    }

    @Test
    void shouldHandleNullPageable() {
        Pageable sanitized = PageableUtils.sanitize(null, ALLOWED_FIELDS, DEFAULT_SORT);

        assertNotNull(sanitized);
        assertEquals(0, sanitized.getPageNumber());
        assertEquals(20, sanitized.getPageSize());
        assertEquals(DEFAULT_SORT, sanitized.getSort());
    }

    @Test
    void shouldHandleUnsortedPageable() {
        Pageable pageable = PageRequest.of(2, 15, Sort.unsorted());
        Pageable sanitized = PageableUtils.sanitize(pageable, ALLOWED_FIELDS, DEFAULT_SORT);

        assertEquals(2, sanitized.getPageNumber());
        assertEquals(15, sanitized.getPageSize());
        assertEquals(DEFAULT_SORT, sanitized.getSort());
    }
}
