package com.sb24.asanzeman.auth.domain.port.out;

import com.sb24.asanzeman.auth.domain.model.AccountName;
import com.sb24.asanzeman.auth.domain.model.Personnel;
import java.util.Optional;

/** The bank's personnel directory (personnelInfo): who a user is and which branch they belong to. */
public interface PersonnelDirectoryPort {

    /**
     * @return the user, or empty when the directory does not know them (HTTP 404)
     * @throws com.sb24.asanzeman.shared.model.TemporarilyUnavailableException on a technical failure: nobody is let
     *         in without a branch
     */
    Optional<Personnel> find(AccountName accountName);
}
