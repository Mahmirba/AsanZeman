package com.sb24.asanzeman.auth.domain.port.out;

import com.sb24.asanzeman.auth.domain.model.AppUser;
import com.sb24.asanzeman.auth.domain.model.Personnel;
import com.sb24.asanzeman.auth.domain.model.Role;
import java.time.Instant;

/** The {@code app_user} table: bank users, refreshed from personnelInfo at every login (D33). */
public interface AppUserRepositoryPort {

    /** Creates the user on first login, otherwise updates the details, branch and role. */
    AppUser upsert(Personnel personnel, Role role, Instant now);
}
