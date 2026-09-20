package com.bajrix.marketplace.common;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
        int totalPages = (int) Math.ceil(totalItems / (double) size);
        return new PageResponse<>(items, page, size, totalItems, totalPages);
    }
}
