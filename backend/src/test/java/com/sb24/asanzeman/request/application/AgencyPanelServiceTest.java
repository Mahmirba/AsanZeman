package com.sb24.asanzeman.request.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.request.domain.model.AgencyInfo;
import com.sb24.asanzeman.request.domain.model.AgencyNotActiveException;
import com.sb24.asanzeman.request.domain.model.BranchSummary;
import com.sb24.asanzeman.request.domain.model.CustomerNotActualException;
import com.sb24.asanzeman.request.domain.model.CustomerSnapshot;
import com.sb24.asanzeman.request.domain.model.CustomerUnderageException;
import com.sb24.asanzeman.request.domain.model.InvalidRequestDataException;
import com.sb24.asanzeman.request.domain.model.NotBankCustomerException;
import com.sb24.asanzeman.request.domain.model.PageResult;
import com.sb24.asanzeman.request.domain.model.RequestDetails;
import com.sb24.asanzeman.request.domain.model.RequestNotFoundException;
import com.sb24.asanzeman.request.domain.model.RequestSearchCriteria;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.RequestSummary;
import com.sb24.asanzeman.request.domain.model.RequestType;
import com.sb24.asanzeman.request.domain.port.in.AgencyProfile;
import com.sb24.asanzeman.request.domain.port.in.CustomerLookupResult;
import com.sb24.asanzeman.request.domain.port.out.AgencyRegistryPort;
import com.sb24.asanzeman.request.domain.port.out.BranchRepositoryPort;
import com.sb24.asanzeman.request.domain.port.out.CustomerLookupPort;
import com.sb24.asanzeman.request.domain.port.out.RequestQueryPort;
import com.sb24.asanzeman.support.MutableClock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgencyPanelServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static final UUID AGENCY = UUID.randomUUID();
    private static final UUID SESSION = UUID.randomUUID();
    private static final String CODE = "0012345679";

    @Mock
    private AgencyRegistryPort agencies;
    @Mock
    private BranchRepositoryPort branches;
    @Mock
    private CustomerLookupPort customers;
    @Mock
    private RequestQueryPort queries;

    private AgencyPanelService service;

    @BeforeEach
    void setUp() {
        service = new AgencyPanelService(agencies, branches, customers, queries, new MutableClock(NOW));
    }

    private static RequestSearchCriteria criteria() {
        return new RequestSearchCriteria(AGENCY, null, null, null, null, null, null, null);
    }

    private static RequestSummary summary(int n) {
        return new RequestSummary(UUID.randomUUID(), "code" + n, RequestType.ISSUANCE,
                RequestStatus.REGISTERED_AT_AGENCY, CODE, null, "Ali", n, "0123", "Main", LocalDate.of(2027, 1, 20),
                NOW, "Sky", "09121234567");
    }

    // ---- profile -----------------------------------------------------------------------------------------------

    @Test
    void theProfileIsTheAgencyNameAndCode() {
        when(agencies.find(AGENCY)).thenReturn(Optional.of(new AgencyInfo(AGENCY, "0042", "Sky Travel", true)));

        assertThat(service.profile(AGENCY)).isEqualTo(new AgencyProfile("Sky Travel", "0042"));
    }

    @Test
    void anUnknownOrInactiveAgencyHasNoProfile() {
        when(agencies.find(AGENCY)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.profile(AGENCY)).isInstanceOf(AgencyNotActiveException.class);

        when(agencies.find(AGENCY)).thenReturn(Optional.of(new AgencyInfo(AGENCY, "0042", "Sky", false)));
        assertThatThrownBy(() -> service.profile(AGENCY)).isInstanceOf(AgencyNotActiveException.class);
    }

    // ---- branches ----------------------------------------------------------------------------------------------

    @Test
    void theBranchListPassesTheTypedLettersAsAPrefix() {
        List<BranchSummary> found = List.of(new BranchSummary("0123", "Valiasr", "Tehran"));
        when(branches.searchOpen("Val", AgencyPanelService.BRANCH_LIST_LIMIT)).thenReturn(found);

        assertThat(service.listOpen(" Val ")).isEqualTo(found);
    }

    @Test
    void persianDigitsInABranchCodeAreNormalised() {
        when(branches.searchOpen("012", AgencyPanelService.BRANCH_LIST_LIMIT)).thenReturn(List.of());

        assertThat(service.listOpen("۰۱۲")).isEmpty();
    }

    @Test
    void noQueryListsEveryOpenBranch() {
        when(branches.searchOpen("", AgencyPanelService.BRANCH_LIST_LIMIT)).thenReturn(List.of());

        assertThat(service.listOpen(null)).isEmpty();
        assertThat(service.listOpen("")).isEmpty();
    }

    // ---- customer lookup ---------------------------------------------------------------------------------------

    private static CustomerSnapshot customer(LocalDate birth, String personality) {
        return new CustomerSnapshot("555", CODE, "Ali", "Rezaei", "Ali Rezaei", "Reza", birth, "09121234567", null,
                null, null, personality, null);
    }

    @Test
    void theLookupShowsTheCustomerOfAnEligibleNationalCode() {
        when(customers.find(SESSION, CODE)).thenReturn(customer(LocalDate.of(1990, 5, 17), "ACTUAL"));

        assertThat(service.lookup(SESSION, "۰۰۱۲۳۴۵۶۷۹")).isEqualTo(new CustomerLookupResult("555", CODE, "Ali Rezaei",
                LocalDate.of(1990, 5, 17), "09121234567"));
    }

    @Test
    void theLookupAppliesTheSameRulesAsRegistration() {
        when(customers.find(SESSION, CODE)).thenReturn(customer(LocalDate.of(2015, 1, 1), "ACTUAL"));
        assertThatThrownBy(() -> service.lookup(SESSION, CODE)).isInstanceOf(CustomerUnderageException.class);

        when(customers.find(SESSION, CODE)).thenReturn(customer(LocalDate.of(1990, 5, 17), "LEGAL"));
        assertThatThrownBy(() -> service.lookup(SESSION, CODE)).isInstanceOf(CustomerNotActualException.class);
    }

    @Test
    void aCustomerYaghutDoesNotKnowIsNotABankCustomer() {
        when(customers.find(SESSION, CODE)).thenThrow(new NotBankCustomerException());

        assertThatThrownBy(() -> service.lookup(SESSION, CODE)).isInstanceOf(NotBankCustomerException.class);
    }

    @Test
    void aBadNationalCodeNeverReachesYaghut() {
        assertThatThrownBy(() -> service.lookup(SESSION, "1234567890"))
                .isInstanceOf(InvalidRequestDataException.class);
        verifyNoInteractions(customers);
    }

    // ---- search, export, details -------------------------------------------------------------------------------

    @Test
    void searchPassesThePageThrough() {
        var page = new PageResult<>(List.of(summary(1)), 1, 0, 20);
        when(queries.search(criteria(), 0, 20)).thenReturn(page);

        assertThat(service.search(criteria(), 0, 20)).isSameAs(page);
    }

    @Test
    void aBadPageOrSizeIsRefused() {
        assertThatThrownBy(() -> service.search(criteria(), -1, 20)).isInstanceOf(InvalidRequestDataException.class);
        assertThatThrownBy(() -> service.search(criteria(), 0, 0)).isInstanceOf(InvalidRequestDataException.class);
        assertThatThrownBy(() -> service.search(criteria(), 0, 201)).isInstanceOf(InvalidRequestDataException.class);
        verify(queries, never()).search(criteria(), 0, 1);
    }

    @Test
    void theExportReadsPageByPageUntilTheTotalIsReached() {
        int size = AgencyPanelService.EXPORT_PAGE_SIZE;
        when(queries.search(criteria(), 0, size)).thenReturn(new PageResult<>(List.of(summary(1), summary(2)),
                size + 1L, 0, size));
        when(queries.search(criteria(), 1, size)).thenReturn(new PageResult<>(List.of(summary(3)), size + 1L, 1,
                size));
        List<List<RequestSummary>> pages = new ArrayList<>();

        service.export(criteria(), pages::add);

        assertThat(pages).hasSize(2);
        assertThat(pages.get(0)).hasSize(2);
        assertThat(pages.get(1)).hasSize(1);
    }

    @Test
    void anEmptyExportCallsTheSinkNever() {
        when(queries.search(criteria(), 0, AgencyPanelService.EXPORT_PAGE_SIZE))
                .thenReturn(new PageResult<>(List.of(), 0, 0, AgencyPanelService.EXPORT_PAGE_SIZE));
        List<List<RequestSummary>> pages = new ArrayList<>();

        service.export(criteria(), pages::add);

        assertThat(pages).isEmpty();
        verify(queries, never()).search(criteria(), 1, AgencyPanelService.EXPORT_PAGE_SIZE);
    }

    @Test
    void anExportStopsIfAPageComesBackEmptyEvenWhenTheTotalPromisesMore() {
        int size = AgencyPanelService.EXPORT_PAGE_SIZE;
        when(queries.search(criteria(), 0, size)).thenReturn(new PageResult<>(List.of(summary(1)), 10_000L, 0, size));
        when(queries.search(criteria(), 1, size)).thenReturn(new PageResult<>(List.of(), 10_000L, 1, size));
        List<List<RequestSummary>> pages = new ArrayList<>();

        service.export(criteria(), pages::add);

        assertThat(pages).hasSize(1);
        verify(queries, never()).search(criteria(), 2, size);
    }

    @Test
    void detailsAreReturnedForTheAgenciesOwnRequest() {
        UUID id = UUID.randomUUID();
        var details = new RequestDetails(summary(1), List.of());
        when(queries.details(AGENCY, id)).thenReturn(Optional.of(details));

        assertThat(service.get(AGENCY, id)).isSameAs(details);
    }

    @Test
    void detailsOfAnUnknownOrForeignRequestAreNotFound() {
        UUID id = UUID.randomUUID();
        when(queries.details(AGENCY, id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(AGENCY, id)).isInstanceOf(RequestNotFoundException.class);
    }
}
