package com.sb24.asanzeman.request.domain.port.in;

import com.sb24.asanzeman.request.domain.model.PageResult;
import com.sb24.asanzeman.request.domain.model.RequestSearchCriteria;
import com.sb24.asanzeman.request.domain.model.RequestSummary;
import java.util.List;
import java.util.function.Consumer;

/** The tracking list of SRS UC-AZS-13 and its export. */
public interface SearchRequestsUseCase {

    int MAX_PAGE_SIZE = 200;

    /**
     * @param page starting at 0
     * @param size 1 to {@value #MAX_PAGE_SIZE}
     * @throws com.sb24.asanzeman.request.domain.model.InvalidRequestDataException a bad page or size
     */
    PageResult<RequestSummary> search(RequestSearchCriteria criteria, int page, int size);

    /**
     * Every matching row for the CSV export (D42), read page by page so memory stays bounded: {@code sink} gets each
     * page in order, newest first.
     */
    void export(RequestSearchCriteria criteria, Consumer<List<RequestSummary>> sink);
}
