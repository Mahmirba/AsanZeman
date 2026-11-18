package com.sb24.asanzeman.integration.yaghut;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sb24.asanzeman.shared.model.TemporarilyUnavailableException;
import com.sb24.asanzeman.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class YaghutSessionManagerTest {

    private MutableClock clock;
    private YaghutClient client;
    private YaghutSessionManager manager;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-17T10:00:00Z"));
        client = mock(YaghutClient.class);
        manager = new YaghutSessionManager(client, "svc", "pw", clock);
    }

    private YaghutLogin login(String id, Instant expiresAt) {
        return new YaghutLogin(new YaghutSession(id, expiresAt), "svc", null, null, null, null, null);
    }

    private static YaghutException fault(YaghutException.Fault fault) {
        return new YaghutException(fault, "test");
    }

    @Test
    void logsInOnceAndReusesTheSession() {
        when(client.login("svc", "pw")).thenReturn(login("S1", clock.instant().plus(Duration.ofHours(1))));

        assertThat(manager.withTechnicalSession(YaghutSession::jsessionId)).isEqualTo("S1");
        assertThat(manager.withTechnicalSession(YaghutSession::jsessionId)).isEqualTo("S1");

        verify(client, times(1)).login("svc", "pw");
    }

    @Test
    void logsInAgainOnceTheEndTimeHasPassed() {
        when(client.login("svc", "pw")).thenReturn(login("S1", clock.instant().plus(Duration.ofHours(1))),
                login("S2", clock.instant().plus(Duration.ofHours(5))));
        manager.withTechnicalSession(YaghutSession::jsessionId);

        clock.advance(Duration.ofHours(1));

        assertThat(manager.withTechnicalSession(YaghutSession::jsessionId)).isEqualTo("S2");
    }

    @Test
    void aSessionWithoutAnEndTimeIsKeptUntilYaghutRefusesIt() {
        when(client.login("svc", "pw")).thenReturn(login("S1", null));
        manager.withTechnicalSession(YaghutSession::jsessionId);

        clock.advance(Duration.ofDays(30));
        manager.withTechnicalSession(YaghutSession::jsessionId);

        verify(client, times(1)).login("svc", "pw");
    }

    @Test
    void anInvalidSessionLogsInAgainAndRepeatsTheCallOnce() {
        when(client.login("svc", "pw")).thenReturn(login("S1", null), login("S2", null));
        List<String> used = new ArrayList<>();

        String result = manager.withTechnicalSession(session -> {
            used.add(session.jsessionId());
            if (session.jsessionId().equals("S1")) {
                throw fault(YaghutException.Fault.INVALID_SESSION);
            }
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(used).containsExactly("S1", "S2");
        verify(client, times(2)).login("svc", "pw");
    }

    @Test
    void aSessionRefusedAgainAfterAFreshLoginIsTechnical() {
        when(client.login("svc", "pw")).thenReturn(login("S1", null), login("S2", null));

        assertThatThrownBy(() -> manager.withTechnicalSession(session -> {
            throw fault(YaghutException.Fault.INVALID_SESSION);
        })).isInstanceOf(TemporarilyUnavailableException.class);

        verify(client, times(2)).login("svc", "pw");
    }

    @Test
    void accessDeniedIsTechnicalAndNotRetried() {
        when(client.login("svc", "pw")).thenReturn(login("S1", null));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> manager.withTechnicalSession(session -> {
            calls.incrementAndGet();
            throw fault(YaghutException.Fault.SERVICE_ACCESS_DENIED);
        })).isInstanceOf(TemporarilyUnavailableException.class);

        assertThat(calls.get()).isEqualTo(1);
        verify(client, times(1)).login("svc", "pw");
    }

    @Test
    void otherFaultsReachTheCallerUnchanged() {
        when(client.login("svc", "pw")).thenReturn(login("S1", null));

        assertThatThrownBy(() -> manager.withTechnicalSession(session -> {
            throw fault(YaghutException.Fault.INVALID_CUSTOMER);
        })).isInstanceOfSatisfying(YaghutException.class,
                ex -> assertThat(ex.fault()).isEqualTo(YaghutException.Fault.INVALID_CUSTOMER));
    }

    @Test
    void anOtherFaultOnTheRepeatedCallReachesTheCallerToo() {
        when(client.login("svc", "pw")).thenReturn(login("S1", null), login("S2", null));

        assertThatThrownBy(() -> manager.withTechnicalSession(session -> {
            if (session.jsessionId().equals("S1")) {
                throw fault(YaghutException.Fault.INVALID_SESSION);
            }
            throw fault(YaghutException.Fault.INVALID_CUSTOMER);
        })).isInstanceOfSatisfying(YaghutException.class,
                ex -> assertThat(ex.fault()).isEqualTo(YaghutException.Fault.INVALID_CUSTOMER));
    }

    @Test
    void aRefusedTechnicalAccountIsAConfigurationErrorAndNothingIsCalled() {
        when(client.login("svc", "pw")).thenThrow(fault(YaghutException.Fault.INVALID_CREDENTIALS));
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> manager.withTechnicalSession(session -> calls.incrementAndGet()))
                .isInstanceOf(TemporarilyUnavailableException.class);

        assertThat(calls.get()).isZero();
        verify(client, times(1)).login("svc", "pw"); // no retry, so the account is not locked
    }

    @Test
    void aTechnicalLoginFailureReachesTheCaller() {
        var down = new TemporarilyUnavailableException("Yaghut", new RuntimeException("timeout"));
        when(client.login("svc", "pw")).thenThrow(down);

        assertThatThrownBy(() -> manager.withTechnicalSession(YaghutSession::jsessionId)).isSameAs(down);
    }

    @Test
    void aNewerSessionOfAnotherCallerIsNotDiscarded() {
        when(client.login("svc", "pw")).thenReturn(login("S1", clock.instant().plus(Duration.ofMinutes(1))),
                login("S2", null));
        List<String> used = new ArrayList<>();

        String result = manager.withTechnicalSession(session -> {
            used.add(session.jsessionId());
            if (session.jsessionId().equals("S1")) {
                // meanwhile another caller notices S1 has ended and logs in: S2 becomes current
                clock.advance(Duration.ofMinutes(2));
                manager.withTechnicalSession(YaghutSession::jsessionId);
                throw fault(YaghutException.Fault.INVALID_SESSION);
            }
            return session.jsessionId();
        });

        // the repeat uses S2 and does not send a third login
        assertThat(result).isEqualTo("S2");
        assertThat(used).containsExactly("S1", "S2");
        verify(client, times(2)).login("svc", "pw");
    }

    @Test
    void manyCallersAtOnceSendOneLogin() throws Exception {
        AtomicInteger logins = new AtomicInteger();
        when(client.login("svc", "pw")).thenAnswer(invocation -> {
            logins.incrementAndGet();
            Thread.sleep(150);
            return login("SHARED", null);
        });
        int callers = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return manager.withTechnicalSession(YaghutSession::jsessionId);
                }));
            }
            start.countDown();
            for (Future<String> result : results) {
                assertThat(result.get()).isEqualTo("SHARED");
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(logins.get()).isEqualTo(1);
    }
}
