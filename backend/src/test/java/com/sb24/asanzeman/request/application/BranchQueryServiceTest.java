package com.sb24.asanzeman.request.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.request.domain.model.BranchRequestRow;
import com.sb24.asanzeman.request.domain.model.BranchSearchCriteria;
import com.sb24.asanzeman.request.domain.model.BranchUser;
import com.sb24.asanzeman.request.domain.model.Companion;
import com.sb24.asanzeman.request.domain.model.InvalidRequestDataException;
import com.sb24.asanzeman.request.domain.model.PageResult;
import com.sb24.asanzeman.request.domain.model.RejectionReason;
import com.sb24.asanzeman.request.domain.model.RequestNotFoundException;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.RequestSummary;
import com.sb24.asanzeman.request.domain.model.RequestType;
import com.sb24.asanzeman.request.domain.port.out.BranchQueryPort;
import com.sb24.asanzeman.request.domain.port.out.CollateralRepositoryPort;
import com.sb24.asanzeman.request.domain.port.out.ReviewRepositoryPort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BranchQueryServiceTest {

    private static final BranchUser ALI = new BranchUser(UUID.randomUUID(), "ali", "0123", false);
    private static final BranchUser ADMIN = new BranchUser(UUID.randomUUID(), "root", "0777", true);
    private static final BranchSearchCriteria ANY = new BranchSearchCriteria("ignored", Set.of(RequestStatus.ISSUED),
            "1000000011", null, null, null, null);

    @Mock
    private BranchQueryPort queries;
    @Mock
    private CollateralRepositoryPort collateral;
    @Mock
    private ReviewRepositoryPort reviews;
    @InjectMocks
    private BranchQueryService service;

    private static BranchRequestRow row(String branch, String handling) {
        return new BranchRequestRow(new RequestSummary(UUID.randomUUID(), "1", RequestType.ISSUANCE,
                RequestStatus.PENDING_BRANCH_REVIEW, "1000000011", "555", "Ali", 1000, branch, "Valiasr",
                LocalDate.of(2027, 1, 20), Instant.now(), "Sky", "0912"), null, null, handling, null, null, null);
    }

    @Test
    void aBranchUserSearchesOnlyTheirOwnBranchWhateverTheCriteriaSay() {
        var page = new PageResult<>(List.of(row("0123", "0123")), 1, 0, 20);
        when(queries.search(org.mockito.ArgumentMatchers.any(), eq(0), eq(20))).thenReturn(page);

        assertThat(service.search(ALI, ANY, 0, 20)).isSameAs(page);

        var scoped = ArgumentCaptor.forClass(BranchSearchCriteria.class);
        verify(queries).search(scoped.capture(), eq(0), eq(20));
        assertThat(scoped.getValue().branchCode()).isEqualTo("0123");
        assertThat(scoped.getValue().statuses()).containsExactly(RequestStatus.ISSUED);
        assertThat(scoped.getValue().customerCode()).isEqualTo("1000000011");
    }

    @Test
    void anAdminSearchesEveryBranch() {
        when(queries.search(org.mockito.ArgumentMatchers.any(), eq(0), eq(20)))
                .thenReturn(new PageResult<>(List.of(), 0, 0, 20));

        service.search(ADMIN, ANY, 0, 20);

        var scoped = ArgumentCaptor.forClass(BranchSearchCriteria.class);
        verify(queries).search(scoped.capture(), eq(0), eq(20));
        assertThat(scoped.getValue().branchCode()).isNull();
    }

    @Test
    void aBadPageOrSizeIsRefused() {
        assertThatThrownBy(() -> service.search(ALI, ANY, -1, 20)).isInstanceOf(InvalidRequestDataException.class);
        assertThatThrownBy(() -> service.search(ALI, ANY, 0, 0)).isInstanceOf(InvalidRequestDataException.class);
        assertThatThrownBy(() -> service.search(ALI, ANY, 0, 201)).isInstanceOf(InvalidRequestDataException.class);
    }

    @Test
    void theDetailsCarryTheCompanionsAndTheCollateral() {
        var row = row("0123", "0456");
        UUID id = row.summary().id();
        var companion = new Companion("Sara", "0012345679", LocalDate.of(2016, 4, 1), "P1");
        when(queries.find(id)).thenReturn(Optional.of(row));
        when(queries.companions(id)).thenReturn(List.of(companion));
        when(collateral.find(id)).thenReturn(List.of());

        var details = service.get(ALI, id);

        assertThat(details.row()).isSameAs(row);
        assertThat(details.companions()).containsExactly(companion);
    }

    @Test
    void aBranchUserCannotSeeAnotherBranchsRequestButAnAdminCan() {
        var row = row("0123", "0456");
        UUID id = row.summary().id();
        when(queries.find(id)).thenReturn(Optional.of(row));
        when(queries.companions(id)).thenReturn(List.of());
        when(collateral.find(id)).thenReturn(List.of());
        var stranger = new BranchUser(UUID.randomUUID(), "omid", "0999", false);

        assertThatThrownBy(() -> service.get(stranger, id)).isInstanceOf(RequestNotFoundException.class);
        assertThat(service.get(ADMIN, id).row()).isSameAs(row);
    }

    @Test
    void anUnknownRequestIsNotFound() {
        UUID id = UUID.randomUUID();
        when(queries.find(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(ALI, id)).isInstanceOf(RequestNotFoundException.class);
    }

    @Test
    void theRevocationReasonsAreAListOfTheirOwnAndAnythingElseIsRefused() {
        var reasons = List.of(new RejectionReason(UUID.randomUUID(), "The guarantee is still needed"));
        when(reviews.reasons("REVOCATION")).thenReturn(reasons);

        assertThat(service.rejectionReasons("REVOCATION")).isEqualTo(reasons);
        assertThatThrownBy(() -> service.rejectionReasons("NONSENSE")).isInstanceOf(InvalidRequestDataException.class);
        assertThatThrownBy(() -> service.rejectionReasons(null)).isInstanceOf(InvalidRequestDataException.class);
    }

    @Test
    void theRejectionReasonsComeFromTheAdminsList() {
        var reasons = List.of(new RejectionReason(UUID.randomUUID(), "Documents are incomplete"));
        when(reviews.reasons("ISSUANCE")).thenReturn(reasons);

        assertThat(service.rejectionReasons("ISSUANCE")).isEqualTo(reasons);
    }
}
