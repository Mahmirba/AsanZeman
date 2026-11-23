package com.sb24.asanzeman.request.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.request.domain.model.BranchLink;
import com.sb24.asanzeman.request.domain.port.out.BranchRepositoryPort;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BranchIntegrityServiceTest {

    @Mock
    private BranchRepositoryPort branches;

    @Test
    void aSoundTableGivesNoProblems() {
        when(branches.links()).thenReturn(List.of(new BranchLink("0123", true, "0123")));

        assertThat(new BranchIntegrityService(branches).check()).isEmpty();
    }

    @Test
    void theProblemsFoundAreReturned() {
        when(branches.links()).thenReturn(List.of(new BranchLink("0123", false, "0123")));

        assertThat(new BranchIntegrityService(branches).check())
                .containsExactly("شعبه 0123 بسته شده اما شعبه جایگزین ندارد");
    }
}
