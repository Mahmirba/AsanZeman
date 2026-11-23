package com.sb24.asanzeman.request.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sb24.asanzeman.request.domain.model.AgencyData;
import com.sb24.asanzeman.request.domain.model.AgencyDuplicateException;
import com.sb24.asanzeman.request.domain.model.ReasonData;
import com.sb24.asanzeman.support.H2Auth;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JdbcAdminReferenceTest {

    private static final Instant T0 = Instant.parse("2026-11-23T10:00:00Z");

    private final JdbcAdminReference reference = new JdbcAdminReference(H2Auth.newDatabase());

    @Test
    void anAgencyIsInsertedReadBackAndListedByCode() {
        UUID sky = UUID.randomUUID();
        UUID air = UUID.randomUUID();
        reference.insertAgency(sky, new AgencyData("9002", "0050", "Sky", true, "09121234567"), "root", T0);
        reference.insertAgency(air, new AgencyData("9001", "0042", "Air", false, null), "root", T0);

        assertThat(reference.findAgency(sky).orElseThrow().data())
                .isEqualTo(new AgencyData("9002", "0050", "Sky", true, "09121234567"));
        assertThat(reference.agencies()).extracting(a -> a.data().code()).containsExactly("0042", "0050");
        assertThat(reference.findAgency(UUID.randomUUID())).isEmpty();
    }

    @Test
    void anAgencyIsUpdatedIncludingSwitchingItOffAndRemovingItsMobile() {
        UUID id = UUID.randomUUID();
        reference.insertAgency(id, new AgencyData("9001", "0042", "Sky", true, "09121234567"), "root", T0);

        reference.updateAgency(id, new AgencyData("9001", "0043", "Sky Travel", false, null), "ali", T0.plusSeconds(5));

        assertThat(reference.findAgency(id).orElseThrow().data())
                .isEqualTo(new AgencyData("9001", "0043", "Sky Travel", false, null));
    }

    @Test
    void aTakenCustomerNumberOrCodeIsADuplicateOnInsertAndOnUpdate() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        reference.insertAgency(first, new AgencyData("9001", "0042", "A", true, null), "root", T0);
        reference.insertAgency(second, new AgencyData("9002", "0043", "B", true, null), "root", T0);

        assertThatThrownBy(() -> reference.insertAgency(UUID.randomUUID(),
                new AgencyData("9001", "0099", "C", true, null), "root", T0))
                .isInstanceOf(AgencyDuplicateException.class);
        assertThatThrownBy(() -> reference.insertAgency(UUID.randomUUID(),
                new AgencyData("9099", "0042", "C", true, null), "root", T0))
                .isInstanceOf(AgencyDuplicateException.class);
        assertThatThrownBy(() -> reference.updateAgency(second, new AgencyData("9002", "0042", "B", true, null),
                "root", T0)).isInstanceOf(AgencyDuplicateException.class);
        assertThatThrownBy(() -> reference.updateAgency(second, new AgencyData("9001", "0043", "B", true, null),
                "root", T0)).isInstanceOf(AgencyDuplicateException.class);
    }

    @Test
    void reasonsAreInsertedUpdatedAndListedByKindThenOrder() {
        UUID second = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID revocation = UUID.randomUUID();
        reference.insertReason(second, new ReasonData("ISSUANCE", "Second", true, 2), "root", T0);
        reference.insertReason(first, new ReasonData("ISSUANCE", "First", true, 1), "root", T0);
        reference.insertReason(revocation, new ReasonData("REVOCATION", "Still in use", true, 0), "root", T0);

        reference.updateReason(second, new ReasonData("ISSUANCE", "Second, off", false, 3), "ali", T0.plusSeconds(5));

        assertThat(reference.reasons()).extracting(r -> r.data().text())
                .containsExactly("First", "Second, off", "Still in use");
        assertThat(reference.findReason(second).orElseThrow().data())
                .isEqualTo(new ReasonData("ISSUANCE", "Second, off", false, 3));
        assertThat(reference.findReason(UUID.randomUUID())).isEmpty();
    }
}
