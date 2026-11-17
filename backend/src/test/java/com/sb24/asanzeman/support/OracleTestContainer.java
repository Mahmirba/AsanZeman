package com.sb24.asanzeman.support;

import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * One Oracle Free container shared by all integration tests (*IT), started once per JVM.
 * The five Asan Zeman schemas and AZS_APP are created by {@code db/oracle-test-users.sql}, which
 * the image runs as SYSDBA before the first connection.
 */
public final class OracleTestContainer {

    private static final DockerImageName IMAGE = DockerImageName.parse("gvenzl/oracle-free:23-slim-faststart");

    public static final OracleContainer ORACLE = new OracleContainer(IMAGE)
            .withUsername("azs_app")
            .withPassword("test")
            .withCopyFileToContainer(MountableFile.forClasspathResource("db/oracle-test-users.sql"),
                    "/container-entrypoint-initdb.d/01_users.sql")
            .withReuse(true);

    static {
        ORACLE.start();
    }

    private OracleTestContainer() {
    }

    public static String jdbcUrl() {
        return ORACLE.getJdbcUrl();
    }
}
