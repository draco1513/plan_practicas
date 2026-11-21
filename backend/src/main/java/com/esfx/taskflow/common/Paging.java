package com.esfx.taskflow.common;

/** Normaliza page/size recibidos por query string. */
public record Paging(int page, int size) {
    public static Paging of(Integer page, Integer size) {
        int p = page == null || page < 0 ? 0 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, 100);
        return new Paging(p, s);
    }
    public int offset() { return page * size; }
}
