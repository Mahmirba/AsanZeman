package com.sb24.asanzeman.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BankAuthenticationTest {

    @Test
    void rejectedHasNoRolesAndMayNotEnter() {
        var rejected = BankAuthentication.rejected();

        assertThat(rejected.accepted()).isFalse();
        assertThat(rejected.roles()).isEmpty();
        assertThat(rejected.mayEnter()).isFalse();
    }

    @Test
    void acceptedWithARoleMayEnter() {
        var accepted = BankAuthentication.accepted(Set.of(BankRole.BRANCH));

        assertThat(accepted.accepted()).isTrue();
        assertThat(accepted.roles()).containsExactly(BankRole.BRANCH);
        assertThat(accepted.mayEnter()).isTrue();
    }

    @Test
    void acceptedWithoutARoleMayNotEnter() {
        assertThat(BankAuthentication.accepted(Set.of()).mayEnter()).isFalse();
    }

    @Test
    void rolesAreCopied() {
        var source = EnumSet.of(BankRole.ADMIN);
        var accepted = BankAuthentication.accepted(source);
        source.clear();

        assertThat(accepted.roles()).containsExactly(BankRole.ADMIN);
        assertThat(BankRole.values()).containsExactly(BankRole.BRANCH, BankRole.ADMIN);
    }
}
