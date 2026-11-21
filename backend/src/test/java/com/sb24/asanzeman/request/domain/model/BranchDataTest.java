package com.sb24.asanzeman.request.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BranchDataTest {

    @Test
    void theCodeIsKeptExactlyAsGivenWithItsLeadingZero() {
        assertThat(new BranchData("0123", "Main", null, null, null, null, null).code()).isEqualTo("0123");
    }

    @Test
    void aBranchNeedsACodeAndAName() {
        assertThatThrownBy(() -> new BranchData(null, "Main", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BranchData(" ", "Main", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BranchData("1", null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BranchData("1", " ", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theSyncSummaryNamesEveryCount() {
        assertThat(new BranchSyncResult(3, 2, 1).summary()).isEqualTo("inserted 3, updated 2, deactivated 1");
    }
}
