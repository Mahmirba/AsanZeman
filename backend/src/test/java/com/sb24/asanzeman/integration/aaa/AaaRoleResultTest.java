package com.sb24.asanzeman.integration.aaa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AaaRoleResultTest {

    @Test
    void copiesItsLists() {
        List<String> roles = new ArrayList<>(List.of("Branch"));
        var result = new AaaRoleResult(List.of(new AaaRoleResult.AaaError(1, "d", "i")), roles);
        roles.clear();

        assertThat(result.roles()).containsExactly("Branch");
        assertThat(result.errors()).hasSize(1);
    }
}
