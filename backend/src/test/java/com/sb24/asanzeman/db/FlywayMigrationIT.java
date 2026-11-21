package com.sb24.asanzeman.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.sb24.asanzeman.support.OracleTestContainer;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Runs every Flyway migration against an empty Oracle (D20) and checks the baseline tables exist
 * with the rules the plan promises. Runs only with {@code -Dintegration=true}.
 */
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties")
class FlywayMigrationIT {

    @DynamicPropertySource
    static void oracle(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", OracleTestContainer::jdbcUrl);
        registry.add("spring.datasource.username", () -> "azs_app");
        registry.add("spring.datasource.password", () -> "test");
        com.sb24.asanzeman.support.TestKeys.register(registry);
    }

    @Autowired
    Flyway flyway;

    @Autowired
    DataSource dataSource;

    @Test
    void allMigrationsAppliedInOrder() {
        var info = flyway.info();
        assertThat(info.pending()).isEmpty();
        assertThat(info.applied()).extracting(m -> m.getVersion().toString()).containsExactly("001", "002", "003");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }

    @Test
    void referenceTablesExistWithTheirConstraints() {
        var jdbc = new JdbcTemplate(dataSource);
        assertThat(count(jdbc, "SELECT COUNT(*) FROM all_tables WHERE owner = 'AZS_BUSINESS' AND table_name IN "
                + "('BRANCH','AGENCY','REJECTION_REASON','SCHEDULER_LOCK','TRACKING_COUNTER')")).isEqualTo(5);
        assertThat(count(jdbc, "SELECT COUNT(*) FROM all_tables WHERE owner = 'AZS_AUDIT' AND table_name = 'AUDIT_LOG'")).isEqualTo(1);
        // branch.parent_branch is a self-reference (D32)
        assertThat(count(jdbc, "SELECT COUNT(*) FROM all_constraints WHERE owner = 'AZS_BUSINESS' "
                + "AND constraint_name = 'FK_BRANCH_PARENT' AND constraint_type = 'R'")).isEqualTo(1);
        // nothing is partitioned (D43)
        assertThat(count(jdbc, "SELECT COUNT(*) FROM all_part_tables WHERE owner LIKE 'AZS_%'")).isZero();
    }

    @Test
    void authTablesExistAndTheRefreshTokenHashIsUnique() {
        var jdbc = new JdbcTemplate(dataSource);
        assertThat(count(jdbc, "SELECT COUNT(*) FROM all_tables WHERE owner = 'AZS_AUTH' AND table_name IN "
                + "('APP_USER','REFRESH_TOKENS','JWT_BLACKLIST','YAGHUT_SESSION')")).isEqualTo(4);
        assertThat(count(jdbc, "SELECT COUNT(*) FROM all_constraints WHERE owner = 'AZS_AUTH' "
                + "AND constraint_name IN ('UX_REFRESH_TOKENS_HASH','UX_APP_USER_ACCOUNT_NAME','CK_APP_USER_ROLE')"))
                .isEqualTo(3);
    }

    @Test
    void agencyCodeMustBeFourDigitsAndCifUnique() {
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO azs_business.agency (cif, code, name) VALUES ('9001', '1234', 'Test Agency')");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                jdbc.update("INSERT INTO azs_business.agency (cif, code, name) VALUES ('9002', '12A4', 'Bad code')"))
                .hasMessageContaining("CK_AGENCY_CODE_DIGITS");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                jdbc.update("INSERT INTO azs_business.agency (cif, code, name) VALUES ('9001', '5678', 'Dup cif')"))
                .hasMessageContaining("UX_AGENCY_CIF");
        jdbc.update("DELETE FROM azs_business.agency WHERE cif = '9001'");
    }

    private static int count(JdbcTemplate jdbc, String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }
}
