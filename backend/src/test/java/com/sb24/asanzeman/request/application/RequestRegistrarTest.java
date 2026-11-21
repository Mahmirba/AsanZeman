package com.sb24.asanzeman.request.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.request.domain.model.AgencyInfo;
import com.sb24.asanzeman.request.domain.model.AgencyNotActiveException;
import com.sb24.asanzeman.request.domain.model.Companion;
import com.sb24.asanzeman.request.domain.model.CustomerSnapshot;
import com.sb24.asanzeman.request.domain.model.CustomerUnderageException;
import com.sb24.asanzeman.request.domain.model.DuplicateRegistrationException;
import com.sb24.asanzeman.request.domain.model.InvalidRequestDataException;
import com.sb24.asanzeman.request.domain.model.Request;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.model.TrackingCode;
import com.sb24.asanzeman.request.domain.port.in.RegisterRequestCommand;
import com.sb24.asanzeman.request.domain.port.in.RegisteredRequest;
import com.sb24.asanzeman.request.domain.port.out.AgencyRegistryPort;
import com.sb24.asanzeman.request.domain.port.out.RegistrationNotifierPort;
import com.sb24.asanzeman.request.domain.port.out.RequestRepositoryPort;
import com.sb24.asanzeman.shared.audit.domain.model.AuditEntry;
import com.sb24.asanzeman.shared.audit.domain.port.in.RecordAuditUseCase;
import com.sb24.asanzeman.shared.model.JalaliDate;
import com.sb24.asanzeman.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class RequestRegistrarTest {

    private static final Instant NOW = Instant.parse("2026-11-21T10:00:00Z");
    private static final UUID AGENCY_ID = UUID.randomUUID();
    private static final AgencyInfo AGENCY = new AgencyInfo(AGENCY_ID, "0042", "Sky Travel", true);
    private static final TrackingCode CODE = TrackingCode.of("0123", "0042", JalaliDate.of(1405, 8, 30), 1);

    @Mock
    private AgencyRegistryPort agencies;
    @Mock
    private RequestRepositoryPort requests;
    @Mock
    private TrackingCodeGenerator trackingCodes;
    @Mock
    private RegistrationNotifierPort notifier;
    @Mock
    private RecordAuditUseCase audit;

    private RequestRegistrar registrar;

    @BeforeEach
    void setUp() {
        registrar = new RequestRegistrar(agencies, requests, trackingCodes, notifier, audit, new MutableClock(NOW),
                new RegistrationSettings(Duration.ofSeconds(10)));
    }

    private static RegisterRequestCommand command(long amount) {
        return new RegisterRequestCommand(AGENCY_ID, UUID.randomUUID(), "Sky Travel", "1000000011", "0123", amount,
                LocalDate.of(2027, 1, 20), List.of(), "10.0.0.5", "Firefox");
    }

    private static CustomerSnapshot customer(LocalDate birth) {
        return new CustomerSnapshot("555", "1000000011", "Ali", "Rezaei", "Ali Rezaei", "Reza", birth,
                "09121234567", null, null, null, "ACTUAL", null);
    }

    private RegisteredRequest persist(long amount) {
        return registrar.persist(command(amount), customer(LocalDate.of(1990, 5, 17)), List.of());
    }

    private void everythingAllows() {
        when(agencies.lock(AGENCY_ID)).thenReturn(Optional.of(AGENCY));
        when(requests.existsSince(any(), anyString(), any())).thenReturn(false);
        when(trackingCodes.next("0123", "0042")).thenReturn(CODE);
    }

    @Test
    void registersInTheDocumentedOrderAndReturnsTheTrackingCode() {
        everythingAllows();

        RegisteredRequest result = persist(1000);

        assertThat(result.trackingCode()).isEqualTo(CODE.value());
        assertThat(result.status()).isEqualTo(RequestStatus.REGISTERED_AT_AGENCY);
        InOrder order = inOrder(agencies, requests, trackingCodes, notifier, audit);
        order.verify(agencies).lock(AGENCY_ID);
        order.verify(requests).existsSince(AGENCY_ID, "1000000011", NOW.minusSeconds(10));
        order.verify(trackingCodes).next("0123", "0042");
        order.verify(requests).insert(any(Request.class));
        order.verify(notifier).customerRegistered(result.id(), "09121234567", "Sky Travel");
        order.verify(audit).record(any(AuditEntry.class));
    }

    @Test
    void theStoredRequestCarriesTheCommandAndTheSnapshot() {
        everythingAllows();

        RegisteredRequest result = persist(1000);

        ArgumentCaptor<Request> saved = ArgumentCaptor.forClass(Request.class);
        verify(requests).insert(saved.capture());
        Request request = saved.getValue();
        assertThat(request.id()).isEqualTo(result.id());
        assertThat(request.agencyId()).isEqualTo(AGENCY_ID);
        assertThat(request.branchCode()).isEqualTo("0123");
        assertThat(request.amount().value()).isEqualTo(1000);
        assertThat(request.registeredBy()).isEqualTo("Sky Travel");
        assertThat(request.registeredAt()).isEqualTo(NOW);
        assertThat(request.customer().code()).isEqualTo("1000000011");
        assertThat(request.companions()).isEqualTo(List.<Companion>of());
    }

    @Test
    void theAuditRowNamesTheAgencyTheRequestAndTheCaller() {
        everythingAllows();

        RegisteredRequest result = persist(1000);

        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(audit).record(entry.capture());
        assertThat(entry.getValue()).extracting(AuditEntry::userId, AuditEntry::userName, AuditEntry::userRole,
                AuditEntry::entityType, AuditEntry::entityId, AuditEntry::action, AuditEntry::newValue,
                AuditEntry::ipAddress, AuditEntry::userAgent).containsExactly(AGENCY_ID, "Sky Travel", "AGENCY",
                "REQUEST", result.id(), "REGISTER", "REGISTERED_AT_AGENCY", "10.0.0.5", "Firefox");
    }

    @Test
    void aRegistrationJustAfterMidnightInTehranStillUsesTodayForTheAgeCheck() {
        everythingAllows();
        var late = new RequestRegistrar(agencies, requests, trackingCodes, notifier, audit,
                new MutableClock(Instant.parse("2026-11-21T20:45:00Z")),
                new RegistrationSettings(Duration.ofSeconds(10)));

        // turns 18 on 22 November (Tehran date at that moment): accepted
        assertThat(late.persist(command(1), customer(LocalDate.of(2008, 11, 22)), List.of()).status())
                .isEqualTo(RequestStatus.REGISTERED_AT_AGENCY);
    }

    @Test
    void aSecondRegistrationInsideTheWindowIsRejectedBeforeAnyNumberIsTaken() {
        when(agencies.lock(AGENCY_ID)).thenReturn(Optional.of(AGENCY));
        when(requests.existsSince(any(), anyString(), any())).thenReturn(true);

        assertThatThrownBy(() -> persist(1000)).isInstanceOf(DuplicateRegistrationException.class);
        verifyNoInteractions(trackingCodes, notifier, audit);
        verify(requests).existsSince(AGENCY_ID, "1000000011", NOW.minusSeconds(10));
        verifyNoMoreInteractions(requests);
    }

    @Test
    void anUnknownOrInactiveAgencyIsRefusedBeforeAnythingElse() {
        when(agencies.lock(AGENCY_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> persist(1000)).isInstanceOf(AgencyNotActiveException.class);

        when(agencies.lock(AGENCY_ID)).thenReturn(Optional.of(new AgencyInfo(AGENCY_ID, "0042", "Sky", false)));
        assertThatThrownBy(() -> persist(1000)).isInstanceOf(AgencyNotActiveException.class);
        verifyNoInteractions(requests, trackingCodes, notifier, audit);
    }

    @Test
    void aNegativeAmountIsRefusedBeforeTheDatabaseIsTouched() {
        assertThatThrownBy(() -> persist(-1)).isInstanceOf(InvalidRequestDataException.class);
        verifyNoInteractions(agencies, requests, trackingCodes, notifier, audit);
    }

    @Test
    void aBrokenRuleSendsNoSmsAndNoAudit() {
        everythingAllows();

        assertThatThrownBy(() -> registrar.persist(command(1000), customer(LocalDate.of(2015, 1, 1)), List.of()))
                .isInstanceOf(CustomerUnderageException.class);

        verifyNoInteractions(notifier, audit);
        verify(requests).existsSince(AGENCY_ID, "1000000011", NOW.minusSeconds(10));
        verifyNoMoreInteractions(requests);
    }

    @Test
    void everythingRunsInOneTransaction() {
        assertThat(RequestRegistrar.class.getMethods()).filteredOn(m -> m.getName().equals("persist"))
                .allSatisfy(m -> assertThat(m.getAnnotation(Transactional.class)).isNotNull());
    }
}
