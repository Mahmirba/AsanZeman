package com.sb24.asanzeman.request.application;

import com.sb24.asanzeman.request.domain.port.in.CheckBranchIntegrityUseCase;
import com.sb24.asanzeman.request.domain.port.out.BranchRepositoryPort;
import com.sb24.asanzeman.request.domain.service.BranchIntegrity;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Reads the branch table and raises every problem to ops as an error log (the alert), one line per problem. */
@Service
public class BranchIntegrityService implements CheckBranchIntegrityUseCase {

    private static final Logger log = LoggerFactory.getLogger(BranchIntegrityService.class);

    private final BranchRepositoryPort branches;

    public BranchIntegrityService(BranchRepositoryPort branches) {
        this.branches = branches;
    }

    @Override
    public List<String> check() {
        List<String> problems = BranchIntegrity.problems(branches.links());
        problems.forEach(problem -> log.error("branch integrity: {}", problem));
        return problems;
    }
}
