package com.sb24.asanzeman.request.domain.port.in;

import com.sb24.asanzeman.request.domain.model.AdminAgency;
import com.sb24.asanzeman.request.domain.model.AdminReason;
import com.sb24.asanzeman.request.domain.model.AgencyData;
import com.sb24.asanzeman.request.domain.model.ReasonData;
import java.util.List;
import java.util.UUID;

/**
 * The reference data an admin edits: agencies and rejection reasons. Branches are not here: they come only from Yaghut
 * and the DBA scripts (D32). Every change is audited.
 */
public interface AdminReferenceUseCase {

    List<AdminAgency> agencies();

    /** @throws com.sb24.asanzeman.request.domain.model.AgencyDuplicateException the customer number or code is taken */
    AdminAgency createAgency(AdminActor actor, AgencyData data);

    /**
     * @throws com.sb24.asanzeman.request.domain.model.AdminItemNotFoundException no such agency
     * @throws com.sb24.asanzeman.request.domain.model.AgencyDuplicateException the customer number or code is taken
     */
    AdminAgency updateAgency(AdminActor actor, UUID id, AgencyData data);

    /** Every reason, both kinds, switched off ones too. */
    List<AdminReason> reasons();

    AdminReason createReason(AdminActor actor, ReasonData data);

    /** @throws com.sb24.asanzeman.request.domain.model.AdminItemNotFoundException no such reason */
    AdminReason updateReason(AdminActor actor, UUID id, ReasonData data);
}
