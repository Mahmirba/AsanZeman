package com.sb24.asanzeman.request.domain.port.in;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegisterRequestCommandTest {

    private static RegisterRequestCommand with(List<CompanionInput> companions) {
        return new RegisterRequestCommand(UUID.randomUUID(), UUID.randomUUID(), "Sky", "0012345679", "0123", 1,
                LocalDate.of(2027, 1, 20), companions, null, null);
    }

    @Test
    void noCompanionsMeansAnEmptyList() {
        assertThat(with(null).companions()).isEmpty();
    }

    @Test
    void theCompanionListIsCopied() {
        var list = new ArrayList<>(List.of(new CompanionInput("A", "0012345679", LocalDate.of(2015, 1, 1), "P1")));
        var command = with(list);

        list.clear();

        assertThat(command.companions()).hasSize(1);
    }
}
