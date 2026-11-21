package com.sb24.asanzeman.request.infrastructure.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.integration.yaghut.YaghutBranchPage;
import com.sb24.asanzeman.integration.yaghut.YaghutClient;
import com.sb24.asanzeman.integration.yaghut.YaghutSession;
import com.sb24.asanzeman.integration.yaghut.YaghutSessionManager;
import com.sb24.asanzeman.request.domain.model.BranchData;
import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class YaghutBranchSourceTest {

    private static final YaghutSession SESSION = new YaghutSession("S1", null);

    @Mock
    private YaghutClient client;
    @Mock
    private YaghutSessionManager sessions;

    private YaghutBranchSource source;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        source = new YaghutBranchSource(client, sessions, 2);
        // run the action with a session, as the real manager does
        when(sessions.withTechnicalSession(any())).thenAnswer(
                call -> ((Function<YaghutSession, Object>) call.getArgument(0)).apply(SESSION));
    }

    private static YaghutBranchPage.Branch yb(String code) {
        return new YaghutBranchPage.Branch(code, "Branch " + code, "Tehran", "addr", "tel", "fax", "zip");
    }

    @Test
    void readsEveryPageUntilTheTotalIsReached() {
        when(client.branches(SESSION, 0, 2)).thenReturn(new YaghutBranchPage(3, List.of(yb("0001"), yb("0002"))));
        when(client.branches(SESSION, 2, 2)).thenReturn(new YaghutBranchPage(3, List.of(yb("0003"))));

        List<BranchData> all = source.fetchAll();

        assertThat(all).extracting(BranchData::code).containsExactly("0001", "0002", "0003");
        assertThat(all.get(0)).isEqualTo(new BranchData("0001", "Branch 0001", "Tehran", "addr", "tel", "fax", "zip"));
    }

    @Test
    void aSinglePageIsOneCall() {
        when(client.branches(eq(SESSION), eq(0), eq(2))).thenReturn(new YaghutBranchPage(1, List.of(yb("0001"))));

        assertThat(source.fetchAll()).hasSize(1);
    }

    @Test
    void zeroBranchesIsAnEmptyList() {
        when(client.branches(SESSION, 0, 2)).thenReturn(new YaghutBranchPage(0, List.of()));

        assertThat(source.fetchAll()).isEmpty();
    }

    @Test
    void anEmptyPageBeforeTheTotalIsABrokenAnswerNotAnEndlessLoop() {
        when(client.branches(SESSION, 0, 2)).thenReturn(new YaghutBranchPage(5, List.of(yb("0001"), yb("0002"))));
        when(client.branches(SESSION, 2, 2)).thenReturn(new YaghutBranchPage(5, List.of()));

        assertThatThrownBy(source::fetchAll).isInstanceOf(TemporarilyUnavailableException.class);
    }
}
