package io.rankpeek.model;

public record LcuWindowBounds(
        boolean found,
        Integer x,
        Integer y,
        Integer width,
        Integer height
) {
    public static LcuWindowBounds notFound() {
        return new LcuWindowBounds(false, null, null, null, null);
    }
}
