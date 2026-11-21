package com.sb24.asanzeman.auth.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.auth.domain.model.AccountName;
import com.sb24.asanzeman.integration.personnel.PersonnelInfo;
import com.sb24.asanzeman.integration.personnel.PersonnelInfoClient;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersonnelDirectoryAdapterTest {

    private static final AccountName ALI = new AccountName("ali");

    @Mock
    private PersonnelInfoClient client;

    @Test
    void mapsTheDirectoryAnswerToTheDomainModel() {
        when(client.find("ali")).thenReturn(
                Optional.of(new PersonnelInfo("0123", "P1", "ali.u", "Main", "Manager", "Clerk")));

        var personnel = new PersonnelDirectoryAdapter(client).find(ALI).orElseThrow();

        assertThat(personnel.accountName()).isEqualTo(ALI);
        assertThat(personnel.branchCode()).isEqualTo("0123");
        assertThat(personnel.prsnCode()).isEqualTo("P1");
        assertThat(personnel.branchName()).isEqualTo("Main");
        assertThat(personnel.position()).isEqualTo("Manager");
        assertThat(personnel.job()).isEqualTo("Clerk");
    }

    @Test
    void anUnknownUserIsEmpty() {
        when(client.find("ali")).thenReturn(Optional.empty());

        assertThat(new PersonnelDirectoryAdapter(client).find(ALI)).isEmpty();
    }

    @Test
    void technicalFailuresPassThrough() {
        var down = new TemporarilyUnavailableException("personnelInfo", new RuntimeException("timeout"));
        when(client.find("ali")).thenThrow(down);

        assertThatThrownBy(() -> new PersonnelDirectoryAdapter(client).find(ALI)).isSameAs(down);
    }
}
