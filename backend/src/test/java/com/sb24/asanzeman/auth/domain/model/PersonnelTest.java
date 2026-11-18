package com.sb24.asanzeman.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PersonnelTest {

    private static final AccountName ALI = new AccountName("ali");

    @Test
    void keepsEveryField() {
        var p = new Personnel(ALI, "0123", "P1", "ali.u", "Main", "Manager", "Clerk");

        assertThat(p.accountName()).isEqualTo(ALI);
        assertThat(p.branchCode()).isEqualTo("0123");
        assertThat(p.prsnCode()).isEqualTo("P1");
        assertThat(p.personelUserName()).isEqualTo("ali.u");
        assertThat(p.branchName()).isEqualTo("Main");
        assertThat(p.position()).isEqualTo("Manager");
        assertThat(p.job()).isEqualTo("Clerk");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void aUserWithoutABranchIsRefused(String branch) {
        assertThatThrownBy(() -> new Personnel(ALI, branch, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("without a branch");
    }

    @Test
    void aUserWithoutANameIsRefused() {
        assertThatThrownBy(() -> new Personnel(null, "1", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("accountName is required");
    }
}
