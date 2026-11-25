package com.sb24.asanzeman.request.infrastructure.persistence;

import com.sb24.asanzeman.request.domain.model.BranchData;
import com.sb24.asanzeman.request.domain.model.BranchLink;
import com.sb24.asanzeman.request.domain.model.BranchSummary;
import com.sb24.asanzeman.request.domain.model.BranchSyncResult;
import com.sb24.asanzeman.request.domain.port.out.BranchRepositoryPort;
import com.sb24.asanzeman.shared.persistence.DbTime;
import java.sql.Types;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** {@code AZS_BUSINESS.branch} through plain JDBC (4.4, D32). Not cached (D45). */
@Component
public class JdbcBranchRepository implements BranchRepositoryPort {

    static final String SELECT_ALL = "SELECT branch_code, active FROM azs_business.branch";
    static final String INSERT = """
            INSERT INTO azs_business.branch (branch_code, name, city, address, tel, fax, zip_code, parent_branch,
                                             active, last_synced_at)
            VALUES (:code, :name, :city, :address, :tel, :fax, :zip, :code, 1, :now)
            """;
    static final String UPDATE = """
            UPDATE azs_business.branch
               SET name = :name, city = :city, address = :address, tel = :tel, fax = :fax, zip_code = :zip,
                   active = 1, last_synced_at = :now, updated_at = :now
             WHERE branch_code = :code
            """;
    static final String DEACTIVATE = """
            UPDATE azs_business.branch SET active = 0, updated_at = :now WHERE branch_code = :code
            """;

    private final JdbcClient jdbc;

    public JdbcBranchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public BranchSyncResult apply(List<BranchData> fetched, Instant now) {
        Map<String, Boolean> existing = new HashMap<>();
        jdbc.sql(SELECT_ALL).query((rs, row) -> existing.put(rs.getString("branch_code"), rs.getInt("active") == 1))
                .list();
        var at = DbTime.toDb(now);
        int inserted = 0;
        int updated = 0;
        Set<String> seen = new HashSet<>();
        for (BranchData b : fetched) {
            seen.add(b.code());
            boolean isNew = !existing.containsKey(b.code());
            jdbc.sql(isNew ? INSERT : UPDATE)
                    .param("code", b.code(), Types.VARCHAR).param("name", b.name(), Types.VARCHAR)
                    .param("city", b.city(), Types.VARCHAR).param("address", b.address(), Types.VARCHAR)
                    .param("tel", b.tel(), Types.VARCHAR).param("fax", b.fax(), Types.VARCHAR)
                    .param("zip", b.zipCode(), Types.VARCHAR).param("now", at, Types.TIMESTAMP).update();
            if (isNew) {
                inserted++;
            } else {
                updated++;
            }
        }
        int deactivated = 0;
        for (Map.Entry<String, Boolean> e : existing.entrySet()) {
            if (!seen.contains(e.getKey()) && e.getValue()) {
                jdbc.sql(DEACTIVATE).param("code", e.getKey(), Types.VARCHAR).param("now", at, Types.TIMESTAMP).update();
                deactivated++;
            }
        }
        return new BranchSyncResult(inserted, updated, deactivated);
    }

    @Override
    public boolean any() {
        return jdbc.sql("SELECT COUNT(*) FROM azs_business.branch").query(Integer.class).single() > 0;
    }

    @Override
    public List<BranchLink> links() {
        return jdbc.sql("SELECT branch_code, active, parent_branch FROM azs_business.branch ORDER BY branch_code")
                .query((rs, row) -> new BranchLink(rs.getString("branch_code"), rs.getInt("active") == 1,
                        rs.getString("parent_branch"))).list();
    }

    @Override
    public boolean isOpen(String branchCode) {
        return jdbc.sql("SELECT COUNT(*) FROM azs_business.branch WHERE branch_code = :c AND active = 1 "
                + "AND parent_branch = branch_code").param("c", branchCode, Types.VARCHAR)
                .query(Integer.class).single() > 0;
    }

    @Override
    public Optional<String> nameOf(String branchCode) {
        return jdbc.sql("SELECT name FROM azs_business.branch WHERE branch_code = :c")
                .param("c", branchCode, Types.VARCHAR).query(String.class).optional();
    }

    @Override
    public List<BranchSummary> searchOpen(String prefix, int limit) {
        String like = escapeLike(prefix == null ? "" : prefix.trim()) + "%";
        return jdbc.sql("SELECT branch_code, name, city FROM azs_business.branch WHERE active = 1 "
                + "AND parent_branch = branch_code AND (name LIKE :p ESCAPE '!' OR branch_code LIKE :p ESCAPE '!') "
                + "ORDER BY name, branch_code FETCH FIRST " + limit + " ROWS ONLY")
                .param("p", like, Types.VARCHAR)
                .query((rs, row) -> new BranchSummary(
                        rs.getString("branch_code"), rs.getString("name"), rs.getString("city")))
                .list();
    }

    private static String escapeLike(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
