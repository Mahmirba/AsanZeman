package com.sb24.asanzeman.support;

import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** A fresh in-memory H2 (Oracle mode) with the AZS_AUTH tables, so the JDBC adapters run real SQL without Docker. */
public final class H2Auth {

    private H2Auth() {
    }

    public static JdbcClient newDatabase() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1", "sa", "");
        new ResourceDatabasePopulator(new ClassPathResource("db/h2-auth-schema.sql")).execute(dataSource);
        return JdbcClient.create(dataSource);
    }
}
