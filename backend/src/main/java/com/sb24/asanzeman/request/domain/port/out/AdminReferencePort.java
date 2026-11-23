package com.sb24.asanzeman.request.domain.port.out;

import com.sb24.asanzeman.request.domain.model.AdminAgency;
import com.sb24.asanzeman.request.domain.model.AdminReason;
import com.sb24.asanzeman.request.domain.model.AgencyData;
import com.sb24.asanzeman.request.domain.model.ReasonData;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The agency and rejection reason tables, as the admin edits them. */
public interface AdminReferencePort {

    List<AdminAgency> agencies();

    Optional<AdminAgency> findAgency(UUID id);

    /** @throws com.sb24.asanzeman.request.domain.model.AgencyDuplicateException the customer number or code is taken */
    void insertAgency(UUID id, AgencyData data, String by, Instant at);

    /** @throws com.sb24.asanzeman.request.domain.model.AgencyDuplicateException the customer number or code is taken */
    void updateAgency(UUID id, AgencyData data, String by, Instant at);

    List<AdminReason> reasons();

    Optional<AdminReason> findReason(UUID id);

    void insertReason(UUID id, ReasonData data, String by, Instant at);

    void updateReason(UUID id, ReasonData data, String by, Instant at);
}
