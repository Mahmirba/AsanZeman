package com.sb24.asanzeman.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.auth.domain.model.RefreshTokenRecord;
import com.sb24.asanzeman.auth.domain.model.Role;
import com.sb24.asanzeman.auth.domain.model.Session;
import com.sb24.asanzeman.support.H2Auth;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Real SQL on in-memory H2. */
class JdbcRefreshTokenStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private static final Instant END = NOW.plusSeconds(28800);

    private JdbcRefreshTokenStore store;

    @BeforeEach
    void setUp() {
        store = new JdbcRefreshTokenStore(H2Auth.newDatabase());
    }

    private static RefreshTokenRecord token(UUID family, String hash) {
        return new RefreshTokenRecord(UUID.randomUUID(), family,
                new Session(UUID.randomUUID(), "ali", Role.BRANCH, "0123"), hash, END, null, null);
    }

    @Test
    void onlyTheTokensThatExpiredBeforeTheLimitAreDeleted() {
        RefreshTokenRecord expired = new RefreshTokenRecord(UUID.randomUUID(), UUID.randomUUID(),
                new Session(UUID.randomUUID(), "ali", Role.BRANCH, "0123"), "h-old", NOW.minusSeconds(100), null, null);
        RefreshTokenRecord valid = token(UUID.randomUUID(), "h-new");
        store.save(expired);
        store.save(valid);

        assertThat(store.deleteExpiredBefore(NOW)).isEqualTo(1);

        assertThat(store.findByHash("h-old")).isEmpty();
        assertThat(store.findByHash("h-new")).isPresent();
        assertThat(store.deleteExpiredBefore(NOW)).isZero();
    }

    @Test
    void aSavedTokenIsFoundByItsHashWithEveryField() {
        RefreshTokenRecord saved = token(UUID.randomUUID(), "hash-1");
        store.save(saved);

        RefreshTokenRecord found = store.findByHash("hash-1").orElseThrow();

        assertThat(found).isEqualTo(saved);
        assertThat(found.session().branchCode()).isEqualTo("0123");
        assertThat(found.expiresAt()).isEqualTo(END);
        assertThat(found.used()).isFalse();
        assertThat(found.revoked()).isFalse();
    }

    @Test
    void aSessionWithoutABranchIsStoredToo() {
        var saved = new RefreshTokenRecord(UUID.randomUUID(), UUID.randomUUID(),
                new Session(UUID.randomUUID(), "agency", Role.AGENCY, null), "hash-2", END, null, null);
        store.save(saved);

        assertThat(store.findByHash("hash-2").orElseThrow().session().branchCode()).isNull();
    }

    @Test
    void anUnknownHashIsEmpty() {
        assertThat(store.findByHash("nothing")).isEmpty();
    }

    @Test
    void markUsedWorksOnceOnly() {
        RefreshTokenRecord saved = token(UUID.randomUUID(), "hash-3");
        store.save(saved);

        assertThat(store.markUsed(saved.id(), NOW)).isTrue();
        assertThat(store.markUsed(saved.id(), NOW.plusSeconds(1))).isFalse();
        RefreshTokenRecord found = store.findByHash("hash-3").orElseThrow();
        assertThat(found.used()).isTrue();
        assertThat(found.usedAt()).isEqualTo(NOW);
    }

    @Test
    void markUsedFailsForAnUnknownOrRevokedToken() {
        UUID family = UUID.randomUUID();
        RefreshTokenRecord saved = token(family, "hash-4");
        store.save(saved);
        store.revokeFamily(family, NOW);

        assertThat(store.markUsed(saved.id(), NOW)).isFalse();
        assertThat(store.markUsed(UUID.randomUUID(), NOW)).isFalse();
    }

    @Test
    void revokingAFamilyRevokesEveryTokenOfItAndNoOtherFamily() {
        UUID family = UUID.randomUUID();
        store.save(token(family, "a"));
        store.save(token(family, "b"));
        store.save(token(UUID.randomUUID(), "other"));

        store.revokeFamily(family, NOW);

        assertThat(store.findByHash("a").orElseThrow().revoked()).isTrue();
        assertThat(store.findByHash("b").orElseThrow().revokedAt()).isEqualTo(NOW);
        assertThat(store.findByHash("other").orElseThrow().revoked()).isFalse();
    }

    @Test
    void revokingAgainKeepsTheFirstRevokeTime() {
        UUID family = UUID.randomUUID();
        store.save(token(family, "a"));
        store.revokeFamily(family, NOW);

        store.revokeFamily(family, NOW.plusSeconds(60));

        assertThat(store.findByHash("a").orElseThrow().revokedAt()).isEqualTo(NOW);
    }

    @Test
    void whenManyUseTheSameTokenAtOnceExactlyOneWins() throws Exception {
        RefreshTokenRecord saved = token(UUID.randomUUID(), "hash-5");
        store.save(saved);
        int callers = 8;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            java.util.List<Future<?>> done = new java.util.ArrayList<>();
            for (int i = 0; i < callers; i++) {
                done.add(pool.submit(() -> {
                    start.await();
                    if (store.markUsed(saved.id(), NOW)) {
                        winners.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : done) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(winners.get()).isEqualTo(1);
    }
}
