package com.iturtle.spring.web.dto.response;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageResponseTest {

    @Test
    void ofCalculatesTotalPagesAndHasNext() {
        PageResponse<String> first = PageResponse.of(List.of("a", "b"), 0, 2, 5);
        PageResponse<String> last = PageResponse.of(List.of("e"), 2, 2, 5);

        assertEquals(3, first.totalPages());
        assertTrue(first.hasNext());
        assertFalse(last.hasNext());
    }

    @Test
    void fromSpringDataPage() {
        PageResponse<String> response = PageResponse.from(new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5));

        assertEquals(List.of("a", "b"), response.content());
        assertEquals(1, response.page());
        assertEquals(2, response.size());
        assertEquals(5, response.totalElements());
        assertEquals(3, response.totalPages());
        assertTrue(response.hasNext());
    }

    @Test
    void mapKeepsPagingInfo() {
        PageResponse<Integer> mapped = PageResponse.of(List.of("a", "bb"), 0, 2, 5).map(String::length);

        assertEquals(List.of(1, 2), mapped.content());
        assertEquals(5, mapped.totalElements());
        assertTrue(mapped.hasNext());
    }
}
