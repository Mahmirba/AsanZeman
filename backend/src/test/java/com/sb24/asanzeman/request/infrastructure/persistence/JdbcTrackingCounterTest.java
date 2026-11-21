package com.sb24.asanzeman.request.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sb24.asanzeman.request.domain.model.TrackingCodesExhaustedException;
import com.sb24.asanzeman.support.H2Auth;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Real SQL on in-memory H2; the MANDATORY transaction is the caller's, so the tests call the SQL part directly. */
class JdbcTrackingCounterTest {

    private final javax.sql.DataSource dataSource = H2Auth.newDataSource();
    private final JdbcClient jdbc = JdbcClient.create(dataSource);
    private final org.springframework.transaction.support.TransactionTemplate tx =
            new org.springframework.transaction.support.TransactionTemplate(
                    new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
    private final JdbcTrackingCounter counter = new JdbcTrackingCounter(jdbc);

    @Test
    void theFirstRequestOfADayGetsOneThenTwoThree() {
        assertThat(counter.next("0123", "0042", "050830")).isEqualTo(1);
        assertThat(counter.next("0123", "0042", "050830")).isEqualTo(2);
        assertThat(counter.next("0123", "0042", "050830")).isEqualTo(3);
    }

    @Test
    void eachBranchAgencyAndDayCountsOnItsOwn() {
        assertThat(counter.next("0123", "0042", "050830")).isEqualTo(1);
        assertThat(counter.next("0124", "0042", "050830")).isEqualTo(1);
        assertThat(counter.next("0123", "0043", "050830")).isEqualTo(1);
        assertThat(counter.next("0123", "0042", "050831")).isEqualTo(1);
    }

    @Test
    void theCounterNeverPassesNineThousandNineHundredNinetyNine() {
        jdbc.sql("INSERT INTO azs_business.tracking_counter VALUES ('0123', '0042', '050830', 9998)").update();

        assertThat(counter.next("0123", "0042", "050830")).isEqualTo(9999);
        assertThatThrownBy(() -> counter.next("0123", "0042", "050830"))
                .isInstanceOf(TrackingCodesExhaustedException.class);
        assertThat(jdbc.sql("SELECT last_value FROM azs_business.tracking_counter").query(Integer.class).single())
                .isEqualTo(9999);
    }

    @Test
    void concurrentRegistrationsNeverGetTheSameNumber() throws Exception {
        int callers = 8;
        Set<Integer> numbers = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(callers)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < callers; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    numbers.add(tx.execute(status -> counter.next("0123", "0042", "050830")));
                    return null;
                }));
            }
            start.countDown();
            for (var f : futures) {
                f.get();
            }
        }

        assertThat(numbers).hasSize(callers);
        assertThat(numbers).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8);
    }
}
