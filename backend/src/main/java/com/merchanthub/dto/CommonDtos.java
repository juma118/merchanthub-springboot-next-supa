package com.merchanthub.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public final class CommonDtos {
    private CommonDtos() {}

    /** Generic paginated envelope returned by list endpoints. */
    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages) {

        public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
            return new PageResponse<>(
                    page.getContent().stream().map(mapper).toList(),
                    page.getNumber(),
                    page.getSize(),
                    page.getTotalElements(),
                    page.getTotalPages());
        }
    }

    public record MeResponse(java.util.UUID id, String name, String email, String shopApiKey) {}
}
