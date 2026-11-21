package com.sb24.asanzeman.request.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.request.domain.model.BranchNotAvailableException;
import com.sb24.asanzeman.request.domain.model.Companion;
import com.sb24.asanzeman.request.domain.model.CustomerSnapshot;
import com.sb24.asanzeman.request.domain.model.InvalidRequestDataException;
import com.sb24.asanzeman.request.domain.model.NotBankCustomerException;
import com.sb24.asanzeman.request.domain.model.RequestStatus;
import com.sb24.asanzeman.request.domain.port.in.CompanionInput;
import com.sb24.asanzeman.request.domain.port.in.RegisterRequestCommand;
import com.sb24.asanzeman.request.domain.port.in.RegisteredRequest;
import com.sb24.asanzeman.request.domain.port.out.BranchRepositoryPort;
import com.sb24.asanzeman.request.domain.port.out.CustomerLookupPort;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegisterRequestServiceTest {

    private static final UUID SESSION = UUID.randomUUID();
    private static final RegisteredRequest REGISTERED = new RegisteredRequest(UUID.randomUUID(), "012300420508300001",
            RequestStatus.REGISTERED_AT_AGENCY);

    @Mock
    private BranchRepositoryPort branches;
    @Mock
    private CustomerLookupPort customers;
    @Mock
    private RequestRegistrar registrar;

    private RegisterRequestService service;

    @BeforeEach
    void setUp() {
        service = new RegisterRequestService(branches, customers, registrar);
    }

    private static RegisterRequestCommand command(String nationalCode, List<CompanionInput> companions) {
        return new RegisterRequestCommand(UUID.randomUUID(), SESSION, "Sky Travel", nationalCode, "0123", 1000,
                LocalDate.of(2027, 1, 20), companions, "10.0.0.5", "Firefox");
    }

    private static CustomerSnapshot customer(String code) {
        return new CustomerSnapshot("555", code, "Ali", "Rezaei", "Ali Rezaei", "Reza", LocalDate.of(1990, 5, 17),
                "09121234567", null, null, null, "ACTUAL", null);
    }

    @Test
    void registersTheCustomerYaghutKnowsAfterCheckingTheBranch() {
        when(branches.isOpen("0123")).thenReturn(true);
        when(customers.find(SESSION, "0012345679")).thenReturn(customer("0012345679"));
        when(registrar.persist(any(), any(), any())).thenReturn(REGISTERED);

        assertThat(service.register(command("0012345679", List.of()))).isEqualTo(REGISTERED);
    }

    @Test
    void persianDigitsInTheNationalCodeAreNormalisedBeforeTheLookup() {
        when(branches.isOpen("0123")).thenReturn(true);
        when(customers.find(SESSION, "0012345679")).thenReturn(customer("0012345679"));
        when(registrar.persist(any(), any(), any())).thenReturn(REGISTERED);

        service.register(command("۰۰۱۲۳۴۵۶۷۹", List.of()));

        verify(customers).find(SESSION, "0012345679");
    }

    @Test
    void companionNationalCodesAreNormalisedAndPassedOn() {
        when(branches.isOpen("0123")).thenReturn(true);
        when(customers.find(SESSION, "0012345679")).thenReturn(customer("0012345679"));
        when(registrar.persist(any(), any(), any())).thenReturn(REGISTERED);

        service.register(command("0012345679",
                List.of(new CompanionInput("Sara", "۰۰۱۲۳۴۵۶۷۹", LocalDate.of(2015, 1, 1), "P1"))));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Companion>> companions = ArgumentCaptor.forClass(List.class);
        verify(registrar).persist(any(), any(), companions.capture());
        assertThat(companions.getValue()).containsExactly(
                new Companion("Sara", "0012345679", LocalDate.of(2015, 1, 1), "P1"));
    }

    @Test
    void aBadNationalCodeStopsBeforeAnyLookup() {
        assertThatThrownBy(() -> service.register(command("1234567890", List.of())))
                .isInstanceOf(InvalidRequestDataException.class);
        assertThatThrownBy(() -> service.register(command(null, List.of())))
                .isInstanceOf(InvalidRequestDataException.class);
        verifyNoInteractions(branches, customers, registrar);
    }

    @Test
    void aBadCompanionStopsBeforeAnyLookup() {
        assertThatThrownBy(() -> service.register(command("0012345679",
                List.of(new CompanionInput("Sara", "1234567890", LocalDate.of(2015, 1, 1), "P1")))))
                .isInstanceOf(InvalidRequestDataException.class);
        verifyNoInteractions(branches, customers, registrar);
    }

    @Test
    void aBranchThatIsNotOpenIsRefusedBeforeYaghutIsCalled() {
        when(branches.isOpen("0123")).thenReturn(false);

        assertThatThrownBy(() -> service.register(command("0012345679", List.of())))
                .isInstanceOf(BranchNotAvailableException.class);
        verifyNoInteractions(customers, registrar);
    }

    @Test
    void aCustomerYaghutDoesNotKnowIsRefused() {
        when(branches.isOpen("0123")).thenReturn(true);
        when(customers.find(SESSION, "0012345679")).thenThrow(new NotBankCustomerException());

        assertThatThrownBy(() -> service.register(command("0012345679", List.of())))
                .isInstanceOf(NotBankCustomerException.class);
        verifyNoInteractions(registrar);
    }

    @Test
    void anAnswerForAnotherNationalCodeIsNeverRegistered() {
        when(branches.isOpen("0123")).thenReturn(true);
        when(customers.find(SESSION, "0012345679")).thenReturn(customer("1000000011"));

        assertThatThrownBy(() -> service.register(command("0012345679", List.of())))
                .isInstanceOf(TemporarilyUnavailableException.class);
        verifyNoInteractions(registrar);
    }
}
