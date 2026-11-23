package com.sb24.asanzeman.request.domain.port.in;

import java.util.List;

/** The nightly check of the branch table (4.4): cycles, chains, inactive parents and closed branches with no replacement. */
public interface CheckBranchIntegrityUseCase {

    /** @return the problems found, each already raised to ops as an error log; empty when the table is sound */
    List<String> check();
}
