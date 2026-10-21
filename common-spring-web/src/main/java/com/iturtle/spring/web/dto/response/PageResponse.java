package com.iturtle.spring.web.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 페이지 응답. page 는 0 부터 시작한다 (Spring Data 와 동일).
 * <pre>
 * Page&lt;Order&gt; orders = orderRepository.findAll(pageable);
 * return ApiBaseResponse.success(PageResponse.from(orders).map(OrderResponse::from));
 * </pre>
 * Spring Data 는 from(Page) 에서만 사용하므로 Spring Data 가 없는 서비스는 of(...) 를 사용한다.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return new PageResponse<>(List.copyOf(content), page, size, totalElements, totalPages, page + 1 < totalPages);
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(List.copyOf(page.getContent()), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }

    public <R> PageResponse<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = content.stream().<R>map(mapper).toList();
        return new PageResponse<>(mapped, page, size, totalElements, totalPages, hasNext);
    }

}
