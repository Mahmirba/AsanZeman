package com.sb24.asanzeman.request.infrastructure.web;

import com.sb24.asanzeman.request.domain.model.PageResult;
import com.sb24.asanzeman.request.domain.model.RequestSummary;
import java.util.List;
import org.springframework.context.MessageSource;

/** A page of the tracking list: {@code page} starts at 0 and {@code total} counts every matching row. */
public record RequestPageResponse(List<RequestSummaryResponse> items, long total, int page, int size) {

    static RequestPageResponse from(PageResult<RequestSummary> page, MessageSource messages) {
        return new RequestPageResponse(page.items().stream().map(s -> RequestSummaryResponse.from(s, messages)).toList(),
                page.total(), page.page(), page.size());
    }
}
