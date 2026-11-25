package com.sb24.asanzeman.auth.domain.port.out;

import com.sb24.asanzeman.auth.domain.model.Agency;
import java.util.Optional;

/** The {@code agency} table, as the login needs it. */
public interface AgencyDirectoryPort {

    Optional<Agency> findByCif(String cif);

    /** The agency by its id, as the access token names it, to check on a refresh that it may still log in. */
    Optional<Agency> findById(java.util.UUID id);
}
