package com.sb24.asanzeman.request.domain.port.in;

import com.sb24.asanzeman.request.domain.model.BranchSummary;
import java.util.List;

/** The cascading branch list of the registration form (SRS UC-AZS-8): open branches only (4.4). */
public interface ListBranchesUseCase {

    /**
     * @param query the first letters of the name or code the agency typed; blank lists every open branch. Matching is
     *        by prefix, so typing the first three letters narrows the list.
     */
    List<BranchSummary> listOpen(String query);
}
