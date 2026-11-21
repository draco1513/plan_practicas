package com.esfx.taskflow.common;

import java.util.List;

/** Página de resultados; "page" empieza en 0 (igual que en el contrato OpenAPI). */
public record PageResponse<T>(int page, int size, long totalElements, int totalPages, List<T> content) {
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long total) {
        int pages = size <= 0 ? 0 : (int) Math.ceil(total / (double) size);
        return new PageResponse<>(page, size, total, pages, content);
    }
}
