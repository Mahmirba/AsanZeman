package com.sb24.asanzeman.auth.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.auth.domain.model.BankAuthentication;
import com.sb24.asanzeman.auth.domain.model.BankRole;
import com.sb24.asanzeman.integration.aaa.AaaClient;
import com.sb24.asanzeman.integration.aaa.AaaRoleResult;
import com.sb24.asanzeman.integration.aaa.config.AaaProperties;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AaaBankAuthenticatorAdapterTest {

    @Mock
    private AaaClient client;

    private static AaaProperties properties(Map<String, String> roleMap) {
        return new AaaProperties("https://aaa.test", "app", "store", "tech", "pw", roleMap,
                new AaaProperties.Wss(false), Duration.ofSeconds(3), Duration.ofSeconds(10),
                new AaaProperties.Tls(false));
    }

    private BankAuthentication authenticate(Map<String, String> roleMap) {
        return new AaaBankAuthenticatorAdapter(client, properties(roleMap)).authenticate("ali@sb24.ir", "pw");
    }

    @Test
    void mapsBranchAndAdminAndIgnoresEveryOtherRole() {
        when(client.getRoleWindows("ali@sb24.ir", "pw")).thenReturn(
                new AaaRoleResult(List.of(), List.of("Branch", "Admin", "Supervisor", "Signers")));

        BankAuthentication result = authenticate(Map.of("Branch", "BRANCH", "Admin", "ADMIN"));

        assertThat(result.accepted()).isTrue();
        assertThat(result.roles()).containsExactlyInAnyOrder(BankRole.BRANCH, BankRole.ADMIN);
        assertThat(result.mayEnter()).isTrue();
    }

    @Test
    void theMappingIsConfigurableAndTolerantOfCaseAndSpaces() {
        when(client.getRoleWindows("ali@sb24.ir", "pw"))
                .thenReturn(new AaaRoleResult(List.of(), List.of("Supervisor")));

        BankAuthentication result = authenticate(Map.of("Supervisor", " admin "));

        assertThat(result.roles()).containsExactly(BankRole.ADMIN);
    }

    @Test
    void anyDetailErrorMeansTheCredentialsWereRefused() {
        when(client.getRoleWindows("ali@sb24.ir", "pw")).thenReturn(new AaaRoleResult(
                List.of(new AaaRoleResult.AaaError(101, "bad password", "inner")), List.of("Branch")));

        BankAuthentication result = authenticate(Map.of("Branch", "BRANCH"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.roles()).isEmpty();
    }

    @Test
    void aUserWithNoMappedRoleIsAcceptedButMayNotEnter() {
        when(client.getRoleWindows("ali@sb24.ir", "pw"))
                .thenReturn(new AaaRoleResult(List.of(), List.of("Amin")));

        BankAuthentication result = authenticate(Map.of("Branch", "BRANCH"));

        assertThat(result.accepted()).isTrue();
        assertThat(result.mayEnter()).isFalse();
    }

    @Test
    void aMappingToAnUnknownRoleIsIgnoredNotFatal() {
        when(client.getRoleWindows("ali@sb24.ir", "pw"))
                .thenReturn(new AaaRoleResult(List.of(), List.of("Branch", "Admin")));

        BankAuthentication result = authenticate(Map.of("Branch", "BRANCHH", "Admin", "ADMIN"));

        assertThat(result.roles()).containsExactly(BankRole.ADMIN);
    }

    @Test
    void technicalFailuresPassThrough() {
        var down = new TemporarilyUnavailableException("AAA", new RuntimeException("timeout"));
        when(client.getRoleWindows("ali@sb24.ir", "pw")).thenThrow(down);

        assertThatThrownBy(() -> authenticate(Map.of())).isSameAs(down);
    }
}
