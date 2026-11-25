package com.sb24.asanzeman.request.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SalutationTest {

    @Test
    void aManIsAddressedAsAqaAndAWomanAsKhanomFollowedByTheNameAndALineBreak() {
        assertThat(Salutation.line("MALE", "Ali Rezaei")).isEqualTo("آقا Ali Rezaei\n");
        assertThat(Salutation.line("FEMALE", " Sara Karimi ")).isEqualTo("خانم Sara Karimi\n");
    }

    @Test
    void withoutAGenderOrANameTheLineIsLeftOut() {
        assertThat(Salutation.line(null, "Ali Rezaei")).isEmpty();
        assertThat(Salutation.line("MALE", null)).isEmpty();
        assertThat(Salutation.line("MALE", "  ")).isEmpty();
    }
}
