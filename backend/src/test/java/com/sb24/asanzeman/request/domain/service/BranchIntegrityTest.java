package com.sb24.asanzeman.request.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.request.domain.model.BranchLink;
import java.util.List;
import org.junit.jupiter.api.Test;

class BranchIntegrityTest {

    private static BranchLink open(String code) {
        return new BranchLink(code, true, code);
    }

    private static BranchLink closedInFavourOf(String code, String parent) {
        return new BranchLink(code, true, parent);
    }

    @Test
    void aSoundTableHasNoProblems() {
        var branches = List.of(open("0456"), closedInFavourOf("0123", "0456"), closedInFavourOf("0124", "0456"),
                new BranchLink("0999", true, "0999"));

        assertThat(BranchIntegrity.problems(branches)).isEmpty();
        assertThat(BranchIntegrity.problems(List.of())).isEmpty();
    }

    @Test
    void anInactiveBranchWithNoReplacementIsReportedInPersian() {
        var branches = List.of(new BranchLink("0123", false, "0123"), open("0456"));

        assertThat(BranchIntegrity.problems(branches)).containsExactly("شعبه 0123 بسته شده اما شعبه جایگزین ندارد");
    }

    @Test
    void anInactiveBranchThatHasAReplacementIsFine() {
        var branches = List.of(new BranchLink("0123", false, "0456"), open("0456"));

        assertThat(BranchIntegrity.problems(branches)).isEmpty();
    }

    @Test
    void aCycleIsReportedForEveryBranchInIt() {
        var branches = List.of(closedInFavourOf("0123", "0456"), closedInFavourOf("0456", "0123"), open("0789"));

        assertThat(BranchIntegrity.problems(branches)).containsExactly(
                "branch 0123 is in a cycle of replacements", "branch 0456 is in a cycle of replacements");
    }

    @Test
    void aChainIsReported() {
        var branches = List.of(closedInFavourOf("0123", "0456"), closedInFavourOf("0456", "0789"), open("0789"));

        assertThat(BranchIntegrity.problems(branches)).containsExactly(
                "branch 0123 points to 0456, which was itself replaced by 0789 (a chain)");
    }

    @Test
    void aBranchPointingAtAnInactiveParentIsReported() {
        var branches = List.of(closedInFavourOf("0123", "0456"), new BranchLink("0456", false, "0456"));

        assertThat(BranchIntegrity.problems(branches)).containsExactly(
                "branch 0123 points to 0456, which is inactive", "شعبه 0456 بسته شده اما شعبه جایگزین ندارد");
    }

    @Test
    void aBranchPointingAtNothingIsReported() {
        assertThat(BranchIntegrity.problems(List.of(closedInFavourOf("0123", "0456")))).containsExactly(
                "branch 0123 points to 0456, which is not a branch");
    }

    @Test
    void aChainThatEndsInAMissingBranchIsReportedWithoutLooping() {
        var branches = List.of(closedInFavourOf("0123", "0456"), closedInFavourOf("0456", "0999"));

        assertThat(BranchIntegrity.problems(branches)).containsExactly(
                "branch 0123 points to 0456, which was itself replaced by 0999 (a chain)",
                "branch 0456 points to 0999, which is not a branch");
    }

    @Test
    void aBranchLeadingIntoACycleItIsNotPartOfIsAChain() {
        var branches = List.of(closedInFavourOf("0111", "0123"), closedInFavourOf("0123", "0456"),
                closedInFavourOf("0456", "0123"));

        assertThat(BranchIntegrity.problems(branches)).containsExactly(
                "branch 0111 points to 0123, which was itself replaced by 0456 (a chain)",
                "branch 0123 is in a cycle of replacements", "branch 0456 is in a cycle of replacements");
    }
}
