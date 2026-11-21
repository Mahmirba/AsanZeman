package com.sb24.asanzeman.request.domain.model;

import java.util.List;

/** One page of a list, with the total number of matching rows. {@code page} starts at 0. */
public record PageResult<T>(List<T> items, long total, int page, int size) {

    public PageResult {
        items = List.copyOf(items);
    }
}
