package com.sb24.asanzeman.request.application;

import com.sb24.asanzeman.request.domain.model.AgencyInfo;
import com.sb24.asanzeman.request.domain.model.AgencyNotActiveException;
import com.sb24.asanzeman.request.domain.model.BranchSummary;
import com.sb24.asanzeman.request.domain.model.CustomerSnapshot;
import com.sb24.asanzeman.request.domain.model.InvalidRequestDataException;
import com.sb24.asanzeman.request.domain.model.PageResult;
import com.sb24.asanzeman.request.domain.model.RequestDetails;
import com.sb24.asanzeman.request.domain.model.RequestNotFoundException;
import com.sb24.asanzeman.request.domain.model.RequestSearchCriteria;
import com.sb24.asanzeman.request.domain.model.RequestSummary;
import com.sb24.asanzeman.request.domain.port.in.AgencyProfile;
import com.sb24.asanzeman.request.domain.port.in.AgencyProfileUseCase;
import com.sb24.asanzeman.request.domain.port.in.CustomerLookupResult;
import com.sb24.asanzeman.request.domain.port.in.GetRequestUseCase;
import com.sb24.asanzeman.request.domain.port.in.ListBranchesUseCase;
import com.sb24.asanzeman.request.domain.port.in.LookupCustomerUseCase;
import com.sb24.asanzeman.request.domain.port.in.SearchRequestsUseCase;
import com.sb24.asanzeman.request.domain.port.out.AgencyRegistryPort;
import com.sb24.asanzeman.request.domain.port.out.BranchRepositoryPort;
import com.sb24.asanzeman.request.domain.port.out.CustomerLookupPort;
import com.sb24.asanzeman.request.domain.port.out.RequestQueryPort;
import com.sb24.asanzeman.shared.model.NationalCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/**
 * The read side of the agency panel (SRS UC-AZS-8 and UC-AZS-13): profile, branch list, customer lookup, tracking list,
 * export and details. Nothing here changes data. The Yaghut lookup is not in a database transaction, and the
 * database reads are single statements, so none of it needs one.
 */
@Service
public class AgencyPanelService implements AgencyProfileUseCase, ListBranchesUseCase, LookupCustomerUseCase,
        SearchRequestsUseCase, GetRequestUseCase {

    static final int BRANCH_LIST_LIMIT = 500;
    static final int EXPORT_PAGE_SIZE = 500;

    private final AgencyRegistryPort agencies;
    private final BranchRepositoryPort branches;
    private final CustomerLookupPort customers;
    private final RequestQueryPort queries;
    private final Clock clock;

    public AgencyPanelService(AgencyRegistryPort agencies, BranchRepositoryPort branches,
            CustomerLookupPort customers, RequestQueryPort queries, Clock clock) {
        this.agencies = agencies;
        this.branches = branches;
        this.customers = customers;
        this.queries = queries;
        this.clock = clock;
    }

    @Override
    public AgencyProfile profile(UUID agencyId) {
        AgencyInfo agency = agencies.find(agencyId).filter(AgencyInfo::active)
                .orElseThrow(AgencyNotActiveException::new);
        return new AgencyProfile(agency.name(), agency.code());
    }

    @Override
    public List<BranchSummary> listOpen(String query) {
        return branches.searchOpen(query == null ? "" : NationalCode.normalise(query), BRANCH_LIST_LIMIT);
    }

    @Override
    public CustomerLookupResult lookup(UUID sessionId, String nationalCode) {
        NationalCode code = NationalCode.parse(nationalCode)
                .orElseThrow(() -> new InvalidRequestDataException("the customer national code is not valid"));
        CustomerSnapshot customer = customers.find(sessionId, code.value());
        LocalDate today = clock.instant().atZone(TrackingCodeGenerator.TEHRAN).toLocalDate();
        customer.requireEligible(today);
        return new CustomerLookupResult(customer.cif(), customer.code(), customer.fullName(), customer.birthDate(),
                customer.mobile());
    }

    @Override
    public PageResult<RequestSummary> search(RequestSearchCriteria criteria, int page, int size) {
        if (page < 0) {
            throw new InvalidRequestDataException("the page starts at 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestDataException("the page size is 1 to " + MAX_PAGE_SIZE);
        }
        return queries.search(criteria, page, size);
    }

    @Override
    public void export(RequestSearchCriteria criteria, Consumer<List<RequestSummary>> sink) {
        int page = 0;
        PageResult<RequestSummary> current;
        do {
            current = queries.search(criteria, page++, EXPORT_PAGE_SIZE);
            if (!current.items().isEmpty()) {
                sink.accept(current.items());
            }
        } while ((long) page * EXPORT_PAGE_SIZE < current.total() && !current.items().isEmpty());
    }

    @Override
    public RequestDetails get(UUID agencyId, UUID requestId) {
        return queries.details(agencyId, requestId).orElseThrow(RequestNotFoundException::new);
    }
}
