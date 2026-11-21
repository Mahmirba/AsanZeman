package com.sb24.asanzeman.auth.domain.model;

import java.util.Set;

/**
 * The outcome of checking a bank user's name and password (6.2). {@code roles} holds only the Asan Zeman roles that
 * AAA's role names map to; any other AAA role is ignored. A user who is accepted but has no role cannot log in.
 *
 * @param accepted whether AAA accepted the user name and password
 * @param roles    the mapped roles, empty when rejected
 */
public record BankAuthentication(boolean accepted, Set<BankRole> roles) {

    public BankAuthentication {
        roles = Set.copyOf(roles);
    }

    public static BankAuthentication rejected() {
        return new BankAuthentication(false, Set.of());
    }

    public static BankAuthentication accepted(Set<BankRole> roles) {
        return new BankAuthentication(true, roles);
    }

    /** Accepted by AAA and allowed into Asan Zeman: at least one role maps. */
    public boolean mayEnter() {
        return accepted && !roles.isEmpty();
    }
}
